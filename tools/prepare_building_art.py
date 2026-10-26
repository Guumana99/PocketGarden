#!/usr/bin/env python3
"""Clean up the ground tiles and building pictures received in #40 and write them to raw_assets/.

The originals are in tools/source_art/ and are never changed. For each one this script:

  * gives it a real alpha channel where it has none. The two windmill pictures came as JPEGs
    with the grey-and-white "transparency" checkerboard painted into the pixels; the board is
    keyed out by its colour (flat grey of the board's two tones) and by being reachable from
    the edge of the picture, or lying in a pocket of board inside the picture;
  * strips the pale halo that background removal left round the ground tiles and the blades
    (white wisps along the edges, a few bluish specks) and drops stray specks that are not
    part of the picture (a blue dot in a corner of the soil tile);
  * brings it to the atlas' source scale, 720 px per tile, where that makes it smaller:
    the ground tiles and the greenhouse are resized so that their footprint is exactly one
    tile wide; the tiles are also stretched upright so that their top face is a 2:1 diamond,
    the shape of a tile in the game;
  * works out its anchor (the point that stands on the ground) and, for the buildings that
    would have to be enlarged to cover their footprint, the scale they are drawn at, and
    writes both into atlas_overrides.json for asset_packer.py.

The hut, the well and the five decorations of #74 (PROPS) came on white, 1024 x 1024, like the
crops: they are cut out of it first (prepare_drawable_art.cut_out), taking out only the plain
white, as the hut's plinth is a pale cream that the usual cut bit into.

Usage:
  python tools/prepare_building_art.py                 # writes raw_assets/ and the overrides
  python tools/prepare_building_art.py --preview out/  # also writes preview pictures

Requires Pillow and numpy.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image

TOOLS = Path(__file__).resolve().parent
SOURCE = TOOLS / "source_art"
RAW = TOOLS / "raw_assets"
OVERRIDES = TOOLS / "atlas_overrides.json"

#: Source pixels per tile, the scale asset_packer.py is run with (--source-tile-width 720).
TILE = 720

ALPHA_MIN = 8


# --- Masks -------------------------------------------------------------------------------------

def dilate(mask: np.ndarray, times: int = 1) -> np.ndarray:
    out = mask.copy()
    for _ in range(times):
        grown = out.copy()
        grown[1:, :] |= out[:-1, :]
        grown[:-1, :] |= out[1:, :]
        grown[:, 1:] |= out[:, :-1]
        grown[:, :-1] |= out[:, 1:]
        out = grown
    return out


def reconstruct(seed: np.ndarray, mask: np.ndarray) -> np.ndarray:
    """Everything in [mask] connected to [seed] (4-connected)."""
    region = seed & mask
    while True:
        grown = dilate(region) & mask
        if (grown == region).all():
            return region
        region = grown


def components(mask: np.ndarray) -> list[np.ndarray]:
    """The connected parts of [mask], largest first."""
    left = mask.copy()
    parts = []
    while left.any():
        y, x = np.argwhere(left)[0]
        seed = np.zeros_like(left)
        seed[y, x] = True
        part = reconstruct(seed, left)
        parts.append(part)
        left &= ~part
    return sorted(parts, key=lambda p: -p.sum())


def border(shape) -> np.ndarray:
    b = np.zeros(shape, bool)
    b[0, :] = b[-1, :] = b[:, 0] = b[:, -1] = True
    return b


# --- Cleaning ----------------------------------------------------------------------------------

def key_out_checkerboard(
    rgb: np.ndarray,
    tones: list[int],
    tolerance: int,
    pocket_min: int,
    square: int | None = None,
    offset: int = 0,
) -> np.ndarray:
    """
    Alpha for a picture whose transparency was painted in as a checkerboard of grey [tones].

    With [square] (the board's square size in pixels, its grid starting at [offset]) a pixel only
    counts as board if it has the tone its own square should have, so that grey wood which
    happens to match one of the two tones is kept.
    """
    v = rgb.astype(int)
    grey = (v.max(axis=2) - v.min(axis=2)) <= 10
    level = v.mean(axis=2)
    if square:
        # Each square of the board is one flat grey, but the two greys drift a little from one
        # part of the picture to another; so every square is matched against its own grey.
        h, w = level.shape
        board = np.zeros((h, w), bool)
        for top in range(offset - square, h, square):
            for left in range(offset - square, w, square):
                y0, y1, x0, x1 = max(0, top), min(h, top + square), max(0, left), min(w, left + square)
                if y1 <= y0 or x1 <= x0:
                    continue
                cell_grey = grey[y0:y1, x0:x1]
                if cell_grey.mean() < 0.05:
                    continue
                tone = np.median(level[y0:y1, x0:x1][cell_grey])
                if not min(tones) - 20 <= tone <= max(tones) + 20:
                    continue
                board[y0:y1, x0:x1] = cell_grey & (np.abs(level[y0:y1, x0:x1] - tone) <= tolerance)
    else:
        board = grey & np.any([np.abs(level - t) <= tolerance for t in tones], axis=0)
    background = reconstruct(border(board.shape), board)
    # Pockets of board enclosed by the picture (between the spokes, under the balcony).
    for part in components(board & ~background):
        if part.sum() >= pocket_min:
            background |= part
    # What is left of the board where a square was mostly picture: flat grey of one of the
    # board's tones, touching the background already found.
    flat = ((v.max(axis=2) - v.min(axis=2)) <= 6) & np.any([np.abs(level - t) <= 10 for t in tones], axis=0)
    background = reconstruct(background, background | flat)
    # And board seen through gaps in the picture, which no square of its own could tell.
    for part in components(flat & ~background):
        if part.sum() >= 300:
            background |= part
    alpha = np.where(background, 0, 255).astype(np.uint8)
    # Soften the cut by one pixel, so that the edge is not jagged.
    rim = dilate(background) & ~background
    alpha[rim] = 160
    return alpha


def strip_halo(rgba: np.ndarray, depth: int) -> np.ndarray:
    """Clears the pale wisps along the outline: bright, washed-out pixels near the transparent area."""
    out = rgba.copy()
    v = out[:, :, :3].astype(int)
    pale = (v.mean(axis=2) > 150) & ((v.max(axis=2) - v.min(axis=2)) < 60)
    for _ in range(depth):
        clear = out[:, :, 3] <= ALPHA_MIN
        halo = pale & (out[:, :, 3] > ALPHA_MIN) & dilate(clear, 2)
        if not halo.any():
            break
        out[halo, 3] = 0
    return out


def keep_main_part(rgba: np.ndarray) -> np.ndarray:
    """Drops specks that are not joined to the picture itself."""
    out = rgba.copy()
    opaque = out[:, :, 3] > ALPHA_MIN
    parts = components(opaque)
    if not parts:
        return out
    keep = parts[0]
    # Parts nearly as big as the largest are part of the picture too.
    for part in parts[1:]:
        if part.sum() > 0.02 * parts[0].sum():
            keep |= part
    out[opaque & ~keep, 3] = 0
    return out


def crop(rgba: np.ndarray) -> np.ndarray:
    ys, xs = np.where(rgba[:, :, 3] > ALPHA_MIN)
    return rgba[ys.min():ys.max() + 1, xs.min():xs.max() + 1]


def resize(rgba: np.ndarray, sx: float, sy: float) -> np.ndarray:
    h, w = rgba.shape[:2]
    size = (max(1, round(w * sx)), max(1, round(h * sy)))
    # Premultiplied, so that transparent pixels do not bleed their colour into the edge.
    image = Image.fromarray(rgba, "RGBA").convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")
    return np.array(image)


# --- Measuring ---------------------------------------------------------------------------------

def base_of(rgba: np.ndarray, band: float = 0.35) -> tuple[float, float, int]:
    """
    The footprint at the bottom of a picture standing on the ground: (centre x, centre y, width).

    The base (a plinth of stone, say) is seen as a diamond. Its left and right corners are the
    furthest points out at the bottom of the picture, at the foot of the plinth's side edges; the
    footprint's centre is halfway between them. (The pictures are not drawn at quite the game's
    2:1 angle, so the front corner is not used here; see [ground_centre_y].)
    """
    opaque = rgba[:, :, 3] > ALPHA_MIN
    h, w = opaque.shape
    low = opaque.copy()
    low[: int(h * (1 - band))] = False
    ys, xs = np.where(low)
    left_x, right_x = xs.min(), xs.max()
    near = max(2, int((right_x - left_x) * 0.02))
    left_y = ys[xs <= left_x + near].max()
    right_y = ys[xs >= right_x - near].max()
    return (left_x + right_x) / 2, (left_y + right_y) / 2, int(right_x - left_x)


def ground_centre_y(rgba: np.ndarray) -> float:
    """
    The height in the picture to stand on the footprint's centre, fitted to all three ground
    corners of the base that can be seen: left, right and front.

    The buildings are drawn flatter than the game's 2:1 diamond, so no single height puts every
    corner on the footprint. Standing the side corners on it leaves the front corner short of
    the footprint's front, and the building looks set back and floating; standing the front
    corner on it lets the sides sink below the footprint. Halfway between the two keeps both
    misses to half as much.
    """
    _, side_y, width = base_of(rgba)
    ys, _ = np.where(rgba[:, :, 3] > ALPHA_MIN)
    front_y = ys.max()
    # On a 2:1 diamond the front corner is a quarter of its width below the centre.
    return (side_y + (front_y - width / 4)) / 2


def top_face_of(rgba: np.ndarray) -> tuple[float, float, float, float]:
    """
    A ground slab's top face: (centre x, centre y, width, height), from its left, right and top
    corners. A corner is the highest point near that side, so that crumbs lying lower down
    beside the slab are not taken for it.
    """
    opaque = rgba[:, :, 3] > ALPHA_MIN
    ys, xs = np.where(opaque)
    left_x, right_x = xs.min(), xs.max()
    near = max(2, int((right_x - left_x) * 0.02))
    left_y = ys[xs <= left_x + near].min()
    right_y = ys[xs >= right_x - near].min()
    top_y = ys.min()
    cx, cy = (left_x + right_x) / 2, (left_y + right_y) / 2
    return cx, cy, right_x - left_x, 2 * (cy - top_y)


# --- The pictures ------------------------------------------------------------------------------

def load(name: str) -> np.ndarray:
    return np.array(Image.open(SOURCE / name).convert("RGBA"))


def flatten_light(rgba: np.ndarray, sigma_share: float = 0.035) -> np.ndarray:
    """
    Evens out the lighting baked into a ground tile's top face (#47).

    The lawn photo is lit from one side: bright at its back corner, dark at its front. Laid cell
    by cell, that slope repeats in every cell and the lawn reads as a chequer of separate tiles.
    Here the slow change of brightness across the top face is divided out (the face's own blur,
    taken over the face only, against its mean), so that only the blades of grass are left and
    neighbouring cells meet without a step. The slab's side faces, below the top face, are kept.
    """
    from PIL import ImageFilter
    out = rgba.copy()
    h, w = rgba.shape[:2]
    cx, cy, width, height = top_face_of(rgba)
    yy, xx = np.mgrid[0:h, 0:w]
    face = (np.abs(xx - cx) / (width / 2) + np.abs(yy - cy) / (height / 2)) <= 1.0
    face &= rgba[:, :, 3] > ALPHA_MIN
    rgb = rgba[:, :, :3].astype(float)
    lum = rgb.mean(axis=2)
    radius = max(2, int(width * sigma_share))

    def blur(values: np.ndarray) -> np.ndarray:
        image = Image.fromarray(np.clip(values, 0, 255).astype(np.uint8))
        return np.array(image.filter(ImageFilter.GaussianBlur(radius))).astype(float)

    # Normalised blur, colour by colour: what the face is like round each point, from the face's
    # own pixels only, so that the light rim along its edges is evened out as well.
    weight = blur(face * 255.0) / 255.0
    for channel in range(3):
        values = rgb[:, :, channel]
        local = blur(np.where(face, values, 0)) / np.maximum(weight, 1e-3)
        gain = np.where(face, values[face].mean() / np.maximum(local, 1.0), 1.0)
        out[:, :, channel] = np.clip(values * gain, 0, 255).astype(np.uint8)
    del lum
    # The two back edges of the face fade out, so that where the cell behind shows through
    # them (the game draws each cell a little larger than its diamond) there is no line.
    u = (xx - cx) / (width / 2)
    v = (yy - cy) / (height / 2)
    back = np.maximum(-u - v, u - v)  # 1 on the back edges, falling towards the front
    feather = 0.03
    ramp = np.clip((1.0 - back) / feather, 0.0, 1.0)
    on_face = face & (v < 0)
    out[:, :, 3] = np.where(on_face, (out[:, :, 3] * ramp).astype(np.uint8), out[:, :, 3])
    return out


def prepare_tile(name: str, halo: bool, flatten: bool = False) -> tuple[np.ndarray, dict]:
    rgba = load(name)
    if halo:
        rgba = strip_halo(rgba, depth=6)
    rgba = crop(keep_main_part(rgba))
    _, _, width, height = top_face_of(rgba)
    # One tile wide, and stood up into the game's 2:1 diamond.
    sx = TILE / width
    sy = (TILE / 2) / height
    rgba = crop(resize(rgba, sx, sy))
    if flatten:
        rgba = flatten_light(rgba)
    cx, cy, _, _ = top_face_of(rgba)
    h, w = rgba.shape[:2]
    return rgba, {"anchorX": round(float(cx / w), 4), "anchorY": round(float(cy / h), 4), "isFlippedAllowed": False}


def flat_to_tile(
    name: "str | Image.Image", side_depth: int, flatten: bool = False, share: float = 1.0, soften: float = 0.0,
) -> tuple[np.ndarray, dict]:
    """
    Turns a flat, seen-from-above texture (#53: tile_lawn_flat.png, tile_soil_flat.png, opaque
    rectangles with no diamond of their own) into a ground tile like the others: the middle square
    of the texture laid onto the game's 2:1 diamond, one tile (720 px) wide, standing on a slab
    [side_depth] px deep whose two side faces carry on the colours of the face's front edges,
    darkened. Drawn at twice the size and scaled down, so that the edges are smooth.

    [share] is how much of the texture's shorter side one tile shows: less makes its detail
    larger. [soften] blurs the texture by that many of its pixels first. A tile is drawn about
    seven times smaller on a phone, and fine, sharp detail (single blades of grass) then glitters
    as grain; larger, softer detail reads as a lawn.
    """
    source = name if isinstance(name, Image.Image) else Image.open(SOURCE / name).convert("RGB")
    if soften > 0:
        from PIL import ImageFilter
        source = source.filter(ImageFilter.GaussianBlur(soften))
    side = int(min(source.size) * share)
    left = (source.width - side) // 2
    top = (source.height - side) // 2
    texture = source.crop((left, top, left + side, top + side))
    k = 2
    w, h, depth = TILE * k, TILE // 2 * k, side_depth * k
    # Output pixel (x, y) on the face <- texture pixel (tx, ty): the texture's corners go to the
    # diamond's top, right, bottom and left corners in turn.
    s = float(side)
    face = texture.transform(
        (w, h), Image.AFFINE, (s / w, s / h, -s / 2, -s / w, s / h, s / 2), resample=Image.BICUBIC,
    )
    face = np.array(face).astype(float)
    yy, xx = np.mgrid[0:h + depth, 0:w]
    u = (xx + 0.5 - w / 2) / (w / 2)
    v = (yy + 0.5 - h / 2) / (h / 2)
    on_face = (np.abs(u) + np.abs(v)) <= 1.0
    out = np.zeros((h + depth, w, 4), dtype=float)
    out[:h][on_face[:h]] = np.concatenate([face, np.full((h, w, 1), 255.0)], axis=2)[on_face[:h]]
    # The slab: below each point of the two front edges, that edge's colour, darker the further
    # down and darker on the left face (away from the light) than on the right.
    edge = np.where(xx[0] < w / 2, h / 2 + xx[0] * h / w, h - (xx[0] - w / 2) * h / w)
    for x in range(w):
        ey = edge[x]
        sample = face[min(h - 1, max(0, int(ey) - 2)), x]
        shade = 0.55 if x < w / 2 else 0.72
        for y in range(int(np.ceil(ey)), min(h + depth, int(ey + depth) + 1)):
            if on_face[y, x] if y < h else False:
                continue
            t = (y - ey) / depth
            out[y, x, :3] = sample * shade * (1.0 - 0.25 * t)
            out[y, x, 3] = 255.0
    rgba = np.array(Image.fromarray(out.astype(np.uint8), "RGBA").resize((w // k, (h + depth) // k), Image.LANCZOS))
    if flatten:
        rgba = flatten_light(rgba)
    # The face is an exact diamond here, so its middle is known rather than looked for
    # (top_face_of reads the corners from a band of pixels, which suits the ragged photos).
    rh, rw = rgba.shape[:2]
    return rgba, {"anchorX": round(TILE / 2 / rw, 4), "anchorY": round(TILE / 4 / rh, 4), "isFlippedAllowed": False}


def prepare_building(rgba: np.ndarray, tiles: int, centred: bool = False) -> tuple[np.ndarray, dict]:
    """
    Fits a building to its footprint. By default it stands where [ground_centre_y] puts it. With
    [centred] its base is centred on the footprint instead: the base's left and right corners
    stand on the footprint's, and its front falls a little short of the footprint's front corner.
    Nothing then spills past the footprint (a wide, flat base made the cottage overhang its lot,
    and the garden's frame when it stood at the edge).
    """
    rgba = crop(keep_main_part(rgba))
    _, _, width = base_of(rgba)
    wanted = tiles * TILE
    scale = 1.0
    if width > wanted:
        rgba = crop(resize(rgba, wanted / width, wanted / width))
    else:
        # Enlarging would only make the atlas bigger without adding detail: drawn larger instead.
        scale = wanted / width
    cx, side_y, _ = base_of(rgba)
    cy = side_y if centred else ground_centre_y(rgba)
    h, w = rgba.shape[:2]
    override = {"anchorX": round(float(cx / w), 4), "anchorY": round(float(cy / h), 4)}
    if scale != 1.0:
        override["scale"] = round(float(scale), 4)
    return rgba, override


#: The pictures of #74, each (source, how it is sized):
#:   ("tiles", n)   a building over its footprint, n tiles across ([prepare_building]);
#:   ("w", share)   a decoration [share] of a tile wide; ("h", share) as tall: about the size it
#:                  was drawn in code, within its cell.
#: [band] is how much of the foot of the picture its base is looked for in ([base_of]): the well's
#: bucket hangs down to near the foot of the stones, so only the lowest part is taken there; the
#: bench's back legs end well above its front ones.
PROPS: dict[str, tuple[str, tuple, float]] = {
    "struct_hut": ("building_hut.png", ("tiles", 2), 0.35),
    "struct_well": ("building_well.png", ("w", 0.86), 0.12),
    "deco_rock": ("deco_rock.png", ("w", 0.46), 0.35),
    "deco_bench": ("deco_bench.png", ("w", 0.56), 0.45),
    "deco_lantern": ("deco_lantern.png", ("h", 0.44), 0.2),
    "deco_sprinkler": ("deco_sprinkler.png", ("h", 0.34), 0.2),
    "deco_starter_crate": ("deco_starter_crate.png", ("w", 0.42), 0.2),
}


def cut_from_white(name: str) -> np.ndarray:
    """[name] (on white) cut out of its white: only the plain white goes (#74)."""
    from prepare_drawable_art import cut_out
    return cut_out(np.array(Image.open(SOURCE / name).convert("RGB")), white_parts=1.0)


def prepare_prop(asset_id: str) -> tuple[np.ndarray, dict]:
    name, (kind, size), band = PROPS[asset_id]
    rgba = crop(keep_main_part(cut_from_white(name)))
    if kind == "tiles":
        return prepare_building(rgba, tiles=size)
    h, w = rgba.shape[:2]
    scale = size * TILE / (w if kind == "w" else h)
    rgba = crop(resize(rgba, scale, scale))
    # It stands on the middle of its base, as a building does.
    cx, side_y, width = base_of(rgba, band)
    ys, _ = np.where(rgba[:, :, 3] > ALPHA_MIN)
    cy = (side_y + (ys.max() - width / 4)) / 2
    h, w = rgba.shape[:2]
    override = {"anchorX": round(float(cx / w), 4), "anchorY": round(float(cy / h), 4)}
    if asset_id == "deco_sprinkler":
        nx, ny = nozzle_of(rgba, cx, cy)
        override["attachX"], override["attachY"] = round(nx / w, 4), round(ny / h, 4)
    return rgba, override


def nozzle_of(rgba: np.ndarray, cx: float, cy: float) -> tuple[float, float]:
    """
    Where the sprinkler's water comes out: the hub its four arms turn on, at the top of its post.
    The post is followed up from the middle of its round foot ([cx], [cy]) along its own line,
    while it is thin; the hub is where it spreads out into the arms.
    """
    opaque = rgba[:, :, 3] > 128
    h, w = opaque.shape
    x = int(round(cx))

    def run_width(y: int) -> int:
        if not opaque[y, x]:
            return 0
        left = right = x
        while left > 0 and opaque[y, left - 1]:
            left -= 1
        while right < w - 1 and opaque[y, right + 1]:
            right += 1
        return right - left + 1

    y = int(cy)
    while y > 0 and run_width(y) > w * 0.12:  # the foot
        y -= 1
    while y > 0 and 0 < run_width(y) <= w * 0.2:  # the post
        y -= 1
    return float(cx), float(y)


def prepare_windmill() -> dict[str, tuple[np.ndarray, dict]]:
    body_rgb = np.array(Image.open(SOURCE / "building_windmill_body.jpg").convert("RGB"))
    body = np.dstack([body_rgb, key_out_checkerboard(body_rgb, tones=[76, 92], tolerance=7, pocket_min=600, square=48, offset=24)])
    blades_rgb = np.array(Image.open(SOURCE / "building_windmill_blades.jpg").convert("RGB"))
    blades = np.dstack([blades_rgb, key_out_checkerboard(blades_rgb, tones=[197, 255], tolerance=14, pocket_min=40)])
    blades = strip_halo(blades, depth=3)

    # Where the blades hang on the body: the hub on the gable, in the original picture.
    hub = np.zeros(body.shape[:2], bool)
    hub[252, 855] = True
    body = keep_main_part(body)
    ys, xs = np.where(body[:, :, 3] > ALPHA_MIN)
    hub_x, hub_y = 855 - xs.min(), 252 - ys.min()
    body = crop(body)
    body_rgba, body_override = prepare_building(body, tiles=2)
    h, w = body_rgba.shape[:2]
    body_override["attachX"] = round(float(hub_x / w), 4)
    body_override["attachY"] = round(float(hub_y / h), 4)

    # The sails reach about as far across as the mill is wide, and turn round their hub.
    blades = crop(keep_main_part(blades))
    _, _, body_width = base_of(body_rgba)
    span = blades.shape[1]
    blades = crop(resize(blades, body_width / span, body_width / span))
    hub_x, hub_y = hub_of(blades)
    bh, bw = blades.shape[:2]
    blades_override = {"anchorX": round(hub_x / bw, 4), "anchorY": round(hub_y / bh, 4), "scale": body_override.get("scale", 1.0)}
    return {
        "struct_windmill": (body_rgba, body_override),
        "struct_windmill_blades": (blades, blades_override),
    }


def hub_of(blades: np.ndarray) -> tuple[float, float]:
    """The hub the four sails turn round: the middle of the dark wooden wheel at their centre."""
    v = blades[:, :, :3].astype(int)
    dark_wood = (blades[:, :, 3] > 200) & (v.mean(axis=2) < 110)
    h, w = dark_wood.shape
    # Only near the middle: the sails' own dark edges are further out.
    cy, cx = h / 2, w / 2
    yy, xx = np.mgrid[0:h, 0:w]
    near = (np.abs(yy - cy) < h * 0.2) & (np.abs(xx - cx) < w * 0.2)
    ys, xs = np.where(dark_wood & near)
    return float(xs.mean()), float(ys.mean())


def prepare_all() -> dict[str, tuple[np.ndarray, dict]]:
    out: dict[str, tuple[np.ndarray, dict]] = {}
    # Since #53 the lawn and the tilled soil come from flat textures; the #40 photos
    # (tile_grass_default.png, tile_dirt_plowed.png) are kept in source_art but no longer used.
    # Since #57 the lawn is the top face of tile_grass_iso.png, seen from straight above.
    from prepare_drawable_art import flat_lawn
    grass, grass_override = flat_to_tile(flat_lawn(), side_depth=55, flatten=True, share=0.5, soften=1.2)
    # Its top face is a symmetric diamond, so the game mirrors cells at random to break the repeat.
    grass_override["isFlippedAllowed"] = True
    out["tile_grass"] = (grass, grass_override)
    # The lawn is a checkerboard of two shades; the second is the same grass a little darker.
    dark = grass.copy()
    dark[:, :, :3] = (dark[:, :, :3].astype(float) * 0.9).astype(np.uint8)
    out["tile_grass_dark"] = (dark, dict(grass_override))
    out["floor_tilled"] = flat_to_tile("tile_soil_flat.png", side_depth=33)
    # Stood like the others, halfway between its side and front corners (#55): centred on its
    # side corners alone, its flat base left the front row of its lot bare and it looked set back.
    out["struct_cottage"] = prepare_building(load("building_cottage.png"), tiles=3)
    out["floor_greenhouse"] = prepare_building(load("building_greenhouse.png"), tiles=1)
    out.update(prepare_windmill())
    for asset_id in PROPS:
        out[asset_id] = prepare_prop(asset_id)
    return out


def write(prepared: dict[str, tuple[np.ndarray, dict]]) -> None:
    overrides = json.loads(OVERRIDES.read_text(encoding="utf-8"))
    for asset_id, (rgba, override) in prepared.items():
        Image.fromarray(rgba, "RGBA").save(RAW / f"{asset_id}.png", optimize=True)
        overrides[asset_id] = override
        print(f"{asset_id:24s} {rgba.shape[1]}x{rgba.shape[0]}  {override}")
    lines = [f'  "{k}": {json.dumps(v)}' for k, v in overrides.items()]
    OVERRIDES.write_text("{\n" + ",\n".join(lines) + "\n}\n", encoding="utf-8")


def preview(prepared: dict[str, tuple[np.ndarray, dict]], folder: Path) -> None:
    """Pictures for a person to look at: each sprite on a checkerboard, and a small lawn."""
    folder.mkdir(parents=True, exist_ok=True)
    for asset_id, (rgba, override) in prepared.items():
        h, w = rgba.shape[:2]
        board = np.indices((h, w)).sum(axis=0) // 16 % 2
        bg = np.where(board[..., None] == 0, [235, 80, 200], [60, 200, 230]).astype(float)
        a = rgba[:, :, 3:4] / 255.0
        img = (rgba[:, :, :3] * a + bg * (1 - a)).astype(np.uint8)
        ax, ay = int(override["anchorX"] * w), int(override["anchorY"] * h)
        img[max(0, ay - 3):ay + 4, max(0, ax - 3):ax + 4] = [255, 0, 0]
        if "attachX" in override:
            px, py = int(override["attachX"] * w), int(override["attachY"] * h)
            img[max(0, py - 3):py + 4, max(0, px - 3):px + 4] = [0, 0, 255]
        Image.fromarray(img).save(folder / f"{asset_id}.png")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", type=Path, help="also write preview pictures into this folder")
    args = parser.parse_args()
    prepared = prepare_all()
    write(prepared)
    if args.preview:
        preview(prepared, args.preview)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
