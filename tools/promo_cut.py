"""Cuts a closer picture out of one taken by promo_burst.py on a large display: half its width and
height, round the visitor, scaled to 1600x900 (docs/promo/2026-11, X-012). The visitor is found by
what differs from a picture of the same run with no one in it.

    python tools/promo_cut.py app/build/promo/bird_before 5 --empty 20 --near 3650 450 \
        --out docs/promo/2026-11/X-012_bird_before_16x9.png
"""

from __future__ import annotations

import argparse
import sys

import numpy as np
from PIL import Image


def visitor_at(picture: Image.Image, empty: Image.Image, near: tuple[int, int], reach: int = 500) -> tuple[int, int]:
    """The middle of what differs from [empty] within [reach] of [near]: the visitor's body."""
    a = np.asarray(picture.convert("RGB")).astype(int)
    b = np.asarray(empty.convert("RGB")).astype(int)
    changed = np.abs(a - b).sum(axis=2) > 120
    x0, y0 = max(near[0] - reach, 0), max(near[1] - reach, 0)
    ys, xs = np.nonzero(changed[y0:near[1] + reach, x0:near[0] + reach])
    if len(xs) == 0:
        raise SystemExit("nothing differs there")
    return int(np.median(xs)) + x0, int(np.median(ys)) + y0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("dir")
    parser.add_argument("index", type=int)
    parser.add_argument("--empty", type=int, required=True, help="a picture of the run with no visitor")
    parser.add_argument("--near", type=int, nargs=2, required=True, help="about where the visitor is (x y, full size)")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    picture = Image.open(f"{args.dir}/b{args.index:02d}.png").convert("RGB")
    cx, cy = visitor_at(picture, Image.open(f"{args.dir}/b{args.empty:02d}.png"), tuple(args.near))
    width, height = picture.size
    w, h = width // 2, height // 2
    left = min(max(cx - w // 2, 0), width - w)
    top = min(max(cy - h // 2, 0), height - h)
    picture.crop((left, top, left + w, top + h)).resize((1600, 900), Image.LANCZOS).save(args.out, optimize=True)
    print("visitor at", (cx, cy), "cut from", (left, top))
    return 0


if __name__ == "__main__":
    sys.exit(main())
