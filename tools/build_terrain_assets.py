#!/usr/bin/env python3
"""Build the terrain art the game loads.

Inputs (tools/source_art):
  tile_path_cobblestone.png  cobblestone texture
  item_fence_wood.png        sheet of fence pieces on a plain background
  tile_river_atlas.png       reference only, see below

Outputs:
  app/src/main/assets/tiles/river_autotile.png   4x4 atlas, one cell per neighbour mask
  app/src/main/assets/tiles/floor_cobblestone.png
  tools/raw_assets/struct_fence.png              picked up by asset_packer.py like any sprite

River autotile
  The game draws a river tile by looking at its four neighbours and building a mask:
  N = 1, E = 2, S = 4, W = 8 (a bit is set when that neighbour is river too). The cell for a
  mask sits at column mask % 4, row mask // 4 of river_autotile.png. Cells are seen from
  straight above, north up; the game lays them onto the isometric ground itself. Whatever is
  not water or bank is transparent, so the lawn shows through.

  For tiles to join, the water must leave every connected edge at the same place and width.
  The pieces in tile_river_atlas.png do not: each leaves its edges at a different position and
  width, the sheet has no piece for several masks (no lone pond, no T-junctions), and it is not
  laid out by mask. So the atlas is drawn here instead, in that sheet's colours. To use
  hand-drawn art, replace river_autotile.png with a sheet that follows the layout above.

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
TILES_OUT = ROOT / "app" / "src" / "main" / "assets" / "tiles"
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


def river_cell(mask: int) -> Image.Image:
    n = CELL * SUPERSAMPLE
    cell = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    draw = ImageDraw.Draw(cell)
    _channel(draw, mask, CHANNEL + BANK_WIDTH, BANK)
    _channel(draw, mask, CHANNEL, WATER_DEEP)
    _channel(draw, mask, CHANNEL - 0.045, WATER)

    # A few short ripples: two in the pool, one along each arm.
    c, w = n / 2, max(2, n // 48)
    def ripple(x: float, y: float, length: float, vertical: bool) -> None:
        x, y, half = x * n, y * n, length * n / 2
        end = (x, y + half) if vertical else (x + half, y)
        start = (x, y - half) if vertical else (x - half, y)
        draw.line((start, end), fill=RIPPLE, width=w)
    ripple(0.44, 0.46, 0.12, False)
    ripple(0.57, 0.56, 0.10, False)
    if mask & NORTH:
        ripple(0.45, 0.16, 0.12, True)
    if mask & SOUTH:
        ripple(0.56, 0.84, 0.12, True)
    if mask & WEST:
        ripple(0.16, 0.55, 0.12, False)
    if mask & EAST:
        ripple(0.84, 0.45, 0.12, False)
    return cell.resize((CELL, CELL), Image.LANCZOS)


def build_river_atlas() -> None:
    atlas = Image.new("RGBA", (CELL * 4, CELL * 4), (0, 0, 0, 0))
    for mask in range(16):
        atlas.paste(river_cell(mask), ((mask % 4) * CELL, (mask // 4) * CELL))
    TILES_OUT.mkdir(parents=True, exist_ok=True)
    atlas.save(TILES_OUT / "river_autotile.png")


def build_cobblestone() -> None:
    texture = Image.open(SOURCE / "tile_path_cobblestone.png").convert("RGB")
    # A quarter of the sheet per tile keeps the stones a readable size on a small tile.
    half = texture.width // 2
    tile = texture.crop((0, 0, half, half)).resize((256, 256), Image.LANCZOS)
    TILES_OUT.mkdir(parents=True, exist_ok=True)
    tile.save(TILES_OUT / "floor_cobblestone.png")


def build_fence() -> None:
    sheet = clear_background(Image.open(SOURCE / "item_fence_wood.png"), tolerance=14, flood=False)
    # The five-picket section in the top-left corner of the sheet.
    region = sheet.crop((20, 30, 585, 290))
    section = region.crop(opaque_bbox(region))
    # Sized for the sprite atlas, where one tile is 720 px wide before packing.
    width = 520
    section = section.resize((width, round(section.height * width / section.width)), Image.LANCZOS)
    section.save(RAW_ASSETS / "struct_fence.png")


def main() -> int:
    build_river_atlas()
    build_cobblestone()
    build_fence()
    print("written:", TILES_OUT / "river_autotile.png", TILES_OUT / "floor_cobblestone.png",
          RAW_ASSETS / "struct_fence.png", sep="\n  ")
    return 0


if __name__ == "__main__":
    sys.exit(main())
