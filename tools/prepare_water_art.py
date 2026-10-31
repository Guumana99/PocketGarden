#!/usr/bin/env python3
"""Make the river's and the pond's water, and the river's banks, out of two pictures in source_art/.

  water_surface.png (seen from straight above)
    water_surface.png   the water, made to tile and brought to the colour of the pond: a little
                        green, and light;
    water_ripple_a.png  the fine waves of it, white on clear: what is left when the slow change of
    water_ripple_b.png  brightness is taken out. Two different cuts, laid over the water and sliding
                        at different speeds (ui/render/RiverScene.kt).

  river_bank.png (seen from straight above: land along the top, water along the bottom)
    river_bank.png         the bank cut out of its white and made to run on without a seam: one
                           copy to a side of a cell, its land edge CELL_OVERHANG outside the cell;
    river_bank_concave.png the bank bent round the inside of a cell whose river turns or ends;
    river_bank_convex.png  the bank bent round a corner of land poking into the river.
    Both bends are the straight bank laid out round a circle: along it the bank carries on from
    the straight pieces at both ends, and meets itself mirrored on the diagonal.

  res/drawable/pond.png: the water in the ring of the pond, moved to the same colour.

All go to res/drawable-nodpi (the pond stays in res/drawable). Requires Pillow and numpy.

    python tools/prepare_water_art.py [--preview out/]
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

TOOLS = Path(__file__).resolve().parent
SOURCE = TOOLS / "source_art"
RES = TOOLS.parent / "app" / "src" / "main" / "res"
OUT = RES / "drawable-nodpi"

#: Pixels to a cell in the bank's pictures.
CELL = 512
#: How far the land edge of the bank lies outside the cell, and how deep the bank is, in cells.
CELL_OVERHANG = 0.08
BANK_DEPTH = 0.28
#: The colour the water is brought to on average: that of the pond, a little greener and lighter.
WATER_COLOUR = (146.0, 178.0, 174.0)
WATER_SIZE = 512
RIPPLE_SIZE = 256


def tile(picture: np.ndarray, margin: int, axes: tuple[int, ...] = (0, 1)) -> np.ndarray:
    """[picture] made to repeat: each edge fades into the part just past the opposite edge."""
    out = picture.astype(float)
    for axis in axes:
        size = out.shape[axis]
        keep = size - margin
        head = np.take(out, range(margin), axis=axis)
        tail = np.take(out, range(keep, size), axis=axis)
        shape = [1] * out.ndim
        shape[axis] = margin
        ramp = np.linspace(0.0, 1.0, margin).reshape(shape)
        body = np.take(out, range(margin, keep), axis=axis)
        out = np.concatenate([tail * (1 - ramp) + head * ramp, body], axis=axis)
    return out


def water() -> dict[str, np.ndarray]:
    rgb = np.array(Image.open(SOURCE / "water_surface.png").convert("RGB")).astype(float)
    tiled = tile(rgb, 128)
    size = tiled.shape[0]
    # The slow change of brightness across the picture would show as a checker of copies. It is
    # taken over three copies each way, so that it wraps round too.
    wrapped = np.concatenate([np.concatenate([tiled] * 3, axis=0)] * 3, axis=1)
    blur = Image.fromarray(wrapped.astype(np.uint8)).filter(ImageFilter.GaussianBlur(size / 14))
    fine = tiled - np.array(blur).astype(float)[size:2 * size, size:2 * size]
    # The waves stay, a good deal softer: the water is what is looked at, not the photograph.
    surface = np.clip(np.array(WATER_COLOUR) + fine * 0.42, 0, 255).astype(np.uint8)

    def ripple(source: np.ndarray) -> np.ndarray:
        level = source.mean(axis=2)
        strength = np.clip((level - 6) / 46, 0, 1) ** 1.2
        rgba = np.dstack([np.full(level.shape + (3,), 255.0), strength * 255])
        return np.array(Image.fromarray(rgba.astype(np.uint8), "RGBA").resize((RIPPLE_SIZE, RIPPLE_SIZE), Image.LANCZOS))

    return {
        "water_surface": np.array(Image.fromarray(surface).resize((WATER_SIZE, WATER_SIZE), Image.LANCZOS)),
        "water_ripple_a": ripple(fine),
        "water_ripple_b": ripple(np.rot90(np.roll(fine, size // 3, axis=1))),
    }


def sample(picture: np.ndarray, xs: np.ndarray, ys: np.ndarray) -> np.ndarray:
    """Premultiplied [picture] at fractional points; wraps round along x, clear past top and bottom."""
    h, w = picture.shape[:2]
    x0 = np.floor(xs).astype(int)
    y0 = np.clip(np.floor(ys).astype(int), 0, h - 2)
    fx = (xs - x0)[..., None]
    fy = np.clip(ys - y0, 0, 1)[..., None]
    x1 = (x0 + 1) % w
    x0 = x0 % w
    out = (picture[y0, x0] * (1 - fx) * (1 - fy) + picture[y0, x1] * fx * (1 - fy)
           + picture[y0 + 1, x0] * (1 - fx) * fy + picture[y0 + 1, x1] * fx * fy)
    out[(ys < 0) | (ys > h - 1)] = 0
    return out


def unmultiply(pre: np.ndarray) -> np.ndarray:
    a = pre[..., 3:4]
    rgb = np.where(a > 0, pre[..., :3] * 255.0 / np.maximum(a, 1e-3), 0)
    return np.clip(np.concatenate([rgb, a], axis=2), 0, 255).round().astype(np.uint8)


def strip_white(rgba: np.ndarray) -> np.ndarray:
    """
    Takes what is left of the white background off the outline: the pale, half-mixed pixels within
    reach of the clear, a little at a time, so that a pale stone only loses its rim. Then the
    outline is pulled in a pixel, and the colours just inside it are taken from further in.
    """
    from prepare_building_art import dilate
    out = rgba.astype(np.uint8).copy()
    v = out[:, :, :3].astype(int)
    pale = (v.min(axis=2) > 150) & (v.max(axis=2) - v.min(axis=2) < 46)
    for _ in range(5):
        clear = out[:, :, 3] <= 40
        fringe = pale & ~clear & dilate(clear, 2)
        if not fringe.any():
            break
        out[fringe, 3] = 0
    solid = out[:, :, 3] > 200
    rim = dilate(~solid, 2) & solid
    out[rim, 3] = 150
    out[~solid & ~rim, 3] = 0
    return out


def bank_strip() -> np.ndarray:
    """The bank premultiplied, one cell long and running on into itself, [CELL] px to a cell."""
    from prepare_drawable_art import cut_out
    rgba = cut_out(np.array(Image.open(SOURCE / "river_bank.png").convert("RGB")), white_parts=1.0).astype(float)
    solid = rgba[:, :, 3] > 40
    rows = np.where(solid.sum(axis=1) > solid.shape[1] * 0.02)[0]
    rgba = strip_white(rgba[rows.min():rows.max() + 1]).astype(float)
    # Premultiplied, so that the white it was cut from does not come back at the edges.
    pre = np.concatenate([rgba[:, :, :3] * rgba[:, :, 3:4] / 255.0, rgba[:, :, 3:4]], axis=2)
    # The bank is narrower than the picture would make it at one copy to a cell, with its stones
    # at the size that goes with that: so it needs more length than the picture has, and goes on
    # mirrored, which joins it to itself.
    depth = BANK_DEPTH * CELL
    wanted = int(pre.shape[0] * CELL / depth)
    run = pre
    while run.shape[1] < wanted + 110:
        run = np.concatenate([run, run[:, ::-1]], axis=1)
    strip = tile(run[:, :wanted + 110], 110, axes=(1,))
    size = (CELL, int(depth))
    return np.array(Image.fromarray(unmultiply(strip), "RGBA").convert("RGBa").resize(size, Image.LANCZOS)).astype(float)


def bends(strip: np.ndarray) -> dict[str, np.ndarray]:
    over = CELL_OVERHANG * CELL
    half = CELL / 2.0

    # Round the middle of the cell. The picture covers the quarter from the overhang past the
    # corner of the cell to its middle.
    size = int(half + over)
    yy, xx = np.mgrid[0:size, 0:size].astype(float) + 0.5
    dx, dy = size - xx, size - yy
    radius = np.hypot(dx, dy)
    angle = np.arctan2(dy, dx)  # 0 along the west side, a quarter turn along the north
    # Out from the diagonal towards each end the bank runs back to the middle of its length,
    # half a cell along, where the straight pieces take over.
    along = half + (np.pi / 4 - np.abs(angle - np.pi / 4)) * half * 0.8
    across = half + over - radius
    concave = sample(strip, along, across)
    concave[across < 0] = 0

    # Round the corner of the land, at the top left of the picture.
    size = int((BANK_DEPTH - CELL_OVERHANG) * CELL) + 2
    yy, xx = np.mgrid[0:size, 0:size].astype(float) + 0.5
    radius = np.hypot(xx, yy)
    angle = np.arctan2(yy, xx)
    along = (np.pi / 4 - np.abs(angle - np.pi / 4)) * half * 0.5
    convex = sample(strip, along, radius + over)
    return {"river_bank_concave": unmultiply(concave), "river_bank_convex": unmultiply(convex)}


def river_icon(pictures: dict[str, np.ndarray]) -> np.ndarray:
    """A piece of the stream as the garden draws it: water between its two banks, on a slab of ground."""
    from prepare_building_art import flat_to_tile
    top = Image.fromarray(pictures["water_surface"]).convert("RGBA").resize((CELL, CELL))
    bank = Image.fromarray(pictures["river_bank"], "RGBA")
    land = int(CELL_OVERHANG * CELL)
    top.alpha_composite(bank.crop((0, land, CELL, bank.height)), (0, 0))
    top.alpha_composite(bank.crop((0, land, CELL, bank.height)).rotate(180), (0, CELL - (bank.height - land)))
    rgba, _ = flat_to_tile(top.convert("RGB"), side_depth=54)
    # The sides of the slab are earth, as on the other pieces of ground, not the colours of the
    # face's edge run down.
    h, w = rgba.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    face = np.abs(xx + 0.5 - w / 2) / (w / 2) + np.abs(yy + 0.5 - w / 4) / (w / 4) <= 1.0
    side = (rgba[:, :, 3] > 0) & ~face
    grain = np.random.default_rng(7).normal(0, 9, (h, w))
    shade = np.where(xx < w / 2, 0.8, 1.0)[..., None]
    earth = (np.array([132.0, 104.0, 74.0]) + grain[..., None]) * shade
    rgba[:, :, :3][side] = np.clip(earth, 0, 255).astype(np.uint8)[side]
    return rgba


def pond_icon() -> np.ndarray:
    """The pond as the garden draws it, with its water as it is now."""
    from prepare_building_art import crop
    return crop(np.array(Image.open(RES / "drawable" / "pond.png").convert("RGBA")))


def pond() -> np.ndarray:
    """The picture of the pond with its water moved to [WATER_COLOUR], and its grain kept."""
    from prepare_building_art import dilate, reconstruct
    rgba = np.array(Image.open(RES / "drawable" / "pond.png").convert("RGBA"))
    v = rgba[:, :, :3].astype(float)
    h, w = rgba.shape[:2]
    pale = (v.min(axis=2) > 118) & (v.max(axis=2) - v.min(axis=2) < 40) & (rgba[:, :, 3] > 200)
    seed = np.zeros_like(pale)
    seed[int(h * 0.42):int(h * 0.5), int(w * 0.45):int(w * 0.55)] = True
    area = dilate(reconstruct(seed, pale), 2) & (rgba[:, :, 3] > 200)
    soft = np.array(Image.fromarray((area * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2))).astype(float)[..., None] / 255
    shift = np.array(WATER_COLOUR) - v[area].mean(axis=0)
    out = rgba.copy()
    out[:, :, :3] = np.clip(v + shift * soft, 0, 255).astype(np.uint8)
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", type=Path)
    parser.add_argument("--pond", action="store_true", help="also recolour res/drawable/pond.png (once: it works on the file in place)")
    args = parser.parse_args()
    sys.path.insert(0, str(TOOLS))
    pictures = water()
    strip = bank_strip()
    pictures["river_bank"] = unmultiply(strip)
    pictures.update(bends(strip))
    for name, picture in pictures.items():
        Image.fromarray(picture).save(OUT / f"{name}.png", optimize=True)
        print(name, picture.shape)
    from prepare_drawable_art import to_icon
    icons = {"icon_tile_river": river_icon(pictures), "icon_deco_pond": pond_icon()}
    for name, picture in icons.items():
        Image.fromarray(to_icon(picture), "RGBA").save(OUT / f"{name}.png", optimize=True)
    if args.pond:
        Image.fromarray(pond()).save(RES / "drawable" / "pond.png", optimize=True)
    if args.preview:
        args.preview.mkdir(parents=True, exist_ok=True)
        for name, picture in pictures.items():
            image = Image.fromarray(picture).convert("RGBA")
            ground = Image.new("RGBA", image.size, (150, 120, 90, 255))
            ground.alpha_composite(image)
            ground.save(args.preview / f"{name}.png")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
