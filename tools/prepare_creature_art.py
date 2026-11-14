#!/usr/bin/env python3
"""Prepare the visitors' pictures (#71): cut out, split into parts, sized, and packed into an atlas of their own.

The originals are the eleven Recraft pictures in tools/source_art/ (creature_*.png, opaque, 1024 x
1024, on white), kept as they came. This script:

  * cuts each out of its white (cut_out from prepare_drawable_art.py), with what each needs:
      - the squirrel: its fur is mixed with the white far into the outline (the fine hairs of the
        tail); the outline is un-mixed deeper, so that no pale haze is left round the tail. The
        running one's cream belly fades into the white: it is let go softly (soft_underside);
      - the bird: its tail, cheeks and belly are white or nearly; only plain white goes, and no
        white pockets are taken out of it;
      - the bird: also the plain white shut in between its legs (without_white_pockets);
      - the dragonfly: its wings are clear. Their membrane, a pale bluish white, is kept half
        see-through, the veins, the rim and the body solid (clear_wings);
  * splits the fliers into parts that move on their own (TileSetDefinition.creaturePartIds):
      - the butterfly and the dragonfly (seen from above, head up): the body (with the head, the
        antennae and the legs) and the left and right wings, cut along the middle of the body. A
        wing keeps the part under the body, so that when it folds towards the body no gap opens;
      - the flying bird: the body (head, breast, tail) and its two wings, cut along an outline of
        the body drawn by hand (BIRD_BODY). Each wing turns about its root, behind the body;
    each wing's anchor is where it joins the body, and its attachment point is the body's anchor:
    the game hangs the wing on the body by the two (CreatureSprites.kt);
  * brings each to its size in the game (SIZES, in tiles) at TILE atlas pixels to the tile: as big
    as it is drawn at the closest zoom (GardenCamera.MAX_SCALE), so that it is not blurred there.
    The running squirrel, the flying and the pecking bird and the leaping frog are brought to the
    same scale as the sitting or standing one by the size of their heads and eyes (SAME_SCALE), so
    that switching does not jump;
  * anchors: the feet of those on the ground; the middle of the underside of the running squirrel;
    the middle of the body of the flying bird; the middle of the thorax of the butterfly and the
    dragonfly (the point they fly about by);
  * writes them to tools/raw_creatures/ with their anchors in tools/creature_overrides.json, and
    packs them (asset_packer.py) into art/png/assets/atlas/creatures_<n>.png and
    creatures_manifest.json: not into the crops' atlas, which is as large as an image may be.

Usage:
  python tools/prepare_creature_art.py                 # writes raw_creatures/ and the creature atlas
  python tools/prepare_creature_art.py --preview out/  # also a sheet of all of them on a dark ground

Requires Pillow and numpy.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import asset_packer
from prepare_building_art import ALPHA_MIN, SOURCE, TOOLS, border, dilate, reconstruct, resize
from prepare_drawable_art import cut_out, erode

RAW = TOOLS / "raw_creatures"
OVERRIDES = TOOLS / "creature_overrides.json"
# PNG masters; tools/webp_art.py makes the app's WebP from them (#fb-1111).
ATLAS_DIR = TOOLS.parent / "art" / "png" / "assets" / "atlas"

#: Atlas pixels to the tile: a tile is 128 dp wide, 336 px on a 420 dpi screen, and the closest
#: zoom (GardenCamera.MAX_SCALE) is 2.5 times that, about 840 px.
TILE = 960

#: How big each picture is in the game, in tiles: ("w", share) by its width, ("h", share) by its height.
SIZES: dict[str, tuple[str, float]] = {
    "squirrel": ("h", 0.2),
    "bird": ("w", 0.2),
    "frog": ("w", 0.2),
    "ladybug": ("w", 0.075),
    "butterfly": ("w", 0.2),
    # 1.3 times what it was (2026-11-08): at the usual zooms it was too small to tell it was flying.
    "dragonfly": ("w", 0.26),
    # #book60: the rabbit a little bigger than the squirrel, by the height of it sitting up; the
    # snail a little bigger than the ladybird, large enough for its shell's whorl at the closest zoom.
    "rabbit": ("h", 0.24),
    "snail": ("w", 0.11),
}

#: Pictures brought to the scale of another: (that one, how much larger its head is there). The
#: heads were measured on the originals (nose or beak to the back of the head, in pixels):
#: sitting squirrel 190, running 123; standing bird 228, flying 135.
SAME_SCALE: dict[str, tuple[str, float]] = {
    "squirrel_run": ("squirrel", 190 / 123),
    "bird_fly": ("bird", 228 / 135),
    # Three frames more (2026-11-02), by their eyes and heads. The pecking bird's eye is as large
    # as the standing one's (47 px high), its head and body a little larger: 0.92 between the two.
    # The gathered squirrel is seen more from the side than the sitting one, so by the width of its
    # head on the two laid side by side, not its eye: 1.03. The
    # leaping frog is drawn much smaller than the sitting one: its eye is 60 px to 112, its head
    # 150 to 267.
    "bird_peck": ("bird", 0.92),
    "squirrel_run2": ("squirrel", 1.03),
    "frog_jump": ("frog", 1.8),
    # #book60: the leaping rabbit is drawn at the scale of the sitting one (their heads are as
    # long: 150 px nose to the back of the head on both originals).
    "rabbit_run": ("rabbit", 1.0),
}

#: Pictures facing the viewer (2026-11-07), shown for a moment as the bird and the squirrel turn
#: round: brought to the height of the one seen from the side (head to feet), and stood on their
#: feet like it, so that turning does not jump.
FRONT_OF: dict[str, str] = {"bird_front": "bird", "squirrel_front": "squirrel", "rabbit_front": "rabbit"}

#: The rabbit's winter coat (#book60): its three pictures with the fur painted white in code, the
#: dark tips of its ears, its eyes, its nose and its shading kept. Fur is the brown of it (red over
#: green over blue) and not dark; its new colour keeps the brightness the brown had, lifted into
#: [WINTER_FUR_LOW] to [WINTER_FUR_HIGH] of [WINTER_WHITE], so that it keeps its shape on snow.
WINTER_WHITE = (247.0, 246.0, 243.0)
WINTER_FUR_LOW = 0.68
WINTER_FUR_HIGH = 1.0
WINTER_FUR_DARKEST = 38

#: How far down the rabbit (a share of its height) the dark patches kept as they are may lie: its
#: eyes and the tips of its ears are all above it, the dark markings of its back below.
WINTER_HEAD_SHARE = 0.42

#: Where the pecking bird's feet are on the original: its beak reaches as low as they do, so
#: feet() would take it for one of them.
BIRD_PECK_FEET = (430.0, 888.0)

#: The eye of the sitting frog and of the leaping one, on the originals. The leaping one is hung by
#: its head where the sitting one's head is (leaping_anchor): its legs fly out behind.
FROG_EYE = (370.0, 367.0)
FROG_JUMP_EYE = (287.0, 453.0)

#: The outline of the flying bird's body on the original (head, breast, belly, tail), in pixels.
BIRD_BODY = [
    (385, 440), (395, 400), (420, 375), (460, 372), (500, 380), (520, 400), (548, 400), (560, 425),
    (600, 445), (630, 458), (648, 480), (655, 520), (660, 540), (700, 555), (760, 580), (815, 605),
    (812, 618), (760, 628), (720, 634), (690, 690), (640, 690), (600, 610), (560, 584), (530, 582),
    (507, 572), (490, 562), (470, 549), (450, 522), (430, 480), (415, 462), (395, 456),
]

#: Where each of the flying bird's wings joins its body, on the original: it turns about there.
BIRD_HINGES = {"left": (470, 515), "right": (595, 440)}

#: The butterfly's and the dragonfly's thorax on the original: the middle of the body, where the
#: wings join it. The wings fold towards x.
THORAX = {"butterfly": (512, 500), "dragonfly": (512, 430)}

#: Half the width of the body (pixels on the original) kept with the body when the wings are cut off.
BODY_HALF_WIDTH = {"butterfly": 14, "dragonfly": 30}


# --- Cutting out -------------------------------------------------------------------------------

def source(name: str) -> np.ndarray:
    return np.array(Image.open(SOURCE / f"creature_{name}.png").convert("RGB"))


def clear_wings(rgb: np.ndarray, floor: float = 0.3, solid: float = 70.0) -> np.ndarray:
    """The dragonfly, its clear wings half see-through. The membrane is a pale bluish white, a little
    darker than the plain white round it: only plain white reaching the edge is background, and
    everything else takes its alpha from how far it is from white (at least [floor], so that the
    membrane shows), its colour un-mixed from the white. The veins, the rim and the body are solid."""
    v = rgb.astype(float)
    mx, mn = v.max(axis=2), v.min(axis=2)
    plain = (mn >= 252) & ((mx - mn) <= 6)
    background = reconstruct(border(plain.shape) & plain, plain)
    alpha = np.where(background, 0.0, np.clip((255.0 - mn) / solid, floor, 1.0))
    # Faint specks of off-white in the background, not joined to the dragonfly, go.
    alpha = np.where(reconstruct(alpha >= 0.99, alpha > 0), alpha, 0.0)
    safe = np.maximum(alpha, 1e-3)[..., None]
    out = np.clip((v - 255.0 * (1.0 - alpha[..., None])) / safe, 0, 255)
    return np.dstack([out, alpha * 255.0]).astype(np.uint8)


def without_white_pockets(rgb: np.ndarray, cut: np.ndarray, pocket: int = 4) -> np.ndarray:
    """[cut] with the plain white shut in by the picture taken out too (between the bird's legs):
    only pure white in patches wider than [pocket], so that the white of its belly and tail stays."""
    v = rgb.astype(float)
    mx, mn = v.max(axis=2), v.min(axis=2)
    pure = (mn >= 248) & ((mx - mn) <= 6) & (cut[:, :, 3] > 0)
    holes = reconstruct(erode(pure, pocket), pure)
    out = cut.astype(float)
    out[holes, 3] = 0
    rim = dilate(holes, 2) & ~holes & (cut[:, :, 3] > 0)
    a = np.clip((255.0 - mn) / 70.0, 0.0, 1.0)
    out[rim, 3] = np.minimum(out[rim, 3], a[rim] * 255.0)
    unmixed = np.clip((v - 255.0 * (1.0 - a[..., None])) / np.maximum(a, 1e-3)[..., None], 0, 255)
    out[rim, :3] = unmixed[rim]
    return out.astype(np.uint8)


def soft_underside(rgb: np.ndarray, cut: np.ndarray, box, dark: int = 150, full: int = 1, gone: int = 6) -> np.ndarray:
    """[cut] with the pale fur of the underside, inside [box] (pixels on the original), let go softly.

    The running squirrel's belly is a light cream (its darkest channel 180 to 210) that fades into
    the white behind it: the usual cut took part of it for background and left a ragged white band.
    Here the pale fur is faded by its distance from the darker fur above it, whole up to [full]
    pixels from it and gone by [gone], so that the outline follows the smooth edge of the orange fur.
    Nothing that was background comes back.
    """
    v = rgb.astype(float)
    mn = v.min(axis=2)
    x0, y0, x1, y1 = box
    zone = np.zeros(mn.shape, bool)
    zone[y0:y1, x0:x1] = True
    body = (mn < dark) & (cut[:, :, 3] > 0)
    distance = np.full(mn.shape, float(gone + 1))
    distance[body] = 0.0
    reach = body.copy()
    for step in range(1, gone + 1):
        grown = dilate(reach, 1)
        distance[grown & ~reach] = step
        reach = grown
    pale = zone & ~body & (cut[:, :, 3] > 0)
    fade = np.clip(1.0 - (distance - full) / (gone - full), 0.0, 1.0)
    out = cut.astype(float)
    out[pale, 3] = np.minimum(out[pale, 3], 255.0 * fade[pale])
    # A shade darker, so that what is left of it does not glow against a dark ground.
    out[pale, :3] = v[pale] * 0.92
    return out.astype(np.uint8)


#: The running squirrel's underside, from its chest to its hind leg, on the original (pixels).
RUN_UNDERSIDE = (230, 520, 650, 615)


def cut(name: str) -> np.ndarray:
    rgb = source(name)
    if name == "squirrel_run":
        # No white pockets in the fur: its bright highlights are not background (#71).
        return soft_underside(rgb, cut_out(rgb, edge=14, solid=120.0, pocket=60), RUN_UNDERSIDE)
    if name.startswith("squirrel"):
        return cut_out(rgb, edge=14, solid=120.0)
    if name.startswith("bird"):
        return without_white_pockets(rgb, cut_out(rgb, light=244, tol=10, pocket=60, edge=3))
    if name == "dragonfly":
        return clear_wings(rgb)
    if name.startswith("rabbit"):
        # Fine fur like the squirrel's, and a white belly and tail: only plain white goes.
        return cut_out(rgb, edge=10, solid=110.0, white_parts=1.0, pocket=60)
    if name == "snail":
        # Its eye stalks are pale and thin: only plain white goes round them.
        return cut_out(rgb, white_parts=1.0)
    return cut_out(rgb)


def winter_coat(rgba: np.ndarray) -> np.ndarray:
    """The rabbit (#book60) in its white winter coat: its brown fur white, all else as it was."""
    rgb = rgba[:, :, :3].astype(float)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    lum = rgb.mean(axis=2)
    seen = rgba[:, :, 3] > 0
    fur = seen & (r > b + 10) & (lum > WINTER_FUR_DARKEST)
    # What is darker still is the eyes and the ears' tips where it lies in patches (thick enough to
    # outlast a few erosions); in single dark hairs among the fur it is fur, and goes white too.
    dark = seen & (lum <= WINTER_FUR_DARKEST)
    patches = reconstruct(erode(dark, 3), dark)
    # Only up at the head: the dark patches of the back and flanks are fur, and go white.
    ys = np.where(seen.any(axis=1))[0]
    patches[ys.min() + int((ys.max() - ys.min()) * WINTER_HEAD_SHARE):] = False
    hairs = dark & ~dilate(patches, 2)
    top = np.percentile(lum[fur], 98) if fur.any() else 255.0
    level = WINTER_FUR_LOW + (WINTER_FUR_HIGH - WINTER_FUR_LOW) * np.clip(lum / top, 0.0, 1.0)
    white = level[..., None] * np.array(WINTER_WHITE)
    # Eased in from the darkest fur, so that the ears' dark tips do not end in a hard line.
    weight = np.maximum(np.clip((lum - WINTER_FUR_DARKEST) / 12.0, 0.0, 1.0) * fur, hairs * 1.0)
    out = rgba.copy()
    out[:, :, :3] = np.clip(rgb * (1 - weight[..., None]) + white * weight[..., None], 0, 255).astype(np.uint8)
    return out


def clearer_ladybug(rgba: np.ndarray, saturation: float = 1.3, value: float = 1.08, rim: float = 0.3) -> np.ndarray:
    """The ladybird made to stand out on leaves, still as photographed: the red of its wing cases
    more saturated and a touch brighter, and its outermost pixel all round darkened to [rim] of
    itself, a thin dark edge. Nothing else of the picture changes."""
    out = rgba.astype(float)
    rgb = out[:, :, :3] / 255.0
    mx, mn = rgb.max(axis=2), rgb.min(axis=2)
    r, g, b = rgb[:, :, 0], rgb[:, :, 1], rgb[:, :, 2]
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    red = (r >= mx - 1e-6) & (sat > 0.35) & (g < r * 0.75) & (b < r * 0.75) & (rgba[:, :, 3] > 0)
    # Scale the colour away from its grey (the min) and lift it a little.
    grey = mn[..., None]
    vivid = grey + (rgb - grey) * saturation
    vivid = np.clip(vivid * value, 0, 1)
    rgb = np.where(red[..., None], vivid, rgb)
    solid = rgba[:, :, 3] > 128
    edge = solid & ~erode(solid, 1)
    rgb = np.where(edge[..., None], rgb * rim, rgb)
    out[:, :, :3] = rgb * 255.0
    return np.clip(out, 0, 255).astype(np.uint8)


# --- Parts -------------------------------------------------------------------------------------

def polygon_mask(shape, points) -> np.ndarray:
    image = Image.new("L", (shape[1], shape[0]), 0)
    ImageDraw.Draw(image).polygon(points, fill=255)
    return np.array(image) > 0


def only(rgba: np.ndarray, mask: np.ndarray) -> np.ndarray:
    """[rgba] where [mask] is, and nothing elsewhere; specks cut off from the rest of the part go too."""
    out = rgba.copy()
    out[~mask, 3] = 0
    seen = out[:, :, 3] > 0
    keep = reconstruct(erode(out[:, :, 3] > 128, 2), seen)
    out[~keep, 3] = 0
    return out


def insect_parts(name: str, rgba: np.ndarray) -> dict[str, np.ndarray]:
    """The body (with the head, antennae and legs) and the left and right wings of a flier seen from above."""
    h, w = rgba.shape[:2]
    cx, cy = THORAX[name]
    ys, xs = np.indices((h, w))
    band = np.abs(xs - cx) <= BODY_HALF_WIDTH[name]
    if name == "butterfly":
        # The antennae: thin and dark, rising from the head over the inner edges of the wings.
        dark = rgba[:, :, :3].max(axis=2) < 120
        head = (ys < 468) & (np.abs(xs - cx) <= 14 + (468 - ys) * 0.6) & dark
    else:
        # The head, its big eyes, and the legs held forward, all above the forewings.
        head = (ys < 362) & (np.abs(xs - cx) <= 125)
    body = band | head
    return {
        "body": only(rgba, body),
        # Each wing keeps the part under the body: folding towards the body opens no gap.
        "wing_left": only(rgba, (xs <= cx) & ~head),
        "wing_right": only(rgba, (xs >= cx) & ~head),
    }


def bird_parts(rgba: np.ndarray) -> dict[str, np.ndarray]:
    """The flying bird's body and its two wings (BIRD_BODY)."""
    h, w = rgba.shape[:2]
    ys, xs = np.indices((h, w))
    body = polygon_mask(rgba.shape, BIRD_BODY)
    left = ~body & (xs < 520) & (ys > 440)
    right = ~body & ~left & (xs >= 520) & (ys < 545)
    return {"body": only(rgba, ~left & ~right), "wing_left": only(rgba, left), "wing_right": only(rgba, right)}


# --- Sizes and anchors -------------------------------------------------------------------------

def bounds(rgba: np.ndarray) -> tuple[int, int, int, int]:
    ys, xs = np.where(rgba[:, :, 3] > ALPHA_MIN)
    return xs.min(), ys.min(), xs.max() + 1, ys.max() + 1


def feet(rgba: np.ndarray) -> tuple[float, float]:
    """The middle of what touches the ground: the lowest few rows of the picture."""
    alpha = rgba[:, :, 3] > 128
    ys, xs = np.where(alpha)
    low = ys >= ys.max() - 12
    return float(xs[low].mean()), float(ys.max() + 1)


def run_belly(rgba: np.ndarray) -> tuple[float, float]:
    """The middle of the underside of the running squirrel's body, without its tail (the left 60 % of it)."""
    x0, y0, x1, y1 = bounds(rgba)
    alpha = rgba[:, :, 3] > 128
    cols = range(x0, x0 + int((x1 - x0) * 0.6))
    xs = np.array(list(cols))
    bottoms = np.array([np.where(alpha[:, x])[0].max() if alpha[:, x].any() else y0 for x in cols])
    # The legs hang below the body; the belly is the middle of what is left above them.
    return float(xs.mean()), float(np.median(bottoms))


def leaping_anchor(sitting_feet: tuple[float, float], ratio: float) -> tuple[float, float]:
    """The point of the leaping frog that lies where the sitting one's feet do, its eye over its eye."""
    return (FROG_JUMP_EYE[0] + (sitting_feet[0] - FROG_EYE[0]) / ratio, FROG_JUMP_EYE[1] + (sitting_feet[1] - FROG_EYE[1]) / ratio)


def to_atlas(rgba: np.ndarray, scale: float) -> tuple[np.ndarray, tuple[float, float]]:
    """[rgba] cropped, resampled by [scale] and cropped again as asset_packer.py will (to any alpha);
    and where the original's (0, 0) lands on it, in its pixels."""
    x0, y0, x1, y1 = bounds(rgba)
    picture = resize(rgba[y0:y1, x0:x1], scale, scale)
    ys, xs = np.where(picture[:, :, 3] > 0)
    picture = picture[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    return picture, (-x0 * scale - xs.min(), -y0 * scale - ys.min())


def fraction(point, zero, scale, picture) -> dict:
    """[point] (pixels on the original) as fractions of [picture], on which the original's (0, 0) is [zero]."""
    h, w = picture.shape[:2]
    return {"x": round((zero[0] + point[0] * scale) / w, 4), "y": round((zero[1] + point[1] * scale) / h, 4)}


def prepare() -> tuple[dict[str, np.ndarray], dict[str, dict]]:
    cuts = {name: cut(name) for name in
            ["squirrel", "squirrel_run", "squirrel_run2", "squirrel_front", "bird", "bird_fly", "bird_peck", "bird_front",
             "frog", "frog_jump", "ladybug", "butterfly", "dragonfly", "rabbit", "rabbit_run", "rabbit_front", "snail"]}
    scales: dict[str, float] = {}
    for name, (side, share) in SIZES.items():
        x0, y0, x1, y1 = bounds(cuts[name])
        scales[name] = share * TILE / ((x1 - x0) if side == "w" else (y1 - y0))
    for name, (other, ratio) in SAME_SCALE.items():
        scales[name] = scales[other] * ratio
    for name, side in FRONT_OF.items():
        _, sy0, _, sy1 = bounds(cuts[side])
        _, fy0, _, fy1 = bounds(cuts[name])
        scales[name] = scales[side] * (sy1 - sy0) / (fy1 - fy0)

    pictures: dict[str, np.ndarray] = {}
    overrides: dict[str, dict] = {}

    def add(asset_id: str, rgba: np.ndarray, scale: float, anchor, attach=None) -> tuple[np.ndarray, tuple[float, float]]:
        picture, zero = to_atlas(rgba, scale)
        pictures[asset_id] = picture
        a = fraction(anchor, zero, scale, picture)
        # A hinge on the very edge of a wing can come out a hair past it.
        entry = {"anchorX": min(max(float(a["x"]), 0.0), 1.0), "anchorY": min(max(float(a["y"]), 0.0), 1.0)}
        if attach is not None:
            t = fraction(attach, zero, scale, picture)
            entry.update({"attachX": float(t["x"]), "attachY": float(t["y"])})
        overrides[asset_id] = entry
        return picture, zero

    for name in ("squirrel", "bird", "frog"):
        add(f"creature_{name}", cuts[name], scales[name], feet(cuts[name]))
    # The ladybird's legs reach out behind it: it stands on the middle of itself.
    ys, xs = np.where(cuts["ladybug"][:, :, 3] > 128)
    add("creature_ladybug", cuts["ladybug"], scales["ladybug"], (float(xs.mean()), float(ys.max() + 1)))
    # Clearer against green leaves (2026-11-08): its red a little brighter, and a thin dark rim
    # round it at the size it is packed at. Its size is as it was.
    pictures["creature_ladybug"] = clearer_ladybug(pictures["creature_ladybug"])
    add("creature_squirrel_run", cuts["squirrel_run"], scales["squirrel_run"], run_belly(cuts["squirrel_run"]))
    # The gathered squirrel touches the ground: it stands on its feet.
    add("creature_squirrel_run2", cuts["squirrel_run2"], scales["squirrel_run2"], feet(cuts["squirrel_run2"]))
    add("creature_bird_peck", cuts["bird_peck"], scales["bird_peck"], BIRD_PECK_FEET)
    # Facing the viewer: stood between its two feet.
    for name in FRONT_OF:
        add(f"creature_{name}", cuts[name], scales[name], feet(cuts[name]))
    add("creature_frog_jump", cuts["frog_jump"], scales["frog_jump"], leaping_anchor(feet(cuts["frog"]), SAME_SCALE["frog_jump"][1]))

    # #book60: the rabbit sitting, leaping (stood on its lowest point, its hind feet, which touch
    # down as it lands) and facing the viewer (between its feet); and each again in its white
    # winter coat. The snail on the middle of its foot.
    for name in ("rabbit", "rabbit_run", "rabbit_front"):
        add(f"creature_{name}", cuts[name], scales[name], feet(cuts[name]))
        white = name.replace("rabbit", "rabbit_white")
        add(f"creature_{white}", winter_coat(cuts[name]), scales[name], feet(cuts[name]))
    ys, xs = np.where(cuts["snail"][:, :, 3] > 128)
    low = ys >= ys.max() - 12
    add("creature_snail", cuts["snail"], scales["snail"], (float(xs[low].mean()), float(ys.max() + 1)))

    # The flying bird: anchored on the middle of its body; each wing hung on its root.
    fly = bird_parts(cuts["bird_fly"])
    centre = (540.0, 500.0)
    add("creature_bird_fly_body", fly["body"], scales["bird_fly"], centre)
    for side in ("left", "right"):
        add(f"creature_bird_fly_wing_{side}", fly[f"wing_{side}"], scales["bird_fly"], BIRD_HINGES[side], attach=centre)

    for name in ("butterfly", "dragonfly"):
        thorax = THORAX[name]
        add(f"creature_{name}", cuts[name], scales[name], thorax)
        parts = insect_parts(name, cuts[name])
        add(f"creature_{name}_body", parts["body"], scales[name], thorax)
        for side in ("left", "right"):
            add(f"creature_{name}_wing_{side}", parts[f"wing_{side}"], scales[name], thorax, attach=thorax)

    # The standing bird's body is this high above its feet, in tiles: the flying bird, anchored on
    # its body, is lifted by as much when it comes down, so that it lands where the standing one stands.
    stand = cuts["bird"]
    _, foot = feet(stand)
    print(f"bird body above its feet: {(foot - 584) * scales['bird'] / TILE:.4f} tiles (BIRD_BODY_HEIGHT)")
    return pictures, overrides


def write(pictures: dict[str, np.ndarray], overrides: dict[str, dict]) -> dict:
    RAW.mkdir(exist_ok=True)
    for old in RAW.glob("*.png"):
        old.unlink()
    for asset_id, rgba in pictures.items():
        Image.fromarray(rgba, "RGBA").save(RAW / f"{asset_id}.png", optimize=True)
        print(f"{asset_id:32s} {rgba.shape[1]}x{rgba.shape[0]}  {overrides[asset_id]}")
    OVERRIDES.write_text(json.dumps(overrides, indent=2) + "\n", encoding="utf-8")
    manifest = asset_packer.run(
        RAW, ATLAS_DIR, tile_width=TILE, page_size=1024, padding=2, overrides_path=OVERRIDES,
        sheet="creatures", manifest_name="creatures_manifest.json",
    )
    for page in manifest["pages"]:
        size = (ATLAS_DIR / page["file"]).stat().st_size
        print(f"packed into {page['file']} {page['width']}x{page['height']}, {size / 1024:.0f} KiB")
    return manifest


def preview(pictures: dict[str, np.ndarray], folder: Path) -> None:
    """Every picture on a dark ground, at twice its size in the atlas, with its anchor marked."""
    folder.mkdir(parents=True, exist_ok=True)
    overrides = json.loads(OVERRIDES.read_text(encoding="utf-8"))
    tiles = []
    for asset_id, rgba in pictures.items():
        image = Image.fromarray(rgba, "RGBA")
        image = image.resize((image.width * 2, image.height * 2), Image.LANCZOS)
        ground = Image.new("RGBA", (image.width + 40, image.height + 60), (34, 40, 52, 255))
        ground.alpha_composite(image, (20, 20))
        draw = ImageDraw.Draw(ground)
        o = overrides[asset_id]
        ax, ay = 20 + o["anchorX"] * image.width, 20 + o["anchorY"] * image.height
        draw.ellipse((ax - 4, ay - 4, ax + 4, ay + 4), outline=(255, 80, 80))
        draw.text((6, image.height + 38), asset_id.removeprefix("creature_"), fill=(230, 230, 230))
        tiles.append(ground)
    width = sum(t.width for t in tiles)
    sheet = Image.new("RGBA", (min(width, 1800), 10), (34, 40, 52, 255))
    rows, row, x = [], [], 0
    for t in tiles:
        if x + t.width > 1800 and row:
            rows.append(row)
            row, x = [], 0
        row.append(t)
        x += t.width
    rows.append(row)
    height = sum(max(t.height for t in r) for r in rows)
    sheet = Image.new("RGBA", (1800, height), (34, 40, 52, 255))
    y = 0
    for r in rows:
        x = 0
        for t in r:
            sheet.alpha_composite(t, (x, y))
            x += t.width
        y += max(t.height for t in r)
    sheet.save(folder / "creatures.png")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", type=Path, help="also write a sheet of the pictures into this folder")
    args = parser.parse_args()
    pictures, overrides = prepare()
    write(pictures, overrides)
    if args.preview:
        preview(pictures, args.preview)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
