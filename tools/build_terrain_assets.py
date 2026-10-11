#!/usr/bin/env python3
"""Build the terrain art the game loads.

Inputs (tools/source_art):
  tile_path_cobblestone.png  cobblestone texture
  item_fence_wood.png        sheet of fence pieces on a plain background
  tile_river_atlas.png       reference only, see below

Outputs:
  app/src/main/res/drawable-nodpi/tile_river_autotile.png   river atlas, see below
  app/src/main/res/drawable-nodpi/tile_path_cobblestone.png
  tools/raw_assets/struct_fence*.png   fence parts, picked up by asset_packer.py like any sprite

  The two tiles go into drawable-nodpi so the app can name them as R.drawable.*: a missing or
  misnamed file then fails the build instead of silently drawing nothing, and nodpi keeps
  Android from rescaling them per screen density.

River autotile
  The game draws a river tile by looking at its four neighbours and building a mask:
  N = 1, E = 2, S = 4, W = 8 (a bit is set when that neighbour is river too). The cell for a
  mask sits at column mask % 4, row mask // 4 of a 4x4 block. The file holds FRAMES such
  blocks stacked top to bottom, one per animation frame; the game plays them in a loop. Cells are seen from
  straight above, north up; the game lays them onto the isometric ground itself. Whatever is
  not water or bank is transparent, so the lawn shows through.

  For tiles to join, the water must leave every connected edge at the same place and width.
  The pieces in tile_river_atlas.png do not: each leaves its edges at a different position and
  width, the sheet has no piece for several masks (no lone pond, no T-junctions), and it is not
  laid out by mask. So the atlas is drawn here instead, in that sheet's colours. To use
  hand-drawn art, replace tile_river_autotile.png with a sheet that follows the layout above
  (a single 4x4 block is fine: it is then shown without animation).

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
TILES_OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
RAW_ASSETS = ROOT / "tools" / "raw_assets"

CELL = 128                # size of one cell in the generated river atlas
NORTH, EAST, SOUTH, WEST = 1, 2, 4, 8
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


# Colours taken from tile_river_atlas.png.
WATER = (150, 205, 240, 255)
WATER_DEEP = (96, 160, 214, 255)
BANK = (132, 92, 70, 255)
RIPPLE = (236, 248, 255, 255)

SUPERSAMPLE = 4       # drawn this many times larger, then scaled down, for smooth edges
CHANNEL = 0.29        # half-width of the water, as a fraction of the tile
BANK_WIDTH = 0.06
POND = 0.35           # radius of a tile with no river neighbours
FRAMES = 4            # animation frames; ripples travel one full step over the loop


def _channel(draw: ImageDraw.ImageDraw, mask: int, half: float, colour) -> None:
    """A round pool in the middle plus an arm of the same width to every connected edge."""
    n = CELL * SUPERSAMPLE
    c = n / 2
    r = (POND - CHANNEL + half if mask == 0 else half) * n
    draw.ellipse((c - r, c - r, c + r, c + r), fill=colour)
    h = half * n
    if mask & NORTH:
        draw.rectangle((c - h, 0, c + h, c), fill=colour)
    if mask & SOUTH:
        draw.rectangle((c - h, c, c + h, n), fill=colour)
    if mask & WEST:
        draw.rectangle((0, c - h, c, c + h), fill=colour)
    if mask & EAST:
        draw.rectangle((c, c - h, n, c + h), fill=colour)


def river_cell(mask: int, frame: int = 0) -> Image.Image:
    n = CELL * SUPERSAMPLE
    cell = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    draw = ImageDraw.Draw(cell)
    _channel(draw, mask, CHANNEL + BANK_WIDTH, BANK)
    _channel(draw, mask, CHANNEL, WATER_DEEP)
    _channel(draw, mask, CHANNEL - 0.045, WATER)

    # Ripples. Along each arm two of them, half an arm apart, drift from the edge towards the
    # pool and wrap round, so the last frame leads back into the first; the two in the pool sway
    # from side to side. Every arm moves the same way relative to its edge, which is what lets
    # neighbouring tiles of any shape line up.
    c, w = n / 2, max(2, n // 48)
    t = frame / FRAMES

    def ripple(x: float, y: float, length: float, vertical: bool) -> None:
        x, y, half = x * n, y * n, length * n / 2
        end = (x, y + half) if vertical else (x + half, y)
        start = (x, y - half) if vertical else (x - half, y)
        draw.line((start, end), fill=RIPPLE, width=w)

    sway = 0.03 * (1 if frame % 2 == 0 else -1) * (1 if frame < 2 else 0.4)
    ripple(0.44 + sway, 0.46, 0.12, False)
    ripple(0.57 - sway, 0.56, 0.10, False)
    arm = 0.36  # how far in from the edge a ripple travels
    for k in (0, 1):
        d = ((t + k / 2) % 1.0) * arm + 0.05
        side = 0.06 if k == 0 else -0.06
        if mask & NORTH:
            ripple(0.5 + side, d, 0.10, True)
        if mask & SOUTH:
            ripple(0.5 - side, 1 - d, 0.10, True)
        if mask & WEST:
            ripple(d, 0.5 - side, 0.10, False)
        if mask & EAST:
            ripple(1 - d, 0.5 + side, 0.10, False)
    return cell.resize((CELL, CELL), Image.LANCZOS)


def build_river_atlas() -> None:
    block = CELL * 4
    atlas = Image.new("RGBA", (block, block * FRAMES), (0, 0, 0, 0))
    for frame in range(FRAMES):
        for mask in range(16):
            atlas.paste(river_cell(mask, frame), ((mask % 4) * CELL, frame * block + (mask // 4) * CELL))
    TILES_OUT.mkdir(parents=True, exist_ok=True)
    atlas.save(TILES_OUT / "tile_river_autotile.png")


def build_cobblestone() -> None:
    texture = Image.open(SOURCE / "tile_path_cobblestone.png").convert("RGB")
    # A quarter of the sheet per tile keeps the stones a readable size on a small tile.
    half = texture.width // 2
    tile = texture.crop((0, 0, half, half)).resize((256, 256), Image.LANCZOS)
    TILES_OUT.mkdir(parents=True, exist_ok=True)
    tile.save(TILES_OUT / "tile_path_cobblestone.png")


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
    build_river_atlas()
    build_cobblestone()
    build_fence()
    print("written:", TILES_OUT / "tile_river_autotile.png", TILES_OUT / "tile_path_cobblestone.png",
          *(RAW_ASSETS / f"{name}.png" for name in FENCE_PARTS), sep="\n  ")
    return 0


if __name__ == "__main__":
    sys.exit(main())
