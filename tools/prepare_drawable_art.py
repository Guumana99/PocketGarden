#!/usr/bin/env python3
"""Prepare the crop, icon and ground pictures received in #57 and #58 (all opaque, 1024 x 1024, on white).

The originals are kept in tools/source_art/ under the names they came with and are never changed.
This script:

  * cuts each picture out of its white background: the white (and the pale grey of the soft
    shadows under the seedlings) that reaches the edge of the picture, plus large pockets of plain
    white enclosed by leaves; the pixels along the outline are un-mixed from the white, so that no
    pale fringe is left (cut_out);
  * crops: makes the stages of one crop agree in how many plants they show and how big they are
    (#57), and writes them to raw_assets/plant_<crop>_stage<N>.png at the atlas' source scale
    (720 px a tile), each as tall (or, for the wheat field, as wide) as STAGES says:
      - corn: stage 1 came as a sheet of 16 seedlings; three of them are set out in the triangle the
        three stalks of stage 3 stand in. Stage 2 came with two stalks; a third, the right-hand one
        mirrored, is stood in front of them in the same place;
      - eggplant: stage 1 came as four seedlings; the front one is kept, as stages 2 and 3 are one plant;
      - carrot: stage 2 (#74) is the leaves with a thin ring of the root showing at their foot
        (until #74, when none had come, stage 3's leaves cut off above the root).
        Stage 3 (#60) is the leaves with only the shoulder of the root showing, as a carrot
        stands in its bed; the whole root above the soil read as lying on the ground;
      - wheat: all three stages are a whole field filling the cell; they are laid out one tile wide
        and stood on the middle of the field (atlas_overrides.json) instead of on a stem;
      - tulip (#58): stages 1 and 2, and stage 3 in six colours: red is plant_tulip_stage3, the
        others plant_tulip_stage3_<colour> (TULIP_COLOURS), all at the same size;
      - mint (#62): three stages and a withered one, as they came; a low herb;
      - cosmos (#63): the same, a tall flower;
      - snowdrop (#64): the same, a low flower; stage 1 came with its white roots showing below the
        leaves, which are cut off, so that it stands on the foot of its leaves (snowdrop_stages);
      - watermelon (#65): the same; stages 2, 3 and withered are a low, wide mound of leaves with no
        stem to stand on (stage 3 with its fruit in front), sized by their width, not their height;
      - lavender (#67): the same, a round bushy clump;
      - shiitake (#68): a log lying on the ground, the same at every stage but for the mushrooms on
        it: each picture is sized by its log alone ("log"), so that the log is as long in all of
        them, and stood on the foot of the log (log_anchor);
      - poinsettia (#69): three stages and a withered one, as they came; a round bush;
      - withered pictures (#58) came for every crop here but the tulip, which does not wither;
        each is as tall (or as wide) as WITHERED says;
  * icons: icon_<crop>.png, cut out and brought to ICON_SIZE, to res/drawable-nodpi for the bag:
    one for each crop here, and for the crops whose plants are made elsewhere (ICON_ONLY, #72);
    and icon_<item>.png for the buildings, decorations and ground of #74 (ITEM_ICONS), cut from
    the same pictures as their sprites, the ground as the slab it came as;
  * the ground of #74 (GROUND_TEXTURES): the top face of each slab (tile_<name>_iso.png) seen from straight
    above, its large light and shade evened out, made to tile, to res/drawable-nodpi/<objectId>.png,
    which the garden lays over a cell, one copy to a cell (ground_texture);
  * the lawn: the top face of tile_grass_iso.png unprojected into a flat square, for
    prepare_building_art.py to make tile_grass from (flat_lawn);
  * prints where the top face lies in each ground picture (GroundSlab's values).

Usage:
  python tools/prepare_drawable_art.py                 # writes raw_assets/ and the icons
  python tools/prepare_drawable_art.py --preview out/  # also writes preview pictures
Then run prepare_building_art.py (the lawn) and asset_packer.py (see docs/ASSETS.md).

Requires Pillow and numpy.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image

from prepare_building_art import (
    ALPHA_MIN, OVERRIDES, RAW, SOURCE, TILE, border, components, crop, dilate,
    reconstruct, resize, top_face_of,
)

ROOT = SOURCE.parent.parent
ICONS = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
GROUND = ROOT / "app" / "src" / "main" / "res" / "drawable"

#: Pixels a side of an icon in the bag: about three times its size on a phone.
ICON_SIZE = 256

#: Each stage's size in tiles: its height, ("w", width) for a field laid over the whole cell, or
#: ("log", width) for the shiitake's log, whose length alone is set (log_width).
STAGES: dict[str, list] = {
    "corn": [0.36, 0.7, 0.9],
    "eggplant": [0.3, 0.58, 0.78],
    "carrot": [0.38, 0.48, 0.62],
    "wheat": [("w", 0.92), ("w", 0.92), ("w", 0.92)],
    "tomato": [0.34, 0.74, 0.86],
    "strawberry": [0.3, 0.48, 0.58],
    "tulip": [0.3, 0.48, 0.62],
    "sunflower": [0.32, 0.7, 0.88],
    "mint": [0.32, 0.42, 0.62],
    "cosmos": [0.3, 0.64, 0.85],
    "snowdrop": [0.3, 0.48, 0.62],
    "watermelon": [0.3, ("w", 0.7), ("w", 0.85)],
    "lavender": [0.3, 0.5, 0.66],
    "shiitake": [("log", 0.5), ("log", 0.5), ("log", 0.5)],
    "poinsettia": [0.3, 0.55, 0.7],
}

#: How a crop's pictures (stages, withered and icon) are cut out, where the defaults do not do:
#: the cosmos' leaves (#63) are hair-fine, and the white showing through them left them in a pale
#: haze; it is taken out of the whole picture, not only its outline (cut_out's [throughout]).
#: The snowdrop's flowers (#64) are white, and the white is taken out more gently round them.
#: The lavender (#67) is as fine as the cosmos. The shiitake's withered log (#68) has pale grey
#: bark, the top of which the usual cut took for background, as with the snowdrop's flowers.
CUT: dict[str, dict] = {
    "cosmos": {"throughout": True, "solid": 100.0},
    "snowdrop": {"white_parts": 0.8},
    "lavender": {"throughout": True, "solid": 100.0},
    "shiitake": {"white_parts": 0.8},
}

#: The same for an icon, where it is cut out differently from its plant: the lavender's bunch is
#: tied with pale twine, which the white was taken out of (#67). The daikon's root is white, and
#: the usual cut bit its shaded side away (#72). The eggplants (#73) lay on a grey shadow that was
#: left under them, and the watermelon's pale, lit side was bitten into, leaving white flecks.
ICON_CUT: dict[str, dict] = {
    "lavender": {"white_parts": 1.0},
    "daikon": {"white_parts": 1.0},
    "eggplant": {"shadow": 0.7},
    "watermelon": {"white_parts": 1.0},
}

#: Crops whose icon is made here though their plants are not (#72): the rose, paprika and daikon
#: are still the #39 pictures, packed from raw_assets as they are.
ICON_ONLY = {"rose", "paprika", "daikon"}

#: Pictures whose leaves are brought up to the brightness of the rest of their crop, by this gain
#: (every channel alike, so their colour stays as it is; yellow flowers are left alone). The
#: watermelon's stage 2 (#66) came with leaves of a near-black grey-green that read as burnt on the
#: bed: their median brightness was 51 against stage 3's 66.
LEAF_GAIN: dict[str, float] = {
    "plant_watermelon_stage2": 1.3,
}

#: The tulip's flowers (#58): red is its plain stage 3, the others are variants of it.
TULIP_COLOURS = ["pink", "yellow", "white", "purple", "blue"]

#: Anchors set by hand, where the foot of the picture is not the stem (asset_packer's stem_base
#: would stand it on the tip of a hanging leaf): the tomato's main stem, behind its lower leaves.
#: The sunflower's first stage (#58) is three seedlings: it stands on their middle, not the left one's stem.
#: The eggplant (#60) is a bush with its fruit hanging below the leaves and no stem to be seen:
#: stood on the foot of its picture it hovered over its bed. It stands where the bush meets the
#: ground, the fruit hanging down in front; the withered one on its bare stem.
ANCHORS = {
    "plant_tomato_stage3": {"anchorX": 0.47, "anchorY": 0.97, "isFlippedAllowed": True},
    "plant_sunflower_stage1": {"anchorX": 0.5, "anchorY": 1.0, "isFlippedAllowed": True},
    "plant_eggplant_stage2": {"anchorX": 0.5, "anchorY": 0.85, "isFlippedAllowed": True},
    "plant_eggplant_stage3": {"anchorX": 0.48, "anchorY": 0.78, "isFlippedAllowed": True},
    "plant_eggplant_withered": {"anchorX": 0.5, "anchorY": 0.83, "isFlippedAllowed": True},
}

#: How much of the carrot's root shows above its bed at stage 3 (#60), as a share of the root,
#: at the front of the root (where the soil line is lowest).
CARROT_SHOULDER = 0.22

#: Where the root goes into the bed (device feedback, after #75): not a level cut but the near half
#: of the ring where round root meets flat earth, so the line dips at the front by this share of the
#: root's half-width, and wanders a little ([CARROT_SOIL_WANDER] px) as crumbled earth does. Just
#: above it the root is stained with the earth ([CARROT_EARTH], over [CARROT_EARTH_ROWS] rows).
CARROT_SOIL_DIP = 0.35
CARROT_SOIL_WANDER = 3.0
CARROT_EARTH = (78, 56, 40)
CARROT_EARTH_ROWS = 14

#: Each withered picture's size in tiles, as in STAGES (#58: drawn ones replaced those made by
#: browning the last stage in #57, and the tomato's and strawberry's from #39).
WITHERED: dict[str, object] = {
    "corn": 0.8,
    "eggplant": 0.7,
    "carrot": 0.42,
    "wheat": ("w", 0.92),
    "tomato": 0.75,
    "strawberry": 0.48,
    "sunflower": 0.72,
    "mint": 0.62,
    "cosmos": 0.85,
    "snowdrop": 0.62,
    "watermelon": ("w", 0.85),
    "lavender": 0.66,
    "shiitake": ("log", 0.5),
    "poinsettia": 0.7,
}


# --- Cutting out -------------------------------------------------------------------------------

#: How grey a shadow is (cut_out's [shadow]): its channels at most this far apart, none below the
#: darkest, so that the dark, purplish shading of a fruit that meets it is not taken as shadow.
SHADOW_TOL = 7
SHADOW_DARKEST = 40


def erode(mask: np.ndarray, times: int) -> np.ndarray:
    return ~dilate(~mask, times)


def cut_out(rgb: np.ndarray, light: int = 190, tol: int = 30, pocket: int = 3, edge: int = 2,
            solid: float = 70.0, throughout: bool = False, white_parts: float = 0.0,
            shadow: float = 0.0) -> np.ndarray:
    """[rgb] (opaque, on white) with the white made transparent; RGBA.

    [edge]: how deep (pixels) the outline is un-mixed from the white. Two is enough for leaves with
    a clean outline; hair-thin foliage (the cosmos, #63) is mixed with the white much further in.
    [solid]: how far from white (its darkest channel) a pixel of the outline must be to be opaque.
    [throughout]: un-mix the whole picture, not only its outline: for foliage so fine that the white
    shows through it everywhere (the cosmos' leaves). Its petals (pink, and a little round them) are
    left solid, so that their pale parts do not turn see-through.
    [white_parts]: part of the picture is white or nearly (the snowdrop's flowers, #64; the twine
    round the lavender's bunch, #67): round it, only the plain white of the background goes, not
    the pale grey of its shading, which the usual test took for background and bit out of it. The
    share of the picture, from the top, where this is done: 0.8 leaves out the foot of a plant,
    where the pale grey is its shadow on the ground; 1.0 is the whole of an icon, which has none;
    0 is not at all.
    [shadow]: the share of the picture, from the top, below which a grey shadow cast on the ground
    goes too, however dark: grey (its channels within [SHADOW_TOL] of one another) and reached
    from the background (the eggplants' shadow under the icon, #73). 0 is not at all.
    """
    v = rgb.astype(int)
    mx, mn = v.max(axis=2), v.min(axis=2)
    pale = (mn >= light) & ((mx - mn) <= tol)
    background = reconstruct(border(pale.shape) & pale, pale)
    if shadow > 0:
        grey = ((mx - mn) <= SHADOW_TOL) & (mn >= SHADOW_DARKEST)
        grey[:int(grey.shape[0] * shadow)] = False
        background = reconstruct(background, background | grey)
    if white_parts > 0:
        plain_white = (mn >= 246) & ((mx - mn) <= 8)
        strict = reconstruct(border(pale.shape) & plain_white, plain_white)
        petals = pale & ~strict
        petals[int(petals.shape[0] * white_parts):] = False
        zone = dilate(petals, 3)
        background = np.where(zone, strict, background)
    # White shut in by leaves: near-white and grey-free, in patches too wide to be a highlight or
    # a petal (they survive [pocket] steps of erosion), so that small light spots stay.
    plain = (mn >= 222) & ((mx - mn) <= 16) & ~background
    if white_parts > 0:
        # Round the flowers, only plain white shut in between the stems goes, in wide patches.
        shut_in = plain_white & ~background & zone
        background |= reconstruct(erode(shut_in, pocket + 2), shut_in)
        plain &= ~zone
    background |= reconstruct(erode(plain, pocket), plain)
    alpha = np.where(background, 0.0, 1.0)
    # The outline: mixed with the white behind it. Its alpha from how far it is from white, and
    # its colour taken back out of the mix.
    rim = ~background & dilate(background, edge)
    if throughout:
        petals = (v[..., 0] - v[..., 1] > 25) & (v[..., 2] - v[..., 1] > 0)
        rim = ~background & ~dilate(petals, 2)
    a = np.clip((255.0 - mn) / solid, 0.0, 1.0)
    alpha[rim] = a[rim]
    out = v.astype(float)
    safe = np.maximum(alpha, 1e-3)[..., None]
    unmixed = (out - 255.0 * (1.0 - alpha[..., None])) / safe
    out = np.where(rim[..., None], np.clip(unmixed, 0, 255), out)
    # Specks and wisps not joined to anything solid go too.
    solid = alpha > 0.5
    keep = reconstruct(erode(solid, 3), solid)
    alpha = np.where(dilate(keep, 1), alpha, 0.0)
    return np.dstack([out, alpha * 255.0]).astype(np.uint8)


def load_cut(name: str, **kwargs) -> np.ndarray:
    return cut_out(np.array(Image.open(SOURCE / name).convert("RGB")), **kwargs)


def paste(canvas: np.ndarray, sprite: np.ndarray, x: int, y: int) -> None:
    """[sprite] laid over [canvas] (both RGBA) with its top left at (x, y)."""
    h, w = sprite.shape[:2]
    region = canvas[y:y + h, x:x + w].astype(float)
    s = sprite.astype(float)
    a = s[:, :, 3:4] / 255.0
    region[:, :, :3] = s[:, :, :3] * a + region[:, :, :3] * (1 - a)
    region[:, :, 3:4] = 255.0 * (a + region[:, :, 3:4] / 255.0 * (1 - a))
    canvas[y:y + h, x:x + w] = region.astype(np.uint8)


def part_at(rgba: np.ndarray, x0: float, y0: float, x1: float, y1: float) -> np.ndarray:
    """The part of [rgba] inside the box (shares of the picture), cropped to what is there."""
    h, w = rgba.shape[:2]
    box = rgba[int(y0 * h):int(y1 * h), int(x0 * w):int(x1 * w)].copy()
    return crop(box)


# --- Crops -------------------------------------------------------------------------------------

def corn_stages() -> list[np.ndarray]:
    # Its seedlings cast grey shadows across the sheet: grey down to a mid tone goes as well.
    sheet = load_cut("crop_corn_stage1.png", light=140, tol=22)
    # Three seedlings of the 4 x 4 sheet, set out as the stalks of stage 3: back left, back right
    # and front middle.
    seedlings = [part_at(sheet, c / 4, r / 4, (c + 1) / 4, (r + 1) / 4) for c, r in ((1, 1), (2, 0), (1, 2))]
    sw = max(s.shape[1] for s in seedlings)
    sh = max(s.shape[0] for s in seedlings)
    canvas = np.zeros((int(sh * 1.5), int(sw * 2.2), 4), np.uint8)
    spots = [(0.0, 0.0), (1.2, 0.0), (0.6, 0.45)]  # in seedling widths and heights: back first
    for seedling, (fx, fy) in zip(seedlings, spots):
        paste(canvas, seedling, int(fx * sw), int(fy * sh * 0.5) + sh - seedling.shape[0])
    # What is left of their shadows: grey, where every seedling is green.
    rgb = canvas[:, :, :3].astype(int)
    canvas[(rgb.max(axis=2) - rgb.min(axis=2)) < 24, 3] = 0
    stage1 = crop(canvas)

    two = crop(load_cut("crop_corn_stage2.png"))
    # The right-hand stalk (the right half of the picture, by its stems), mirrored and stood in
    # front of the two, between them, as the third stalk of stage 3 stands.
    h, w = two.shape[:2]
    right = crop(two[:, int(w * 0.52):].copy())
    third = right[:, ::-1].copy()
    canvas = np.zeros((h + int(h * 0.1), w, 4), np.uint8)
    paste(canvas, two, 0, 0)
    paste(canvas, third, int(w * 0.5 - third.shape[1] * 0.45), canvas.shape[0] - third.shape[0])
    stage2 = crop(canvas)
    stage3 = crop(load_cut("crop_corn_stage3.png"))
    return [stage1, stage2, stage3]


def eggplant_stages() -> list[np.ndarray]:
    four = load_cut("crop_eggplant_stage1.png")
    # The front seedling: the plant whose stem stands lowest in the picture.
    parts = components(four[:, :, 3] > ALPHA_MIN)
    front = max(parts[:4], key=lambda p: np.where(p)[0].max())
    one = four.copy()
    one[~front, 3] = 0
    return [crop(one)] + [crop(load_cut(f"crop_eggplant_stage{n}.png")) for n in (2, 3)]


def carrot_stages() -> list[np.ndarray]:
    grown = crop(load_cut("crop_carrot_stage3.png"))
    rgb = grown[:, :, :3].astype(int)
    orange = (rgb[:, :, 0] > 170) & (rgb[:, :, 1] < 150) & (rgb[:, :, 2] < 90) & (grown[:, :, 3] > 128)
    rows = np.where(orange.sum(axis=1) > 6)[0]
    # Stage 3: the leaves and the shoulder of the root, going into the bed below it (#60). The
    # bed's edge round the root is a curve, lower at the front, and ragged (CARROT_SOIL_DIP).
    cut = rows.min() + int((rows.max() - rows.min()) * CARROT_SHOULDER)
    columns = np.where(orange[cut - 2])[0]
    left, right = columns.min(), columns.max()
    centre, half = (left + right) / 2, max(1.0, (right - left) / 2)
    width = grown.shape[1]
    x = np.arange(width)
    across = np.clip((x - centre) / half, -1.0, 1.0)
    dip = CARROT_SOIL_DIP * half * np.sqrt(1.0 - across ** 2)
    rng = np.random.default_rng(60)
    wander = np.convolve(rng.normal(0.0, 1.0, width + 8), np.ones(9) / 9, mode="valid")[:width]
    wander = wander / max(1e-6, np.abs(wander).max()) * CARROT_SOIL_WANDER
    line = (cut - CARROT_SOIL_DIP * half) + dip + wander          # the soil line, row by column
    ready = grown[: int(np.ceil(line.max())) + 1].copy()
    rows_below = np.arange(ready.shape[0])[:, None] - line[None, :]  # > 0 under the soil
    alpha = np.clip(1.0 - (rows_below + 1.5) / 3.0, 0.0, 1.0)      # a soft edge 3 px wide
    ready[:, :, 3] = (ready[:, :, 3] * alpha).astype(ready.dtype)
    stain = np.clip(1.0 + rows_below / CARROT_EARTH_ROWS, 0.0, 1.0) ** 2 * 0.7
    for c in range(3):
        channel = ready[:, :, c].astype(float)
        ready[:, :, c] = np.clip(channel + (CARROT_EARTH[c] - channel) * stain, 0, 255).astype(ready.dtype)
    # Stage 2 came in #74: its leaves, with a thin ring of the root's top at their foot. (Until
    # then it was stage 3's leaves cut off above the root.)
    return [crop(load_cut(f"crop_carrot_stage{n}.png")) for n in (1, 2)] + [crop(ready)]


def without_roots(rgba: np.ndarray, flare: float = 1.15) -> np.ndarray:
    """[rgba] cut off where roots spread out below the foot of its leaves.

    The foot is found from below: in the lowest fifth of the picture, the first row (from the top
    of that fifth down) that is more than [flare] times as wide as the bundle of leaves above it,
    which is where the roots fan out. Everything from there down goes.
    """
    h = rgba.shape[0]
    solid = rgba[:, :, 3] > 128
    def span(y: int) -> int:
        xs = np.where(solid[y])[0]
        return int(xs.max() - xs.min() + 1) if len(xs) else 0
    top = int(h * 0.8)
    bundle = float(np.median([span(y) for y in range(int(h * 0.65), top)]))
    foot = next((y for y in range(top, h) if span(y) > bundle * flare), h)
    return crop(rgba[:foot].copy())


def snowdrop_stages() -> list[np.ndarray]:
    stages = simple_stages("snowdrop", "_stage")
    return [without_roots(stages[0])] + stages[1:]


def simple_stages(name: str, suffix: str) -> list[np.ndarray]:
    return [crop(load_cut(f"crop_{name}{suffix}{n}.png", **CUT.get(name, {}))) for n in (1, 2, 3)]


def brighter_leaves(rgba: np.ndarray, gain: float) -> np.ndarray:
    """[rgba] with all but its yellow flowers [gain] times as bright, each channel alike."""
    rgb = rgba[:, :, :3].astype(float)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    yellow = (r > 140) & (g > 110) & (b < 100) & (r - b > 70)
    out = rgba.copy()
    out[:, :, :3] = np.where(yellow[..., None], rgb, np.clip(rgb * gain, 0, 255)).astype(np.uint8)
    return out


def log_width(rgba: np.ndarray) -> int:
    """How long the log in [rgba] is, in pixels: its widest row in the lowest quarter of the
    picture, below the mushrooms, which stand on its top and may reach past its ends."""
    solid = rgba[:, :, 3] > 128
    rows = np.where(solid.any(axis=1))[0]
    widths = [np.ptp(np.where(solid[y])[0]) + 1 for y in range(rows.max() - (rows.max() - rows.min()) // 4, rows.max() + 1) if solid[y].any()]
    return int(max(widths))


def log_anchor(rgba: np.ndarray) -> dict:
    """A log stands on its foot: the middle of the log, at its lowest point."""
    h, w = rgba.shape[:2]
    solid = rgba[:, :, 3] > 128
    ys = np.where(solid.any(axis=1))[0]
    low = ys.max() - (ys.max() - ys.min()) // 4
    xs = np.where(solid[low:].any(axis=0))[0]
    return {"anchorX": round(float((xs.min() + xs.max() + 1) / 2 / w), 4), "anchorY": round(float((ys.max() + 1) / h), 4), "isFlippedAllowed": True}


def to_size(rgba: np.ndarray, size) -> np.ndarray:
    h, w = rgba.shape[:2]
    if isinstance(size, tuple) and size[0] == "log":
        scale = size[1] * TILE / log_width(rgba)
    elif isinstance(size, tuple):
        scale = size[1] * TILE / w
    else:
        scale = size * TILE / h
    return resize(rgba, scale, scale)


def field_anchor(rgba: np.ndarray) -> dict:
    """A field stands on the middle of its cell: a quarter of its width above its front corner."""
    h, w = rgba.shape[:2]
    ys = np.where(rgba[:, :, 3] > ALPHA_MIN)[0]
    return {"anchorX": 0.5, "anchorY": round(float((ys.max() - w / 4) / h), 4), "isFlippedAllowed": True}


def anchor_for(crop_name: str, picture: np.ndarray, asset_id: str) -> dict | None:
    """Where [picture] stands: a field on its middle, a log on its foot, others as ANCHORS says
    (or on the foot of their stem, found by asset_packer, when it says nothing)."""
    if crop_name == "wheat":
        return field_anchor(picture)
    if all(isinstance(s, tuple) and s[0] == "log" for s in STAGES[crop_name]):
        return log_anchor(picture)
    return ANCHORS.get(asset_id)


def prepare_crops() -> dict[str, tuple[np.ndarray, dict | None]]:
    stages = {
        "corn": corn_stages(),
        "eggplant": eggplant_stages(),
        "carrot": carrot_stages(),
        "wheat": simple_stages("wheat", "_stage"),
        "tomato": simple_stages("tomato", "_"),
        "strawberry": simple_stages("strawberry", "_"),
        "tulip": [crop(load_cut(f"crop_tulip_stage{n}.png")) for n in (1, 2)] + [crop(load_cut("crop_tulip_red_stage3.png"))],
        "sunflower": simple_stages("sunflower", "_stage"),
        "mint": simple_stages("mint", "_stage"),
        "cosmos": simple_stages("cosmos", "_stage"),
        "snowdrop": snowdrop_stages(),
        "watermelon": simple_stages("watermelon", "_stage"),
        "lavender": simple_stages("lavender", "_stage"),
        "shiitake": simple_stages("shiitake", "_stage"),
        "poinsettia": simple_stages("poinsettia", "_stage"),
    }
    out: dict[str, tuple[np.ndarray, dict | None]] = {}
    for crop_name, pictures in stages.items():
        sized = [to_size(p, s) for p, s in zip(pictures, STAGES[crop_name])]
        for n, picture in enumerate(sized, start=1):
            override = anchor_for(crop_name, picture, f"plant_{crop_name}_stage{n}")
            asset_id = f"plant_{crop_name}_stage{n}"
            if asset_id in LEAF_GAIN:
                picture = brighter_leaves(picture, LEAF_GAIN[asset_id])
            out[asset_id] = (picture, override)
        if crop_name in WITHERED:
            dead = to_size(crop(load_cut(f"crop_{crop_name}_withered.png", **CUT.get(crop_name, {}))), WITHERED[crop_name])
            out[f"plant_{crop_name}_withered"] = (dead, anchor_for(crop_name, dead, f"plant_{crop_name}_withered"))
    for colour in TULIP_COLOURS:
        bloom = to_size(crop(load_cut(f"crop_tulip_{colour}_stage3.png")), STAGES["tulip"][2])
        out[f"plant_tulip_stage3_{colour}"] = (bloom, None)
    return out


# --- Icons, lawn, ground -----------------------------------------------------------------------

def prepare_icons() -> dict[str, np.ndarray]:
    out = {}
    for path in sorted(SOURCE.glob("icon_*.png")):
        # Only the crops known here: source_art also holds art for crops still to come (#66).
        if path.stem.removeprefix("icon_") not in STAGES.keys() | ICON_ONLY:
            continue
        name = path.stem.removeprefix("icon_")
        out[path.stem] = to_icon(crop(load_cut(path.name, **ICON_CUT.get(name, CUT.get(name, {})))))
    for item_id, source in ITEM_ICONS.items():
        out[f"icon_{item_id}"] = item_icon(source)
    for item_id, path in CUT_ICONS.items():
        out[f"icon_{item_id}"] = to_icon(crop(np.array(Image.open(path).convert("RGBA"))))
    out["icon_struct_windmill"] = windmill_icon()
    out["icon_tile_river"] = river_icon()
    return out


#: The ground of #74: objectId -> (slab picture, how far in from its edges the face is taken, how it
#: is made to tile). "blend": the far edge brought round and blended in, as the pond's water
#: (#54); "joints": the same, but in the middle of the blend the darker of the two wins, so that
#: stones meet at a joint instead of showing through one another; "boards": the decking is cut
#: across to a whole number of boards, so that they run on from cell to cell, and along them it
#: is not blended: a cell's edge is where its boards end, as boards do.
GROUND_TEXTURES: dict[str, tuple[str, float, str]] = {
    "ground_pebble": ("tile_pebble_iso.png", 0.05, "joints"),
    "ground_flowers": ("tile_flowers_iso.png", 0.05, "blend"),
    "floor_wood": ("tile_wood_deck_iso.png", 0.03, "boards"),
    "floor_cobblestone": ("tile_cobblestone_iso.png", 0.05, "joints"),
}

#: Pixels a side of a ground texture: one cell. About as many as a cell of the atlas (512).
GROUND_SIZE = 512

#: How much wider than [GROUND_SIZE] the face is taken, to blend its far edge in over.
GROUND_MARGIN = 88

#: How large the light and shade taken out of a ground texture is, at least (pixels): baked into
#: the slab's face, a lit corner showed once a cell as a pattern over the ground.
GROUND_EVEN_OUT = 60

#: The icons of #74's things, item id -> the picture it is cut from (the slab, for the ground).
ITEM_ICONS: dict[str, str] = {
    "struct_hut": "building_hut.png",
    "struct_well": "building_well.png",
    "deco_rock": "deco_rock.png",
    "deco_bench": "deco_bench.png",
    "deco_lantern": "deco_lantern.png",
    "deco_sprinkler": "deco_sprinkler.png",
    "ground_pebble": "tile_pebble_iso.png",
    "ground_flowers": "tile_flowers_iso.png",
    "floor_wood": "tile_wood_deck_iso.png",
    "floor_cobblestone": "tile_cobblestone_iso.png",
    # Device feedback (after #75): the rest of what the shop sells, so that no shelf mixes pictures
    # and emoji. The grass as the slab it came as, like the other ground.
    "ground_grass": "tile_grass_iso.png",
}

#: Icons cut from pictures that are already cut out (transparent), as the garden draws them: the
#: cottage and the greenhouse from their sprites, the pond from its own picture. The windmill is
#: put together from its body and its sails ([windmill_icon]).
CUT_ICONS: dict[str, Path] = {
    "struct_cottage": RAW / "struct_cottage.png",
    "floor_greenhouse": RAW / "floor_greenhouse.png",
    "deco_pond": SOURCE / "pond.png",
    # The panel of fence the garden draws for one standing alone (item_fence_wood.png is a sheet of parts).
    "struct_fence": RAW / "struct_fence.png",
}


def flat_face(name: str, size: int, inset: float) -> np.ndarray:
    """The top face of the slab [name], [inset] in from its edges, seen from straight above: RGB,
    [size] square, its x along the face's edge from its top corner to its right one (gridX)."""
    source = Image.open(SOURCE / name).convert("RGB")
    cx, cy, width, height = top_face_of(cut_out(np.array(source)))
    half_w, half_h = width / 2, height / 2
    step = (1 - 2 * inset) / size
    matrix = (half_w * step, -half_w * step, cx, half_h * step, half_h * step, cy - half_h + height * inset)
    return np.array(source.transform((size, size), Image.AFFINE, matrix, resample=Image.BICUBIC)).astype(float)


def even_out(rgb: np.ndarray, radius: float) -> np.ndarray:
    """[rgb] with its light and shade larger than [radius] taken out, its mean colour kept."""
    from PIL import ImageFilter
    blurred = np.array(Image.fromarray(rgb.clip(0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius))).astype(float)
    return rgb - blurred + rgb.reshape(-1, 3).mean(axis=0)


def tile_by_blending(rgb: np.ndarray, margin: int, joints: bool) -> np.ndarray:
    """
    [rgb] made to tile, [margin] narrower each way: along each axis the strip past its far edge is
    blended into its first pixels, weighted sin² in and keeping the grain (the difference from
    the mean scaled back up, as FlatGround.seamless). With [joints], the darker of the two takes
    over in the middle of the strip.
    """
    mean = rgb.reshape(-1, 3).mean(axis=0)
    t = (np.arange(margin) + 0.5) / margin
    own = np.sin(t * np.pi / 2) ** 2
    other = 1 - own
    norm = np.sqrt(own ** 2 + other ** 2)
    dark_share = 4 * own * other if joints else np.zeros(margin)

    def blend(a: np.ndarray, b: np.ndarray, shape: tuple) -> np.ndarray:
        o, ot, n, k = (w.reshape(shape) for w in (own, other, norm, dark_share))
        soft = mean + ((a - mean) * o + (b - mean) * ot) / n
        darker = np.where(a.mean(axis=-1, keepdims=True) < b.mean(axis=-1, keepdims=True), a, b)
        return soft * (1 - k) + darker * k

    tile = rgb.shape[0] - margin
    across = rgb[:, :tile].copy()
    across[:, :margin] = blend(rgb[:, :margin], rgb[:, tile:tile + margin], (1, margin, 1))
    out = across[:tile].copy()
    out[:margin] = blend(across[:margin], across[tile:tile + margin], (margin, 1, 1))
    return out


def tile_boards(rgb: np.ndarray) -> np.ndarray:
    """Decking whose boards run down [rgb]: cut across at its gaps to a whole number of boards, square."""
    columns = rgb.mean(axis=2).mean(axis=0)
    gap = columns < np.percentile(columns, 12)
    starts = np.where(gap[1:] & ~gap[:-1])[0] + 1
    pitch = float(np.median(np.diff(starts)))
    width = int(round(int((rgb.shape[1] - starts[0]) // pitch) * pitch))
    return rgb[:width, starts[0]:starts[0] + width]


def ground_texture(object_id: str) -> np.ndarray:
    name, inset, how = GROUND_TEXTURES[object_id]
    face = even_out(flat_face(name, GROUND_SIZE + GROUND_MARGIN, inset), GROUND_EVEN_OUT)
    if how == "boards":
        square = tile_boards(face)
        out = Image.fromarray(square.clip(0, 255).astype(np.uint8)).resize((GROUND_SIZE, GROUND_SIZE), Image.LANCZOS)
        return np.array(out)
    return tile_by_blending(face, GROUND_MARGIN, joints=how == "joints").clip(0, 255).astype(np.uint8)


def item_icon(name: str) -> np.ndarray:
    """An icon from [name] (on white), cut out of its plain white only, as the pictures of #74 are."""
    rgba = crop(cut_out(np.array(Image.open(SOURCE / name).convert("RGB")), white_parts=1.0))
    return to_icon(rgba)


def windmill_icon() -> np.ndarray:
    """The windmill's icon: its body with the sails on their hub, where the garden puts them
    (atlas_overrides.json: the body's attachX/attachY, the sails' anchor)."""
    overrides = json.loads((ROOT / "tools/atlas_overrides.json").read_text(encoding="utf-8"))
    body = np.array(Image.open(RAW / "struct_windmill.png").convert("RGBA"))
    sails = np.array(Image.open(RAW / "struct_windmill_blades.png").convert("RGBA"))
    hub_x = overrides["struct_windmill"]["attachX"] * body.shape[1]
    hub_y = overrides["struct_windmill"]["attachY"] * body.shape[0]
    sx = int(round(hub_x - overrides["struct_windmill_blades"]["anchorX"] * sails.shape[1]))
    sy = int(round(hub_y - overrides["struct_windmill_blades"]["anchorY"] * sails.shape[0]))
    left, top = min(0, sx), min(0, sy)
    width = max(body.shape[1], sx + sails.shape[1]) - left
    height = max(body.shape[0], sy + sails.shape[0]) - top
    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    canvas.alpha_composite(Image.fromarray(body), (-left, -top))
    canvas.alpha_composite(Image.fromarray(sails), (sx - left, sy - top))
    return to_icon(crop(np.array(canvas)))


def river_icon() -> np.ndarray:
    """The river's icon: a straight stretch (north to south) out of the garden's own river tiles
    (tile_river_autotile.png, made by build_terrain_assets.py: 4 x 4 joins, 128 px each, seen from
    straight above), laid down as a cell of the garden is: a diamond twice as wide as it is tall."""
    tiles = Image.open(ROOT / "app/src/main/res/drawable-nodpi/tile_river_autotile.png").convert("RGBA")
    mask = 1 | 4
    piece = tiles.crop(((mask % 4) * 128, (mask // 4) * 128, (mask % 4 + 1) * 128, (mask // 4 + 1) * 128))
    # Screen (x, y) of the 256 x 128 diamond from the tile's (u, v): x = u - v + 128, y = (u + v) / 2.
    diamond = piece.transform((256, 128), Image.AFFINE, (0.5, 1.0, -64.0, -0.5, 1.0, 64.0), resample=Image.BICUBIC)
    return to_icon(crop(np.array(diamond)))


def to_icon(rgba: np.ndarray) -> np.ndarray:
    h, w = rgba.shape[:2]
    scale = ICON_SIZE * 0.92 / max(h, w)
    small = resize(rgba, scale, scale)
    canvas = np.zeros((ICON_SIZE, ICON_SIZE, 4), np.uint8)
    sh, sw = small.shape[:2]
    paste(canvas, small, (ICON_SIZE - sw) // 2, (ICON_SIZE - sh) // 2)
    return canvas


def slab_of(path: Path) -> tuple[float, float, float]:
    """(anchorX, anchorY, topWidth) of a ground picture's top face, in shares of the picture."""
    rgb = np.array(Image.open(path).convert("RGB"))
    rgba = cut_out(rgb)
    cx, cy, width, _ = top_face_of(rgba)
    h, w = rgb.shape[:2]
    return round(float(cx / w), 3), round(float(cy / h), 3), round(float(width / w), 3)


def flat_lawn(size: int = 720, inset: float = 0.06) -> Image.Image:
    """The top face of tile_grass_iso.png seen from straight above: a square of grass."""
    source = Image.open(SOURCE / "tile_grass_iso.png").convert("RGB")
    rgba = cut_out(np.array(source))
    cx, cy, width, _ = top_face_of(rgba)
    half_w, half_h = width / 2, width / 4
    top_x, top_y = cx, cy - half_h
    # Square (i, j) -> picture: i runs from the top corner to the right one, j to the left one.
    step = (1 - 2 * inset) / size
    a, b = half_w * step, -half_w * step
    d, e = half_h * step, half_h * step
    c = top_x + (half_w - half_w) * inset
    f = top_y + (half_h + half_h) * inset
    return source.transform((size, size), Image.AFFINE, (a, b, c, d, e, f), resample=Image.BICUBIC)


def write(crops, icons) -> None:
    overrides = json.loads(OVERRIDES.read_text(encoding="utf-8"))
    for asset_id, (rgba, override) in crops.items():
        Image.fromarray(rgba, "RGBA").save(RAW / f"{asset_id}.png", optimize=True)
        if override is not None:
            overrides[asset_id] = override
        print(f"{asset_id:28s} {rgba.shape[1]}x{rgba.shape[0]}  {override or ''}")
    lines = [f'  "{k}": {json.dumps(v)}' for k, v in overrides.items()]
    OVERRIDES.write_text("{\n" + ",\n".join(lines) + "\n}\n", encoding="utf-8")
    for name, rgba in icons.items():
        Image.fromarray(rgba, "RGBA").save(ICONS / f"{name}.png", optimize=True)
        print(f"{name:28s} {ICON_SIZE}x{ICON_SIZE}")
    for object_id in GROUND_TEXTURES:
        Image.fromarray(ground_texture(object_id), "RGB").save(ICONS / f"{object_id}.png", optimize=True)
        print(f"{object_id:28s} {GROUND_SIZE}x{GROUND_SIZE} (ground)")
    for name in ("soil_wasteland", "soil_tilled", "soil_revived", "pond_center"):
        print(f"GroundSlab {name:16s} anchorX, anchorY, topWidth = {slab_of(GROUND / f'{name}.png')}")


def preview(crops, icons, folder: Path) -> None:
    """Each crop's stages side by side on a striped ground at their sizes, and the icons."""
    folder.mkdir(parents=True, exist_ok=True)
    names = sorted({k.rsplit("_", 1)[0] for k in crops})
    rows = []
    for name in names:
        ids = [k for k in crops if k.rsplit("_", 1)[0] == name]
        pics = [crops[k][0] for k in ids]
        h = max(p.shape[0] for p in pics) + 10
        row = np.zeros((h, sum(p.shape[1] + 20 for p in pics), 3), np.uint8)
        row[:] = (np.indices(row.shape[:2]).sum(axis=0) // 24 % 2)[..., None] * 40 + [90, 120, 90]
        x = 0
        for p in pics:
            a = p[:, :, 3:4] / 255.0
            y = h - p.shape[0] - 5
            sub = row[y:y + p.shape[0], x:x + p.shape[1]]
            row[y:y + p.shape[0], x:x + p.shape[1]] = (p[:, :, :3] * a + sub * (1 - a)).astype(np.uint8)
            x += p.shape[1] + 20
        rows.append(row)
        Image.fromarray(row).save(folder / f"{name}.png")
    sheet = np.zeros((ICON_SIZE, ICON_SIZE * len(icons), 3), np.uint8) + 200
    for i, rgba in enumerate(icons.values()):
        a = rgba[:, :, 3:4] / 255.0
        sub = sheet[:, i * ICON_SIZE:(i + 1) * ICON_SIZE]
        sheet[:, i * ICON_SIZE:(i + 1) * ICON_SIZE] = (rgba[:, :, :3] * a + sub * (1 - a)).astype(np.uint8)
    Image.fromarray(sheet).save(folder / "icons.png")
    flat_lawn().save(folder / "lawn_flat.png")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", type=Path, help="also write preview pictures into this folder")
    args = parser.parse_args()
    crops = prepare_crops()
    icons = prepare_icons()
    write(crops, icons)
    if args.preview:
        preview(crops, icons, args.preview)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
