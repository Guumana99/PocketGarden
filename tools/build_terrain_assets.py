#!/usr/bin/env python3
"""Build the parts of the fence.

Inputs (tools/source_art):
  item_fence_wood.png        sheet of fence pieces on a plain background

Outputs:
  tools/raw_assets/struct_fence*.png   fence parts, picked up by asset_packer.py like any sprite

  (Until #74 it also made the paving; the paving is now prepare_drawable_art.py's
  floor_cobblestone.png. It also drew the river until the water and the banks became pictures,
  tools/prepare_water_art.py.)

Usage:
  python tools/build_terrain_assets.py

Requires:  pip install Pillow numpy
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "tools" / "source_art"
RAW_ASSETS = ROOT / "tools" / "raw_assets"

SENTINEL = (255, 0, 255)  # a colour the art does not use, to mark flood-filled background


def clear_background(image: Image.Image, tolerance: int, flood: bool) -> Image.Image:
    """Make the plain background transparent.

    flood=True only clears background connected to the image border, which protects light
    details inside the artwork. flood=False clears every pixel of the background colour,
    which is needed where the background shows through holes (between fence rails).
    """
    rgb = image.convert("RGB")
    background = rgb.getpixel((2, 2))
    if flood:
        work = rgb.copy()
        w, h = work.size
        for seed in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1), (w // 2, 0), (0, h // 2)]:
            if work.getpixel(seed) != SENTINEL:
                ImageDraw.floodfill(work, seed, SENTINEL, thresh=tolerance)
        mask = np.all(np.array(work) == np.array(SENTINEL), axis=-1)
    else:
        diff = np.abs(np.array(rgb).astype(int) - np.array(background)).max(axis=-1)
        mask = diff <= tolerance
    rgba = np.array(rgb.convert("RGBA"))
    rgba[mask, 3] = 0
    return Image.fromarray(rgba, "RGBA")


def opaque_bbox(image: Image.Image):
    return image.getchannel("A").point(lambda a: 255 if a > 32 else 0).getbbox()


# Fence parts cut from the sheet: output name -> (left, top, right, bottom) on the 1024 px sheet.
# Each is drawn upright, facing the viewer, exactly as on the sheet: none is sheared or rotated.
FENCE_PARTS = {
    "struct_fence": (20, 30, 585, 290),        # picket section, five pickets on one rail
    "struct_fence_rail": (40, 705, 460, 990),  # two posts joined by two rails
    "struct_fence_post": (590, 790, 690, 990), # a single capped post
}

# Atlas pixels per source pixel. One value for all parts, so they keep the sizes they have
# relative to each other on the sheet (the sprite atlas is drawn at 720 px per tile).
FENCE_SCALE = 520 / 545


def build_fence() -> None:
    sheet = clear_background(Image.open(SOURCE / "item_fence_wood.png"), tolerance=14, flood=False)
    for name, box in FENCE_PARTS.items():
        region = sheet.crop(box)
        part = region.crop(opaque_bbox(region))
        size = (round(part.width * FENCE_SCALE), round(part.height * FENCE_SCALE))
        part.resize(size, Image.LANCZOS).save(RAW_ASSETS / f"{name}.png")


def main() -> int:
    build_fence()
    print("written:",
          *(RAW_ASSETS / f"{name}.png" for name in FENCE_PARTS), sep="\n  ")
    return 0


if __name__ == "__main__":
    sys.exit(main())
