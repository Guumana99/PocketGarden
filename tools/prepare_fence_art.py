#!/usr/bin/env python3
"""Cut the three fences and the brick edging into the parts the garden joins up (#85).

Each original (tools/source_art/struct_fence_*.png, struct_edging_brick.png) is one section seen
from the front, on white. Out of it come, for each of them, with <id> its object id:

  <id>.png        the whole picture: one standing alone
  <id>_post.png   one post, with nothing of the rails left on it: what stands on a cell of a run
  <id>_rail.png   what goes between two posts, one cell long and still upright; the garden shears
                  it along the grid, so it lies at any of the slopes of a run

Everything is at the atlas' source scale, 720 px a tile. Across the screen a cell of a run is half
a tile, so a rail is 360 px between post centres, whatever is the picture's own width. The fences
are all [POST_HEIGHT] tall, the picture squeezed in height until they are. Their middles are
made up to that width by laying them side by side, never by drawing them out: stretched, the wood's
rails lost their grain and read as thin sticks (#86), and pickets would turn fat. The wood's copies
overlap and fade into one another, as its rails do not end where they could meet. Sheared along
the grid a rail keeps its height on the screen but is thinner across, so the wood's bars are made
that much taller ([SHEAR_THICKNESS]): across they are as thick as on the section standing alone.

Usage:
  python tools/prepare_fence_art.py                 # writes raw_assets/ and the overrides
  python tools/prepare_fence_art.py --preview out/  # also a picture of them all joined up

Requires Pillow and numpy.
"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from prepare_building_art import OVERRIDES, RAW, SOURCE, TILE, components, crop, dilate, reconstruct, resize, border
from prepare_drawable_art import ICONS, to_icon

#: A post's height, in source pixels. Between the two the old fence had: 167 joined, 247 alone.
POST_HEIGHT = 210

#: Across the screen, from the centre of a post to the next one's.
SPAN = TILE // 2

#: How far a rail goes in under the post at each of its ends, so that no gap shows between them.
RAIL_TUCK = 3

#: How much taller a bar lying along a cell must be to stay as thick: 1 / cos(atan(1/2)).
SHEAR_THICKNESS = 1.118

#: The edging's height: about a third of a post's.
BRICK_HEIGHT = 76

#: How much of a tooth's far side is the next brick's dark edge, in the original's pixels.
BRICK_SLIVER = 30

#: Bricks to a cell, and how much of the original's row a tooth of the saw is, in pixels.
BRICKS_PER_CELL = 7
BRICK_PITCH = 224

#: The stretch of the original's row that repeats: its left edge at the top and the bottom of the
#: picture, running down the gap between two bricks.
BRICK_SEAM_TOP = (470, 440)
BRICK_SEAM_BOTTOM = (272, 650)


@dataclass(frozen=True)
class Fence:
    source: str
    #: The post kept: the columns of its shaft, top and bottom row. [cap] is the columns and the
    #: last row of what is wider than the shaft at its top.
    post: tuple[int, int, int, int]
    cap: tuple[int, int, int] | None
    #: The columns of the middle, between the two posts.
    middle: tuple[int, int]
    #: How much wider than tall the picture is drawn: the originals are narrow for the cell.
    stout: float
    #: How many times the middle is laid side by side.
    repeats: int
    #: How many of the original's columns two copies fade across; 0 where they just meet.
    overlap: int = 0
    #: The rows of the bars that run across, top and bottom, made up for the shear; none for
    #: what has uprights in front of its rails.
    bars: tuple[tuple[int, int], ...] = ()


FENCES: dict[str, Fence] = {
    "struct_fence": Fence("struct_fence_wood.png", (223, 333, 121, 955), None, (338, 684), stout=1.3, repeats=3, overlap=60, bars=((226, 320), (601, 692))),
    "struct_fence_white": Fence("struct_fence_white.png", (226, 318, 169, 858), (205, 340, 224), (319, 701), stout=1.3, repeats=2),
    "struct_fence_bamboo": Fence("struct_fence_bamboo.png", (160, 236, 213, 815), None, (288, 781), stout=1.0, repeats=2),
}


def unmix(rgb: np.ndarray, alpha: np.ndarray) -> np.ndarray:
    """[rgb] as it was before it was mixed with the white behind it, with the [alpha] (0 to 1) given."""
    a = alpha[..., None]
    clean = (rgb.astype(float) - (1 - a) * 255.0) / np.maximum(a, 0.05)
    return np.clip(clean, 0, 255)


def cut_plain_white(name: str) -> np.ndarray:
    """
    [name] cut out of pure white and nothing else, for what is itself pale: the white fence's paint
    is light grey (about 205), its background 255, and the outline in between is taken back out of
    the mix. Gaps between its pickets go as well, wherever they are.
    """
    rgb = np.array(Image.open(SOURCE / name).convert("RGB"))
    darkest = rgb.astype(int).min(axis=2).astype(float)
    alpha = np.clip((250.0 - darkest) / 30.0, 0.0, 1.0)
    alpha[alpha < 0.08] = 0.0
    out = unmix(rgb, alpha)
    return np.dstack([out, alpha * 255.0]).round().astype(np.uint8)


def cut_shadowed(name: str) -> np.ndarray:
    """
    [name] cut out of a white floor with a soft grey shadow on it: all that is pale and has no
    colour goes, from the picture's edge and in the pockets the picture shuts in. Pale flecks
    inside the outline stay.
    """
    rgb = np.array(Image.open(SOURCE / name).convert("RGB"))
    v = rgb.astype(int)
    mx, mn = v.max(axis=2), v.min(axis=2)
    pale = (mn > 176) & ((mx - mn) < 22)
    background = reconstruct(border(pale.shape), pale)
    for part in components(pale & ~background):
        if part.sum() > 900:
            background |= part
    alpha = np.where(background, 0.0, 1.0)
    rim = dilate(background) & ~background
    alpha[rim] = np.clip((245.0 - mn[rim]) / 70.0, 0.25, 1.0)
    out = unmix(rgb, alpha)
    return np.dstack([out, alpha * 255.0]).round().astype(np.uint8)


def only(rgba: np.ndarray, keep: np.ndarray) -> np.ndarray:
    out = rgba.copy()
    out[..., 3] = (out[..., 3] * keep).astype(np.uint8)
    return out


def post_of(cut: np.ndarray, fence: Fence) -> np.ndarray:
    """The post alone: the shaft's columns, and the cap's over the rows it takes."""
    x0, x1, top, bottom = fence.post
    keep = np.zeros(cut.shape[:2], bool)
    keep[top - 2:bottom + 3, x0:x1] = True
    if fence.cap:
        cap_from, cap_to, cap_last = fence.cap
        keep[top - 2:cap_last, cap_from:cap_to] = True
    return only(cut, keep)


def lay_side_by_side(strip: np.ndarray, times: int, overlap: int) -> np.ndarray:
    """[times] copies of [strip] in a row, each fading into the next across [overlap] columns."""
    h, w = strip.shape[:2]
    step = w - overlap
    out = np.zeros((h, step * (times - 1) + w, 4))
    premultiplied = np.concatenate([strip[..., :3] * (strip[..., 3:4] / 255.0), strip[..., 3:4].astype(float)], axis=2)
    ramp = np.ones(w)
    if overlap:
        ramp[:overlap] = np.linspace(0.0, 1.0, overlap)
    for i in range(times):
        fade = np.ones(w) if i == 0 else ramp
        out[:, i * step:i * step + w] = out[:, i * step:i * step + w] * (1 - fade[None, :, None]) + premultiplied * fade[None, :, None]
    alpha = out[..., 3:4]
    rgb = np.where(alpha > 0, out[..., :3] * 255.0 / np.maximum(alpha, 1e-3), 0)
    return np.clip(np.concatenate([rgb, alpha], axis=2), 0, 255).round().astype(np.uint8)


def thickened(strip: np.ndarray, rows: tuple[int, int], top: int) -> np.ndarray:
    """[strip], whose first row is the picture's row [top], with the bar in [rows] taller about its middle."""
    first, last = rows[0] - top, rows[1] - top
    bar = strip[first:last]
    tall = int(round((last - first) * SHEAR_THICKNESS))
    grown = np.array(Image.fromarray(bar, "RGBA").convert("RGBa").resize((bar.shape[1], tall), Image.BICUBIC).convert("RGBA"))
    out = strip.copy()
    out[first:last] = 0
    at = (first + last - tall) // 2
    out[at:at + tall] = grown
    return out


def rail_of(cut: np.ndarray, fence: Fence, post_width: float, sx: float, sy: float) -> tuple[np.ndarray, float]:
    """
    What goes between two posts, [SPAN] less a post wide and tucked under them at its ends.
    @return it, and how far below its bottom edge the ground is, in source pixels.
    """
    x0, x1 = fence.middle
    _, _, top, bottom = fence.post
    strip = cut[top:bottom + 1, x0:x1].copy()
    if fence.cap:  # the cap reaches out over the middle
        strip[:fence.cap[2] + 4 - top, :, 3] = 0
    wide = SPAN - post_width + 2 * RAIL_TUCK
    tall = int(round((bottom + 1 - top) * sy))
    for rows in fence.bars:
        strip = thickened(strip, rows, top)
    whole = lay_side_by_side(strip, fence.repeats, fence.overlap)
    rail = np.array(Image.fromarray(whole, "RGBA").convert("RGBa").resize((int(round(wide)), tall), Image.LANCZOS).convert("RGBA"))
    ys = np.where(rail[..., 3].max(axis=1) > 8)[0]
    return rail, float(tall - 1 - ys.max())


def seam_distance(shape: tuple[int, ...]) -> np.ndarray:
    """How far along the row each pixel of the brick picture is, measured from a seam on the slant."""
    (tx, ty), (bx, by) = BRICK_SEAM_TOP, BRICK_SEAM_BOTTOM
    lean = (tx - bx) / (ty - by)  # columns the seam moves for each row down
    yy, xx = np.mgrid[0:shape[0], 0:shape[1]].astype(float)
    return xx - tx - (yy - ty) * lean


def brick_row(cut: np.ndarray) -> np.ndarray:
    """
    One cell of the saw of bricks: [BRICKS_PER_CELL] teeth, each cut on the slant along the gaps.
    Both of its ends slant alike, so that rows set a cell apart close up with no gap.
    """
    along = seam_distance(cut.shape)
    box = only(cut, (along >= 0) & (along < BRICK_PITCH + 2))
    ys, xs = np.where(box[..., 3] > 8)
    tooth = crop(box)
    h, w = tooth.shape[:2]
    # Past the last tooth's far side is a sliver of the brick after it, which nothing lies over.
    last = only(cut, (along >= 0) & (along < BRICK_PITCH - BRICK_SLIVER))[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    row = np.zeros((h, w + BRICK_PITCH * (BRICKS_PER_CELL - 1), 4), np.uint8)
    # The tooth on the right lies over the one on its left, as in the picture.
    for i in range(BRICKS_PER_CELL):
        over = row[:, i * BRICK_PITCH:i * BRICK_PITCH + w]
        laid = last if i == BRICKS_PER_CELL - 1 else tooth
        a = laid[..., 3:4] / 255.0
        over[..., :3] = (laid[..., :3] * a + over[..., :3] * (1 - a)).astype(np.uint8)
        over[..., 3] = np.maximum(over[..., 3], laid[..., 3])
    return row


def prepare_fence(asset_id: str, fence: Fence) -> dict[str, tuple[np.ndarray, dict]]:
    cut = cut_plain_white(fence.source) if asset_id == "struct_fence_white" else cut_shadowed(fence.source)
    _, _, top, bottom = fence.post
    sy = POST_HEIGHT / (bottom + 1 - top)
    sx = sy * fence.stout
    single = crop(resize(crop(cut), sx, sy))
    post = crop(resize(crop(post_of(cut, fence)), sx, sy))
    rail, gap = rail_of(cut, fence, (fence.post[1] - fence.post[0]) * sx, sx, sy)
    rail = crop(rail)
    h = rail.shape[0]
    return {
        asset_id: (single, {"anchorX": 0.5, "anchorY": 0.97}),
        f"{asset_id}_post": (post, {"anchorX": 0.5, "anchorY": 0.97}),
        f"{asset_id}_rail": (rail, {"anchorX": 0.5, "anchorY": 1.0, "attachX": 0.5, "attachY": round(1.0 + gap / h, 4)}),
    }


def prepare_edging() -> dict[str, tuple[np.ndarray, dict]]:
    cut = cut_shadowed("struct_edging_brick.png")
    single = crop(cut)
    sy = BRICK_HEIGHT / single.shape[0]
    sx = SPAN / (BRICK_PITCH * BRICKS_PER_CELL)
    return {
        "struct_edging_brick": (crop(resize(single, sx * 1.5, sy)), {"anchorX": 0.5, "anchorY": 0.8}),
        "struct_edging_brick_rail": (crop(resize(brick_row(cut), sx, sy)), {"anchorX": 0.5, "anchorY": 0.8, "attachX": 0.5, "attachY": 0.8}),
    }


def prepare_all() -> dict[str, tuple[np.ndarray, dict]]:
    out: dict[str, tuple[np.ndarray, dict]] = {}
    for asset_id, fence in FENCES.items():
        out.update(prepare_fence(asset_id, fence))
    out.update(prepare_edging())
    return out


def write(prepared: dict[str, tuple[np.ndarray, dict]]) -> None:
    overrides = json.loads(OVERRIDES.read_text(encoding="utf-8"))
    for asset_id, (rgba, override) in prepared.items():
        Image.fromarray(rgba, "RGBA").save(RAW / f"{asset_id}.png", optimize=True)
        overrides[asset_id] = override
        print(f"{asset_id:30s} {rgba.shape[1]}x{rgba.shape[0]}  {override}")
    for asset_id in (*FENCES, "struct_edging_brick"):
        Image.fromarray(to_icon(prepared[asset_id][0]), "RGBA").save(ICONS / f"icon_{asset_id}.png", optimize=True)
    lines = [f'  "{k}": {json.dumps(v)}' for k, v in overrides.items()]
    OVERRIDES.write_text("{\n" + ",\n".join(lines) + "\n}\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", type=Path, help="also write the sprites, on a dark and on a light ground, here")
    args = parser.parse_args()
    prepared = prepare_all()
    write(prepared)
    if args.preview:
        args.preview.mkdir(parents=True, exist_ok=True)
        for asset_id, (rgba, _) in prepared.items():
            sheet = Image.new("RGBA", (rgba.shape[1] * 2 + 20, rgba.shape[0] + 10), (70, 52, 38, 255))
            ImageDraw.Draw(sheet).rectangle((rgba.shape[1] + 10, 0, sheet.width, sheet.height), fill=(198, 214, 150, 255))
            sprite = Image.fromarray(rgba, "RGBA")
            sheet.alpha_composite(sprite, (5, 5))
            sheet.alpha_composite(sprite, (rgba.shape[1] + 15, 5))
            sheet.convert("RGB").save(args.preview / f"{asset_id}.png")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
