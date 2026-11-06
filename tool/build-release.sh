#!/usr/bin/env bash
# Assemble and sign a playstore release with the operator's key, then prove the signature.
#
# The key is not in this tree and not in the public mirror, and the passphrase never reaches a
# command line, a shell history entry or a file on disk: this script prompts for it, exports it
# into the environment of a single Gradle run and unsets it afterwards. Once Gradle is done, every
# APK's signing-certificate digest is compared against libs.lock, and a mismatch or a missing pin
# renames the artifact to *.rejected instead of leaving a plausible release in the output directory
# — the same refusal to degrade silently that FORK.md section 1 records for ABIs.
#
# The pin is checked before Gradle starts, so a build that cannot be verified is never paid for.
#
# Usage: tool/build-release.sh --keystore PATH [--alias NAME] [--lock PATH] [-- <gradle args>]
#   Run this under Git Bash. PowerShell resolves bash.exe to the WSL launcher, and WSL has no
#   cygpath, no cmd.exe and no "python" — the script refuses there instead of failing later.
#   The keystore path is required and has no default: it is operator key layout, and this file is
#   published. The path is spelled with forward slashes, or quoted: an unquoted C:\... loses its
#   backslashes to the shell long before it reaches this script. E.g.
#   tool/build-release.sh --keystore C:/keys/release-key.p12
#   --alias is optional. The keystore is read before Gradle starts and the alias is resolved from
#   the certificate digest pinned in libs.lock, so a name typed from memory is never needed; a name
#   that is given must point at the pinned certificate.
#   The free-issuance endpoint belongs after --, e.g.
#   tool/build-release.sh -- -P NERPA_FREE_ENDPOINT=https://example/issue
#   It is operator data and, like the key, it is not stored in the tree.
#   --skip-lint excludes the three lintVital…PlaystoreRelease tasks. Only use it when Google Maven is
#   down: without the flag the build fails instead of pretending it checked. See FORK.md section 10.
#
# Output is passed through untouched; redirect it to V2rayNG/.build-logs/ yourself if you want it
# kept, the passphrase is never printed either way.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"
PROPS="$ROOT/V2rayNG/local.properties"
OUT="$ROOT/V2rayNG/app/build/outputs/apk/playstore/release"
LOCK="$ROOT/libs.lock"
KEYSTORE="${NERPA_KEYSTORE:-}"
ALIAS="${NERPA_KEY_ALIAS:-}"
SKIP_LINT=0
EXTRA=()

die() { echo "$1" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --keystore) KEYSTORE="${2:-}"; shift 2 ;;
    --skip-lint) SKIP_LINT=1; shift ;;
    --alias)    ALIAS="${2:-}"; shift 2 ;;
    --lock)     LOCK="${2:-}"; shift 2 ;;
    --)         shift; EXTRA=("$@"); break ;;
    *)          die "unknown argument: $1" ;;
  esac
done

[ -f "$LOCK" ] || die "libs.lock not found at $LOCK"

# This script drives Windows tools: cygpath for paths, cmd.exe for apksigner.bat, ./gradlew for the
# build. WSL has none of them, and PowerShell resolves bash.exe to the WSL launcher before Git's
# own, so a run started from PowerShell lands in WSL by accident. Name that here instead of
# letting the first missing helper look like the real problem.
[ -z "${WSL_DISTRO_NAME:-}" ] || die "this script needs Git Bash, not WSL — run it as: 'C:/Program Files/Git/bin/bash.exe' -c 'tool/build-release.sh'"

command -v sha256sum >/dev/null || die "sha256sum is required"

# The interpreter is chosen by running it, not by looking its name up: on Windows the Microsoft
# Store alias "python3" resolves to a stub that prints "Python" and exits with a failure code, and
# a stub that answers would hand pin() a digest that is not a digest — every APK would then be
# rejected as "wrong key" for a reason that has nothing to do with the key.
PYTHON=()
for candidate in "python" "python3" "py -3"; do
  parts=()
  read -r -a parts <<<"$candidate"
  if "${parts[@]}" -c 'import json' >/dev/null 2>&1; then
    PYTHON=("${parts[@]}")
    break
  fi
done
[ ${#PYTHON[@]} -gt 0 ] || die "no working python interpreter found (tried: python, python3, py -3)"

# Same reader as tool/fetch-native.sh: the lock file is the only place a pinned value lives, so a
# digest is never duplicated here. Native Windows python cannot open an MSYS-style path.
pin() {
  local arg="$LOCK"
  if command -v cygpath >/dev/null; then arg="$(cygpath -w "$LOCK")"; fi
  "${PYTHON[@]}" -c "
import json, sys
n = json.load(open(r'$arg', encoding='utf-8'))
for k in sys.argv[1].split('/'):
    try:
        n = n[k]
    except KeyError:
        sys.exit('libs.lock has no entry for ' + sys.argv[1])
print(n)
" "$1"
}

WANT="$(pin release_signing/certificate_sha256 | tr 'A-F' 'a-f')"
[ -n "$WANT" ] || die "release_signing/certificate_sha256 is empty"

case "$KEYSTORE" in
  /* | [A-Za-z]:[\\/]*) ;;
  # An unquoted C:\ path loses its backslashes to the shell before this line, so the value that
  # arrives here is not a path anyone can recognize. The refusal has to name it and say how to spell it.
  *) die "--keystore needs an absolute path, got '$KEYSTORE' — quote the Windows path ('C:\\...') or spell it with forward slashes (C:/...)" ;;
esac
[ -f "$KEYSTORE" ] || die "no keystore at '$KEYSTORE' — pass --keystore PATH or set NERPA_KEYSTORE"

SDK="${ANDROID_HOME:-}"
if [ -z "$SDK" ] && [ -f "$PROPS" ]; then
  # local.properties escapes the colon too: sdk.dir=C\:\\Users\\...
  SDK="$(sed -n 's/^sdk\.dir=//p' "$PROPS" | head -1 | sed -e 's/\\:/:/' -e 's/\\\\/\\/g')"
  if command -v cygpath >/dev/null; then SDK="$(cygpath -u "$SDK")"; fi
fi
[ -n "$SDK" ] || die "set ANDROID_HOME or create V2rayNG/local.properties with sdk.dir"
APKSIGNER="$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)apksigner.bat"
[ -f "$APKSIGNER" ] || die "apksigner.bat not found under $SDK/build-tools"

# The prompt goes to the terminal, not to stderr: the documented way to keep a build log is to redirect
# the output, and a passphrase question nobody can see looks exactly like a hung build. The group
# swallows bash's own "/dev/tty: No such device" line when there is no terminal, so the fallback below
# stays readable.
say() { { printf '%s' "$1" >/dev/tty; } 2>/dev/null || printf '%s' "$1" >&2; }

say "Passphrase for the release keystore: "
# Without the guard, set -e kills the script on EOF before the refusal text, which reads as a silent
# failure to anyone who pipes or redirects stdin instead of answering in a terminal.
IFS= read -rs PASSPHRASE || die "no passphrase could be read — run this from a terminal, stdin is not one"
say $'\n'
[ -n "$PASSPHRASE" ] || die "empty passphrase"

export NERPA_KEYSTORE="$KEYSTORE"
export NERPA_KEYSTORE_PASSWORD="$PASSPHRASE" NERPA_KEY_PASSWORD="$PASSPHRASE"
unset PASSPHRASE

# The keystore is read here, before Gradle starts, and the alias is resolved from the pinned
# certificate instead of being asked for. The operator's keystore layout is not in this tree and
# must not be, while an alias typed from memory is a guess: JDK lowercases PKCS12 aliases, so a
# near-miss is not a warning but a build that runs for minutes and then fails in
# packagePlaystoreRelease. The pinned digest identifies the key unambiguously, so the alias follows
# from it and the operator has nothing to remember.
KEYTOOL=""
if command -v keytool >/dev/null 2>&1; then
  KEYTOOL="keytool"
elif [ -n "${JAVA_HOME:-}" ]; then
  JH="$JAVA_HOME"
  if command -v cygpath >/dev/null; then JH="$(cygpath -u "$JAVA_HOME")"; fi
  [ -f "$JH/bin/keytool" ] && KEYTOOL="$JH/bin/keytool"
fi

RESOLVED="$ALIAS"
if [ -z "$KEYTOOL" ]; then
  # Without keytool nothing can be read ahead of the build, so the alias has to come from the
  # operator after all — but say that instead of pretending the check happened.
  [ -n "$ALIAS" ] || die "keytool was not found, so the keystore cannot be read before the build — pass --alias NAME or set NERPA_KEY_ALIAS"
  echo "NOTICE: keytool was not found, so the keystore was not checked before the build." >&2
  echo "        A wrong alias will surface as a signing failure in packagePlaystoreRelease." >&2
else
  KS_ARG="$KEYSTORE"
  if command -v cygpath >/dev/null; then KS_ARG="$(cygpath -w "$KEYSTORE")"; fi
  # -J forces the English labels this parses: keytool localizes them, and the console language of
  # the operator is not this script's business. The store password travels in the environment, so
  # it never reaches a command line. The "|| true" is not about ignoring an error: keytool exits
  # non-zero on a wrong store password, and under pipefail that would kill the script here, before
  # the refusal below could name the cause.
  ENTRIES="$("$KEYTOOL" -J-Duser.language=en -J-Duser.country=US -list -v \
      -keystore "$KS_ARG" -storetype PKCS12 -storepass:env NERPA_KEYSTORE_PASSWORD 2>/dev/null |
    awk '/^Alias name: /{a=$NF} /SHA256:/{gsub(":","",$2); print a" "tolower($2)}' || true)"
  [ -n "$ENTRIES" ] || die "keytool could not read '$KEYSTORE' — wrong store password, or the file is not a PKCS12 keystore"

  PINNED="$(printf '%s\n' "$ENTRIES" | awk -v want="$WANT" '$2==want {print $1}')"
  PINNED_COUNT="$(printf '%s\n' "$PINNED" | grep -c . || true)"
  if [ -n "$ALIAS" ]; then
    RESOLVED="$(printf '%s\n' "$ENTRIES" | awk -v a="$ALIAS" 'tolower($1)==tolower(a) {print $1; exit}')"
  fi

  if [ -n "$RESOLVED" ]; then
    # The alias exists, so it names a real key: it has to be the pinned one, or it is a different key
    # wearing a familiar name.
    GOT="$(printf '%s\n' "$ENTRIES" | awk -v a="$RESOLVED" 'tolower($1)==tolower(a) {print $2; exit}')"
    [ "$GOT" = "$WANT" ] || {
      echo "the alias '$RESOLVED' holds a certificate that is not the pinned one:" >&2
      echo "  got  $GOT" >&2
      echo "  want $WANT" >&2
      die "the alias and the pin disagree — the pin in libs.lock is what the release has to be signed with"
    }
  elif [ "$PINNED_COUNT" -eq 1 ]; then
    RESOLVED="$PINNED"
    if [ -n "$ALIAS" ]; then
      echo "NOTICE: the alias '$ALIAS' is not in this keystore; using the entry that matches the" >&2
      echo "        pinned certificate instead: '$RESOLVED'" >&2
    else
      echo "using the keystore entry that matches the pinned certificate: '$RESOLVED'" >&2
    fi
  else
    {
      echo "the keystore at '$KEYSTORE' does not hold exactly one certificate matching the pin:" >&2
      printf '%s\n' "$ENTRIES" | awk '{printf "  alias %-24s sha256 %s\n", $1, $2}' >&2
      echo "  expected sha256 $WANT" >&2
    }
    die "the release key is not in this keystore under any usable alias"
  fi
fi

export NERPA_KEY_ALIAS="$RESOLVED"

# apksigner ships as a .bat on this platform, and cmd.exe needs host-style paths for both the
# script and the APK.
run_apksigner() {
  local apk="$1"; shift
  cmd //C "$(cygpath -w "$APKSIGNER")" "$@" "$(cygpath -w "$apk")" 2>&1
}

echo "assembling playstore release"

# Named tasks, not checkReleaseBuilds: the tree keeps claiming that lint belongs to a release, and
# only this run steps outside it. Without the flag Gradle fails on the artifact it cannot download,
# which is the honest outcome — an unverified release has to be a decision, not a side effect.
LINT_TASKS=(
  :app:lintVitalAnalyzePlaystoreRelease
  :app:lintVitalReportPlaystoreRelease
  :app:lintVitalPlaystoreRelease
)
LINT_RECORD="$OUT/lint-skipped.txt"
if [ "$SKIP_LINT" -eq 1 ]; then
  for task in "${LINT_TASKS[@]}"; do EXTRA+=( -x "$task" ); done
  {
    echo "NOTICE: this run excludes lintVital — the APKs about to be produced are NOT lint-verified."
    echo "        excluded tasks: ${LINT_TASKS[*]}"
  } >&2
fi

# --no-daemon because the inputs here are environment variables: a daemon reused from an earlier
# build may not see them, and a single-use daemon does not keep the passphrase in memory after the
# build. Either way the gate in app/build.gradle.kts would refuse rather than sign silently.
(cd "$ROOT/V2rayNG" && ./gradlew --no-daemon --no-configuration-cache --console=plain \
  :app:assemblePlaystoreRelease ${EXTRA[@]+"${EXTRA[@]}"})

shopt -s nullglob
APKS=("$OUT"/*.apk)
shopt -u nullglob
[ ${#APKS[@]} -gt 0 ] || die "Gradle reported success but $OUT holds no APK"

FAILED=0
for apk in "${APKS[@]}"; do
  name="$(basename "$apk")"
  if ! VERIFY="$(run_apksigner "$apk" verify --print-certs)"; then
    echo "UNSIGNED  $name — apksigner rejected it"
    mv "$apk" "$apk.rejected"
    FAILED=1
    continue
  fi
  GOT="$(printf '%s\n' "$VERIFY" | tr -d '\r' | sed -n 's/.*certificate SHA-256 digest: //p' | head -1 | tr 'A-F' 'a-f')"
  [ -n "$GOT" ] || { echo "NO-DIGEST $name"; mv "$apk" "$apk.rejected"; FAILED=1; continue; }
  if [ "$GOT" != "$WANT" ]; then
    echo "WRONG KEY $name"
    echo "          got  $GOT"
    echo "          want $WANT"
    mv "$apk" "$apk.rejected"
    FAILED=1
    continue
  fi
  printf '%s\n' "$(sha256sum "$apk")"
  printf '%-64s %10d  cert %s\n' "$name" "$(wc -c < "$apk" | tr -d ' ')" "$GOT"
done

if [ "$FAILED" -ne 0 ]; then
  rm -f "$LINT_RECORD"
  die "some artifacts did not match libs.lock and were renamed to *.apk.rejected — do not hand any of them out"
fi

# The record travels with the APKs: whoever copies the files out of this directory sees, without
# asking, that lint did not run. A run that did not skip lint removes the previous record instead of
# leaving it to mislabel these files.
if [ "$SKIP_LINT" -eq 1 ]; then
  {
    echo "lint did not run for the APKs in this directory."
    echo "  run:   $(date -Is)  tree: $(git -C "$ROOT" rev-parse HEAD)"
    echo "  excluded tasks: ${LINT_TASKS[*]}"
    echo "  reason: --skip-lint was passed (see FORK.md section 10 for why lint is unavailable here)"
  } > "$LINT_RECORD"
  echo "recorded the exclusion in $LINT_RECORD"
else
  rm -f "$LINT_RECORD"
fi

echo "signed with the pinned key; a release APK will not update a debug-signed install, so" \
     "removing the previous copy is part of handing it out"
