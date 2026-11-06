#!/usr/bin/env bash
# Fetch, verify and stage the native artifacts pinned in libs.lock.
#
# Upstream commits no prebuilt native libraries: the core binding ships as libv2ray.aar
# (built by 2dust/AndroidLibXrayLite with gomobile) and the hev tunnel is compiled from a
# submodule by compile-hevtun.sh, which needs the Android NDK. Until the NDK path is wired
# up, the tunnel libraries are taken from the upstream release APK, which is GPG-signed.
# Every artifact is checked against a hash in libs.lock before it reaches V2rayNG/app/libs,
# so a corrupted or substituted download cannot silently become part of a build.
# See FORK.md.
#
# Usage: tool/fetch-native.sh [arm64-v8a ...]   (default: the ABIs pinned in libs.lock)
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"
CACHE="$ROOT/.native-cache"
LIBS="$ROOT/V2rayNG/app/libs"
LOCK="$ROOT/libs.lock"

[ -f "$LOCK" ] || { echo "libs.lock not found" >&2; exit 1; }
for cmd in curl gpg unzip python; do
  command -v "$cmd" >/dev/null || { echo "$cmd is required" >&2; exit 1; }
done

# Reads pinned values out of libs.lock so this script never carries its own copy of a hash.
# Path segments are separated by "/" because library names contain dots. A key whose value is
# a dict yields its space-separated member names. Native Windows python cannot open an
# MSYS-style path, so it gets one converted for the host platform.
LOCK_ARG="$LOCK"
command -v cygpath >/dev/null && LOCK_ARG="$(cygpath -w "$LOCK")"
lock() { python -c "
import json, sys
n = json.load(open(r'$LOCK_ARG', encoding='utf-8'))
for k in sys.argv[1].split('/'):
    try:
        n = n[k]
    except KeyError:
        sys.exit('libs.lock has no entry for ' + sys.argv[1])
print(' '.join(n.keys()) if isinstance(n, dict) else n)
" "$1"; }

sha() { sha256sum "$1" | cut -d' ' -f1; }

APK_BASE_URL="https://github.com/$(lock base/repository)/releases/download/$(lock base/tag)"
AAR_URL="https://github.com/$(lock core/repository)/releases/download/$(lock core/tag)/$(lock core/asset)"

# Writes $CACHE/$2 from $1 and keeps it only if its SHA-256 is $3.
fetch() {
  local url="$1" name="$2" want="$3" out="$CACHE/$2" attempt
  if [ -f "$out" ] && [ "$(sha "$out")" = "$want" ]; then echo "cached  $name"; return 0; fi
  [ -f "$out" ] && echo "stale   $name (hash mismatch), re-downloading" && rm -f "$out"
  for attempt in 1 2 3 4 5; do
    echo "get     $name attempt $attempt"
    # No -C/--continue: a reset transfer that curl reports as success is exactly how a
    # truncated artifact reached disk before. Each attempt writes the whole file or nothing.
    if curl -fsSL --retry 3 --retry-all-errors --retry-delay 5 \
         --speed-limit 4096 --speed-time 25 --max-time 1800 -o "$out.part" "$url"; then
      if [ "$(sha "$out.part")" = "$want" ]; then
        mv "$out.part" "$out"; echo "ok      $name"; return 0
      fi
      echo "badhash $name got $(sha "$out.part") want $want"
    fi
    rm -f "$out.part"; sleep 8
  done
  echo "FAILED to fetch $url with the pinned digest" >&2
  return 1
}

# Copies a verified file into app/libs, skipping the write when the target already matches.
stage() {
  local src="$1" dst="$2"
  if [ -f "$dst" ] && [ "$(sha "$dst")" = "$(sha "$src")" ]; then
    echo "staged  ${dst#"$ROOT"/} (already current)"; return 0
  fi
  mkdir -p "$(dirname "$dst")"; cp "$src" "$dst"; echo "staged  ${dst#"$ROOT"/}"
}

mkdir -p "$CACHE" "$LIBS"
export GNUPGHOME="$CACHE/gnupg-nerpa"
mkdir -p "$GNUPGHOME"; chmod 700 "$GNUPGHOME" 2>/dev/null || true

# --- signing key -----------------------------------------------------------------------------
fetch "$APK_BASE_URL/$(lock gpg/asset)" "$(lock gpg/asset)" "$(lock gpg/sha256)"
[ -f "$GNUPGHOME/pubring.kbx" ] || gpg --batch --quiet --import "$CACHE/$(lock gpg/asset)"
FP=$(gpg --batch --list-keys --with-colons 2>/dev/null | awk -F: '/^fpr/{print $10; exit}')
[ "$FP" = "$(lock gpg/fingerprint)" ] || {
  echo "imported key fingerprint $FP is not the pinned $(lock gpg/fingerprint)" >&2; exit 1; }
echo "key     fingerprint verified"

# --- core binding (no upstream signature: the pinned digest is the only gate) -----------------
fetch "$AAR_URL" "$(lock core/tag)-$(lock core/asset)" "$(lock core/sha256)"
stage "$CACHE/$(lock core/tag)-$(lock core/asset)" "$LIBS/$(lock core/asset)"

# --- tunnel libraries, lifted out of the GPG-signed release APK ---------------------------------
ABIS=("$@")
[ ${#ABIS[@]} -eq 0 ] && ABIS=($(lock tunnel_source/abis))

for abi in "${ABIS[@]}"; do
  echo "=== $abi ==="
  case " $(lock tunnel_source/abis) " in *" $abi "*) ;; *)
    echo "ABI $abi is not pinned in libs.lock" >&2; exit 1 ;; esac

  apk="$(lock "tunnel_source/abis/$abi/apk_asset")"
  sig="$(lock "tunnel_source/abis/$abi/signature_asset")"

  fetch "$APK_BASE_URL/$sig" "$sig" "$(lock "tunnel_source/abis/$abi/signature_sha256")"
  fetch "$APK_BASE_URL/$apk" "$apk" "$(lock "tunnel_source/abis/$abi/apk_sha256")"

  gpg --batch --verify "$CACHE/$sig" "$CACHE/$apk" || {
    echo "gpg signature check FAILED for $apk" >&2; exit 1; }
  echo "gpg     $apk signature verified"

  extract="$CACHE/extract/$abi"; mkdir -p "$extract"
  for lib in $(lock "tunnel_source/abis/$abi/libraries"); do
    want="$(lock "tunnel_source/abis/$abi/libraries/$lib/sha256")"
    if [ -f "$extract/$lib" ] && [ "$(sha "$extract/$lib")" = "$want" ]; then
      echo "cached  $lib"
    else
      unzip -o -qq -j "$CACHE/$apk" "lib/$abi/$lib" -d "$extract"
      [ "$(sha "$extract/$lib")" = "$want" ] || {
        echo "badhash $lib inside the verified APK: got $(sha "$extract/$lib") want $want" >&2
        exit 1; }
      echo "ok      $lib"
    fi
    stage "$extract/$lib" "$LIBS/$abi/$lib"
  done
done

echo
echo "Native artifacts are in place. Next: cd V2rayNG && ./gradlew assembleDebug"
