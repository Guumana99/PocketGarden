"""Lays marks over the promotional pictures after they are taken (docs/promo/2026-11): red rings
round the rain button (X-026) and the soil's names under its cells (X-019). Nothing of this is in
the game: the pictures are taken plain and marked here.

    python tools/promo_marks.py            # every marked picture, from docs/promo/2026-11/raw
    python tools/promo_marks.py --only X-026_rain_button_1_16x9.png

The plain pictures are kept in docs/promo/2026-11/raw under the same names, so that marking again
never marks twice.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
DIR = ROOT / "docs/promo/2026-11"
RAW = DIR / "raw"

RED = (226, 32, 32)
RING_WIDTH = 7

# Where the rain button stands in each picture of X-026 (1600 x 900): its middle and the ring's radius.
RINGS = {
    "X-026_rain_button_1_16x9.png": [((800, 195), 92)],   # the brown text button at the top
    "X-026_rain_button_2_16x9.png": [((800, 205), 96)],   # the rain cloud in the sky (and its rain)
    "X-026_rain_button_3_16x9.png": [((1542, 284), 52)],  # the cloud at the head of the right-hand column
}

# The names under the three cells of X-019, left to right, and where they go (set by promo_capture.py).
SOIL_NAMES = ["ふつう", "よい", "ふかふか"]


def font(size: int) -> ImageFont.FreeTypeFont:
    for name in ("YuGothB.ttc", "meiryob.ttc", "meiryo.ttc", "msgothic.ttc"):
        path = Path("C:/Windows/Fonts") / name
        if path.exists():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default(size)


def ring(image: Image.Image, rings: list[tuple[tuple[int, int], int]]) -> Image.Image:
    out = image.convert("RGB").copy()
    draw = ImageDraw.Draw(out)
    for (x, y), r in rings:
        draw.ellipse((x - r, y - r, x + r, y + r), outline=RED, width=RING_WIDTH)
    return out


def label(image: Image.Image, labels: list[tuple[str, tuple[int, int]]], size: int = 34) -> Image.Image:
    """Small white words, a soft dark edge round them so that they read on the earth, centred at each point."""
    out = image.convert("RGB").copy()
    draw = ImageDraw.Draw(out)
    face = font(size)
    for text, (x, y) in labels:
        draw.text((x, y), text, font=face, fill=(255, 255, 255), anchor="mm", stroke_width=3, stroke_fill=(40, 30, 20))
    return out


def soil_points(path: Path) -> list[tuple[int, int]]:
    """The label points promo_capture.py wrote beside the plain picture (x,y per line)."""
    return [tuple(int(v) for v in line.split(",")) for line in path.read_text().split()]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--only", nargs="*")
    args = parser.parse_args()
    return main_for(args.only)


def main_for(only: list[str] | None) -> int:
    """Marks the pictures named in [only] (all of them for None)."""
    wanted = set(only) if only else None
    for name, rings in RINGS.items():
        if wanted is None or name in wanted:
            ring(Image.open(RAW / name), rings).save(DIR / name, optimize=True)
            print("marked", name)
    soil = "X-019_soil_16x9.png"
    if (wanted is None or soil in wanted) and (RAW / soil).exists():
        points = soil_points(RAW / "X-019_soil_16x9.points.txt")
        label(Image.open(RAW / soil), list(zip(SOIL_NAMES, points))).save(DIR / soil, optimize=True)
        print("marked", soil)
    return 0


if __name__ == "__main__":
    sys.exit(main())
