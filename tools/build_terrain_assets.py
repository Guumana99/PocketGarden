#!/usr/bin/env python3
"""Build the terrain art the game loads.

Inputs (tools/source_art):
  item_fence_wood.png        sheet of fence pieces on a plain background
  tile_river_atlas.png       reference only, see below

Outputs:
  app/src/main/res/drawable-nodpi/tile_river_autotile.png   river atlas, see below
  tools/raw_assets/struct_fence*.png   fence parts, picked up by asset_packer.py like any sprite

  (Until #74 it also made the paving, tile_path_cobblestone.png, from an illustration of that
  name; the paving is now prepare_drawable_art.py's floor_cobblestone.png, and the illustration
  is kept in source_art unused.)

  The river goes into drawable-nodpi so the app can name them as R.drawable.*: a missing or
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


# Water colours start from those of tile_river_atlas.png and are spread into a range of depths.
WATER_SHALLOW = (186, 228, 246)   # at the bank, where the bed shows through
WATER = (138, 200, 238)
WATER_DEEP = (84, 150, 208)       # mid-stream
FOAM = (244, 251, 255)
BANK_WET = (104, 78, 58)          # mud at the waterline
BANK_DRY = (158, 122, 86)         # earth further out
BANK_LIP = (92, 112, 62)          # where the turf starts
PEBBLE = (206, 196, 176)
GLINT = (255, 255, 255)

SUPERSAMPLE = 4       # drawn this many times larger, then scaled down, for smooth edges
CHANNEL = 0.29        # half-width of the water, as a fraction of the tile
BANK_WIDTH = 0.07
POND = 0.35           # radius of a tile with no river neighbours
FRAMES = 4            # animation frames; the waves travel one wavelength over the loop
FLOW_WAVES = 1.25     # wavelengths per tile along the flow; see river_cell for the .25
LAP_WAVES = 2.25      # the same for the foam lapping at the bank


def _lerp(a, b, t):
    """Blend colour a into colour b by t (an array of 0..1), giving an (h, w, 3) array."""
    t = np.clip(t, 0.0, 1.0)[..., None]
    return np.asarray(a, dtype=np.float64) * (1.0 - t) + np.asarray(b, dtype=np.float64) * t


def _smooth(t):
    t = np.clip(t, 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def river_cell(mask: int, frame: int = 0) -> Image.Image:
    """
    One river tile. Everything is worked out from two numbers per pixel: `u`, how far the pixel is
    from the middle of the stream (a pool in the centre of the tile plus an arm to every edge with
    a river neighbour), and `s`, how far it is from the centre of the tile.

    `u` decides what the pixel is: deep water, shallows, the foam at the waterline, wet mud, dry
    earth, turf. None of that depends on the frame, so the outline of the water never moves.

    The animation is a train of waves running downstream, which is towards the east and the south
    of the grid (down the screen). Their phase at a pixel is FLOW_WAVES * (x + y) - frame / FRAMES,
    with x and y running from 0 to 1 across the tile. FLOW_WAVES is a whole number plus 1 / FRAMES,
    which means the pattern does not repeat from tile to tile but is one frame further on in the
    next tile downstream. The game makes up for exactly that by showing each tile one frame behind
    its upstream neighbour (see RiverAutotile.frameFor): with that offset the waves run on from
    tile to tile without a seam, and the stream is one moving surface instead of tiles that all
    blink together.
    """
    n = CELL * SUPERSAMPLE
    axis = (np.arange(n) + 0.5) / n
    x, y = np.meshgrid(axis, axis)
    dx, dy = x - 0.5, y - 0.5
    s = np.hypot(dx, dy)

    # Distance to the stream's middle line: the centre point, and a half-line per arm.
    u = s.copy()
    if mask & NORTH:
        u = np.minimum(u, np.where(dy < 0, np.abs(dx), s))
    if mask & SOUTH:
        u = np.minimum(u, np.where(dy > 0, np.abs(dx), s))
    if mask & WEST:
        u = np.minimum(u, np.where(dx < 0, np.abs(dy), s))
    if mask & EAST:
        u = np.minimum(u, np.where(dx > 0, np.abs(dy), s))
    if mask == 0:
        u = u - (POND - CHANNEL)  # a pond is wider than a stream

    t = frame / FRAMES
    two_pi = 2.0 * np.pi

    # --- Water: shallow and pale at the bank, deep and blue mid-stream -----------------------
    depth = _smooth(1.0 - u / CHANNEL)
    water = np.where((depth < 0.5)[..., None],
                     _lerp(WATER_SHALLOW, WATER, depth * 2.0),
                     _lerp(WATER, WATER_DEEP, depth * 2.0 - 1.0))
    # The bank's shadow on the water just inside the waterline.
    shade = _smooth((u - (CHANNEL - 0.075)) / 0.05) * (u < CHANNEL - 0.02)
    water = water * (1.0 - 0.16 * shade[..., None])

    # Bands of light travelling downstream, broken up across the stream so they read as waves
    # and not as stripes. The wobble repeats exactly once per tile, so it meets itself at the
    # edges. Whole periods fit into the loop, so frame FRAMES is frame 0 again.
    across = np.sin(two_pi * (dx * 4.0 + dy * 3.0)) * 0.5 + np.sin(two_pi * (dx * 2.0 - dy * 5.0)) * 0.5
    along = x + y  # grows downstream: 0 at the tile's north-west corner, 2 at its south-east
    wave = np.sin(two_pi * (FLOW_WAVES * along - t) + across * 0.9)
    crest = _smooth((wave - 0.72) / 0.28) * _smooth(depth * 1.6)
    water = water + (np.asarray(FOAM) - water) * (0.6 * crest[..., None])
    trough = _smooth((-wave - 0.6) / 0.4) * depth
    water = water * (1.0 - 0.07 * trough[..., None])

    # Glints: small hard sparkles on some crests. They drift downstream with the water, and
    # like the waves they are laid out so that a tile one frame behind picks up exactly where its
    # upstream neighbour leaves off: each term below changes by a whole number of turns from a
    # pixel in one tile and frame to the same pixel one tile downstream and one frame earlier.
    quarter = frame / FRAMES
    sparkle = (np.sin(two_pi * (11.25 * x + 3.25 * y - quarter)) *
               np.sin(two_pi * (-4.75 * x + 13.25 * y - quarter)))
    glint = (sparkle > 0.84) & (crest > 0.35)
    water = np.where(glint[..., None], np.asarray(GLINT, dtype=np.float64), water)

    # Foam lapping at the waterline: a thin pale line that thickens and thins along the bank
    # and breathes with the frames.
    lap = 0.5 + 0.5 * np.sin(two_pi * (LAP_WAVES * along - t))
    foam_width = 0.010 + 0.014 * lap
    foam = _smooth((u - (CHANNEL - foam_width)) / foam_width)
    water = water + (np.asarray(FOAM) - water) * (0.78 * foam[..., None])

    # --- Bank: wet mud at the water, drying earth, then the lip of the turf -------------------
    across_bank = (u - CHANNEL) / BANK_WIDTH
    bank = _lerp(BANK_WET, BANK_DRY, _smooth(across_bank / 0.75))
    lip = _smooth((across_bank - 0.72) / 0.2)
    bank = bank + (np.asarray(BANK_LIP) - bank) * lip[..., None]
    # A scatter of pebbles on the earth, the same in every frame.
    scatter = (np.sin(two_pi * x * 17.0 + 1.3 * np.sin(two_pi * y * 9.0)) *
               np.sin(two_pi * y * 19.0 + 1.7 * np.sin(two_pi * x * 7.0)))
    pebbles = (scatter > 0.93) & (across_bank > 0.15) & (across_bank < 0.7)
    bank = np.where(pebbles[..., None], np.asarray(PEBBLE, dtype=np.float64), bank)

    is_water = u < CHANNEL
    inside = u < CHANNEL + BANK_WIDTH
    rgb = np.where(is_water[..., None], water, bank)
    # Outside the bank the pixels are see-through; they still carry the turf colour, so that
    # scaling down does not pull a dark fringe in from them.
    rgb = np.where(inside[..., None], rgb, np.asarray(BANK_LIP, dtype=np.float64))
    alpha = np.where(inside, 255.0, 0.0)

    rgba = np.dstack([np.clip(rgb, 0, 255), alpha]).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA").resize((CELL, CELL), Image.LANCZOS)


def build_river_atlas() -> None:
    block = CELL * 4
    atlas = Image.new("RGBA", (block, block * FRAMES), (0, 0, 0, 0))
    for frame in range(FRAMES):
        for mask in range(16):
            atlas.paste(river_cell(mask, frame), ((mask % 4) * CELL, frame * block + (mask // 4) * CELL))
    TILES_OUT.mkdir(parents=True, exist_ok=True)
    atlas.save(TILES_OUT / "tile_river_autotile.png")


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
    build_fence()
    print("written:", TILES_OUT / "tile_river_autotile.png",
          *(RAW_ASSETS / f"{name}.png" for name in FENCE_PARTS), sep="\n  ")
    return 0


if __name__ == "__main__":
    sys.exit(main())
