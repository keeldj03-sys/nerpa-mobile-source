#!/usr/bin/env python3
"""Regenerates the launcher icon densities from tool/launcher-icon/nerpa-logo.png.

Upstream merges touch res/mipmap-*, so the brand assets are rebuilt from the master logo instead of
being hand-patched: python3 tool/launcher-icon/make-launcher-icon.py
"""
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
LOGO = os.path.join(HERE, "nerpa-logo.png")
RES = os.path.join(
    os.path.dirname(os.path.dirname(HERE)), "V2rayNG", "app", "src", "main", "res"
)
PAPER = (255, 255, 255, 255)
INK = (5, 13, 36, 255)

# Launcher icon size, then the share of it taken by the shield, which is now cropped to its ink and
# slightly taller than wide. A circular launcher mask clips the shield's half-diagonal, not its
# width, so the adaptive foreground stays near 0.5 and the round legacy tile near 0.68.
# `ic_launcher_background.xml` carries PAPER and must stay in sync with it.
LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FOREGROUND = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
LEGACY_FILL = 0.74
LEGACY_ROUND_FILL = 0.68
FOREGROUND_FILL = 0.50


def shield():
    """The logo with its paper keyed out and cropped to the ink, so each target can supply its own
    background and the fills below are measured against the shield rather than the master's margins."""
    src = Image.open(LOGO).convert("L")
    alpha = src.point(lambda lum: max(0, min(255, int((250 - lum) * 255 / 170))))
    box = alpha.point(lambda lum: 255 if lum > 64 else 0).getbbox()
    alpha = alpha.crop(box)
    out = Image.new("RGBA", alpha.size, (0, 0, 0, 0))
    out.putalpha(alpha)
    out.paste(Image.new("RGBA", alpha.size, INK), (0, 0), out)
    return out


def scaled(mark, target):
    ratio = target / max(mark.size)
    return mark.resize((round(mark.width * ratio), round(mark.height * ratio)), Image.LANCZOS)


def paste(canvas, mark):
    canvas.paste(mark, ((canvas.width - mark.width) // 2, (canvas.height - mark.height) // 2), mark)


def tile(size, fill, mark):
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(out)
    if fill == "square":
        draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=round(size * 0.18), fill=PAPER)
    else:
        draw.ellipse((0, 0, size - 1, size - 1), fill=PAPER)
    paste(out, scaled(mark, round(size * (LEGACY_FILL if fill == "square" else LEGACY_ROUND_FILL))))
    return out


def main():
    if not os.path.isfile(LOGO):
        print("missing master logo: " + LOGO)
        return 1
    mark = shield()

    for density, size in LEGACY.items():
        folder = os.path.join(RES, "mipmap-" + density)
        if not os.path.isdir(folder):
            print("missing density folder: " + folder)
            return 1
        tile(size, "square", mark).save(os.path.join(folder, "ic_launcher.png"), optimize=True)
        round_name = "ic_launcher_round.png"
        if os.path.isfile(os.path.join(folder, round_name)):
            tile(size, "round", mark).save(os.path.join(folder, round_name), optimize=True)

    for density, size in FOREGROUND.items():
        folder = os.path.join(RES, "mipmap-" + density)
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        paste(canvas, scaled(mark, round(size * FOREGROUND_FILL)))
        canvas.save(os.path.join(folder, "ic_launcher_foreground.png"), optimize=True)

    print("launcher icons written for " + ", ".join(LEGACY))
    return 0


if __name__ == "__main__":
    sys.exit(main())
