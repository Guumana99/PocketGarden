#!/usr/bin/env python3
"""Cut the joined-up greenhouse's parts out of its two originals, and draw its icon.

The originals (tools/source_art/struct_greenhouse_wall.png, struct_greenhouse_door.png) are one
section of wall seen from the front on white, its windows empty: a wooden frame with a post at each
end, on a low bed of stones. Out of them come, at the atlas' source scale of 720 px a tile:

  struct_greenhouse_wall.png   a wall from the middle of one end post to the middle of the other
  struct_greenhouse_door.png   the same with the door in it
  struct_greenhouse_post.png   the post at a corner, stones and all

Walls side by side make one whole post where they meet, so a long side needs no joining parts. Across
the screen a cell's edge is half a tile ([SPAN]), and a wall stands [WALL_HEIGHT] tall; the garden
shears it along the edge as it does a fence's rail, with the bottom of the stones on the edge. The
windows come out empty: the glass is drawn in code, behind the frame.

The icon (res/drawable-nodpi/icon_floor_greenhouse.png) is the greenhouse of the book: two cells
by two, nothing growing, roofed with thin frames (GreenhouseLayout's roof, by the same numbers).

Usage:
  python tools/prepare_greenhouse_art.py     # writes raw_assets/, the overrides and the icon

Requires Pillow and numpy.
"""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from prepare_building_art import OVERRIDES, RAW, SOURCE, TILE
from prepare_drawable_art import ICONS, to_icon
from prepare_fence_art import SPAN, cut_plain_white

#: How tall a wall stands, in tiles.
WALL_HEIGHT = 0.6

#: The part of the originals kept: the middles of the end posts across, the top of the frame to
#: the bottom of the stones down.
LEFT, RIGHT, TOP, BOTTOM = 76, 947, 148, 875

#: The columns of a post, in the original.
POST = (52, 100)

#: A post's width in source pixels: the corner of two sheared walls wants it a little stouter.
POST_WIDTH = 36

#: The roof of the icon: as GreenhouseLayout has it for a span of two cells.
ROOF_RISE = 0.2
FRAME = (58, 44, 34, 255)


def fitted(cut: np.ndarray, width: int, height: int) -> np.ndarray:
    """[cut] brought to [width] x [height], its see-through parts keeping their edges clean."""
    picture = Image.fromarray(cut, "RGBA").convert("RGBa").resize((width, height), Image.LANCZOS).convert("RGBA")
    return np.array(picture)


def prepare() -> dict[str, tuple[np.ndarray, dict]]:
    tall = int(round(WALL_HEIGHT * TILE))
    wall = cut_plain_white("struct_greenhouse_wall.png")
    door = cut_plain_white("struct_greenhouse_door.png")
    on_edge = {"anchorX": 0.5, "anchorY": 1.0, "attachX": 0.5, "attachY": 1.0}
    return {
        "struct_greenhouse_wall": (fitted(wall[TOP:BOTTOM, LEFT:RIGHT], SPAN, tall), on_edge),
        "struct_greenhouse_door": (fitted(door[TOP:BOTTOM, LEFT:RIGHT], SPAN, tall), on_edge),
        "struct_greenhouse_post": (fitted(wall[TOP:BOTTOM, POST[0]:POST[1]], POST_WIDTH, tall), {"anchorX": 0.5, "anchorY": 1.0}),
    }


def icon(prepared: dict[str, tuple[np.ndarray, dict]]) -> np.ndarray:
    """The greenhouse two cells by two, drawn as the garden draws it, at a tile [T] px across."""
    T = 1024
    H = T // 2
    width, height = 2 * T + 80, T + int((WALL_HEIGHT + ROOF_RISE) * T) + 80
    origin = (width / 2, height - 40 - T)  # the far corner of the cells, on the ground

    def at(x: float, y: float, z: float = 0.0) -> tuple[float, float]:
        return origin[0] + (x - y) * T / 2, origin[1] + (x + y) * H / 2 - z * T

    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))

    def polygon(points: list[tuple[float, float]], fill: tuple[int, int, int, int]) -> None:
        layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        ImageDraw.Draw(layer).polygon(points, fill=fill)
        canvas.alpha_composite(layer)

    def panel(name: str, left: tuple[float, float], right: tuple[float, float]) -> None:
        """A wall on the ground from [left] to [right], sheared along them."""
        sprite = Image.fromarray(prepared[name][0], "RGBA")
        (x0, y0), (x1, y1) = at(*left), at(*right)
        wide, tall = int(x1 - x0), int(WALL_HEIGHT * T)
        slope = (y1 - y0) / (x1 - x0)
        big = sprite.convert("RGBa").resize((wide, tall), Image.LANCZOS)
        pad = abs(int(slope * wide)) + 2
        sheared = big.transform((wide, tall + pad), Image.AFFINE, (1, 0, 0, -slope, 1, -pad if slope < 0 else 0), Image.BICUBIC)
        top = y0 - tall - (pad if slope < 0 else 0)
        canvas.alpha_composite(sheared.convert("RGBA"), (int(x0), int(top)))

    def glass(left: tuple[float, float], right: tuple[float, float], alpha: int) -> None:
        a, b = at(*left), at(*right)
        polygon([a, b, at(*right, WALL_HEIGHT), at(*left, WALL_HEIGHT)], (200, 225, 235, alpha))

    def post(x: float, y: float) -> None:
        sprite = Image.fromarray(prepared["struct_greenhouse_post"][0], "RGBA")
        wide, tall = int(POST_WIDTH / TILE * T), int(WALL_HEIGHT * T)
        sx, sy = at(x, y)
        canvas.alpha_composite(sprite.resize((wide, tall), Image.LANCZOS), (int(sx - wide / 2), int(sy - tall)))

    def edges(x: int, y: int) -> dict[str, tuple[tuple[float, float], tuple[float, float]]]:
        return {
            "north": ((x, y), (x + 1, y)),
            "east": ((x + 1, y + 1), (x + 1, y)),
            "south": ((x, y + 1), (x + 1, y + 1)),
            "west": ((x, y + 1), (x, y)),
        }

    wall, door = "struct_greenhouse_wall", "struct_greenhouse_door"
    # The back walls, the earth, the front walls: as the garden has them.
    post(0, 0)
    for side, cells in (("north", ((1, 0), (0, 0))), ("west", ((0, 0), (0, 1)))):
        for cell in cells:
            left, right = edges(*cell)[side]
            glass(left, right, 40)
            panel(wall, left, right)
    soil = Image.open(SOURCE / "tile_soil_flat.png").convert("RGB")
    size = 2 * T
    flat = soil.resize((size, size // 2), Image.LANCZOS).convert("RGBA")
    diamond = Image.new("L", flat.size, 0)
    ImageDraw.Draw(diamond).polygon([(size / 2, 0), (size, size / 4), (size / 2, size / 2), (0, size / 4)], fill=255)
    flat.putalpha(diamond)
    canvas.alpha_composite(flat, (int(origin[0] - T), int(origin[1])))
    post(2, 0)
    post(0, 2)
    for side, cell, name in (("south", (0, 1), wall), ("south", (1, 1), door), ("east", (1, 0), wall), ("east", (1, 1), wall)):
        left, right = edges(*cell)[side]
        glass(left, right, 22)
        panel(name, left, right)
    post(2, 2)
    # The roof: thin glass and thin frames, its ridge along x.
    ridge = 1.0
    top = WALL_HEIGHT + ROOF_RISE
    for near, far in ((0.0, ridge), (2.0, ridge)):
        polygon([at(0, near, WALL_HEIGHT), at(2, near, WALL_HEIGHT), at(2, far, top), at(0, far, top)], (205, 228, 238, 22))
    draw = ImageDraw.Draw(canvas)
    line = max(6, T // 56)
    for x in (0, 1, 2):
        draw.line([at(x, 0, WALL_HEIGHT), at(x, ridge, top), at(x, 2, WALL_HEIGHT)], fill=FRAME, width=line, joint="curve")
    draw.line([at(0, ridge, top), at(2, ridge, top)], fill=FRAME, width=line + 2)
    return to_icon(np.array(canvas.crop(canvas.getbbox())))


def main() -> int:
    prepared = prepare()
    overrides = json.loads(OVERRIDES.read_text(encoding="utf-8"))
    for asset_id, (rgba, override) in prepared.items():
        Image.fromarray(rgba, "RGBA").save(RAW / f"{asset_id}.png", optimize=True)
        overrides[asset_id] = override
        print(f"{asset_id:30s} {rgba.shape[1]}x{rgba.shape[0]}  {override}")
    lines = [f'  "{k}": {json.dumps(v)}' for k, v in overrides.items()]
    OVERRIDES.write_text("{\n" + ",\n".join(lines) + "\n}\n", encoding="utf-8")
    Image.fromarray(icon(prepared), "RGBA").save(ICONS / "icon_floor_greenhouse.png", optimize=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
