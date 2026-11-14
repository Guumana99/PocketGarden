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
      - hakusai (#87): the same; stage 2 came with its roots showing, which are cut off, so that it
        stands on the foot of its leaves (hakusai_stages); stage 3 and withered are a head in a wide
        spread of outer leaves, sized by their width and stood on the foot of the head, the leaves
        lying out in front of it;
      - nanohana (#87): the same; a wide flowering clump, sized by its width from stage 2 on. Stage 3
        and withered (#93), and stage 2 with its green buds (#94), are stems tipped with spires, about as tall as wide;
        the withered one is pale, and is cut more firmly and deepened ([WITHERED_CUT], [firmed]);
      - cosmos stage 1 (#87): a young plant so pale and fine that it vanished on the soil; it is
        brought to the colour of stage 2's leaves and its strands are firmed up (deepened);
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
    keep_main_part, reconstruct, resize, top_face_of,
)

ROOT = SOURCE.parent.parent
# PNG masters; tools/webp_art.py makes the app's WebP from them (#fb-1111).
ICONS = ROOT / "art" / "png" / "res" / "drawable-nodpi"
GROUND = ROOT / "art" / "png" / "res" / "drawable"

#: Pixels a side of an icon in the bag: about three times its size on a phone.
ICON_SIZE = 256

#: Each stage's size in tiles: its height, ("w", width) for a field laid over the whole cell, or
#: ("log", width) for the shiitake's log, whose length alone is set (log_width).
STAGES: dict[str, list] = {
    "corn": [0.36, 0.7, 0.9],
    "eggplant": [0.3, 0.58, 0.78],
    "carrot": [0.38, 0.48, 0.72],
    "wheat": [("w", 0.92), ("w", 0.92), ("w", 0.92)],
    "tomato": [0.34, 0.74, 0.86],
    "strawberry": [0.3, 0.48, 0.58],
    "tulip": [0.3, 0.48, 0.62],
    "sunflower": [0.32, 0.7, 0.88],
    "mint": [0.32, 0.42, 0.62],
    "cosmos": [0.3, 0.64, 0.85],
    "snowdrop": [0.3, 0.48, 0.62],
    "watermelon": [0.3, ("w", 0.7), ("w", 0.85)],
    "lavender": [0.26, 0.42, 0.56],
    "shiitake": [("log", 0.5), ("log", 0.5), ("log", 0.5)],
    "poinsettia": [0.3, 0.55, 0.7],
    "hakusai": [0.3, 0.46, ("w", 0.76)],
    "nanohana": [0.3, ("w", 0.55), ("w", 0.8)],
    # The four of the book's 60 (#book60): stage 1 of each is made from its stage 2.
    "kabu": [0.3, 0.48, 0.62],
    "satsumaimo": [0.28, ("w", 0.56), ("w", 0.7)],
    "suisen": [0.3, 0.48, 0.6],
    "habotan": [0.28, ("w", 0.46), ("w", 0.6)],
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
    "hakusai": {"white_parts": 1.0},
    "nanohana": {"throughout": True, "solid": 100.0},
    # The turnip's root is white: the usual cut bit its shaded side away (#book60).
    "kabu": {"white_parts": 1.0},
    # The ornamental cabbage's heart is white leaves shut in by green ones: none of it is a pocket
    # of background (its own background is a pale grey, taken out from the edge as usual).
    "habotan": {"pocket": 60},
}

#: The same for a withered picture, where it differs from the plant's own (#93): the nanohana's is
#: so pale that the plant's cut turned its leaves see-through. FIRMED: how its colours are then
#: deepened (gain, contrast, the power its alpha is raised to), for its edges to hold on pale ground.
WITHERED_CUT: dict[str, dict] = {"nanohana": {"throughout": True, "solid": 45.0}}
FIRMED: dict[str, tuple[float, float, float]] = {"nanohana": (0.8, 1.2, 0.55)}

#: The same for an icon, where it is cut out differently from its plant: the lavender's bunch is
#: tied with pale twine, which the white was taken out of (#67). The daikon's root is white, and
#: the usual cut bit its shaded side away (#72). The eggplants (#73) lay on a grey shadow that was
#: left under them, and the watermelon's pale, lit side was bitten into, leaving white flecks.
ICON_CUT: dict[str, dict] = {
    "lavender": {"white_parts": 1.0},
    "daikon": {"white_parts": 1.0},
    "eggplant": {"shadow": 0.7},
    "watermelon": {"white_parts": 1.0},
    "nanohana": {"white_parts": 1.0},
    # FB1114: the turnip's root is white: only plain white goes, as with the daikon. The sweet
    # potatoes cast a faint grey shadow under them, which goes as the eggplants' does.
    "kabu": {"white_parts": 1.0},
    "satsumaimo": {"shadow": 0.45},
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

#: How [deepened] takes a picture: contrast about its middle colour, and the power its alpha is
#: raised to (below 1, half see-through strands come up).
DEEPEN_CONTRAST = 1.25
DEEPEN_FIRMNESS = 0.6

#: How much of the foot of the hakusai's stage 2 is its roots: thin hairs that fan out no wider
#: than the stalks, so that [without_roots] does not see them.
HAKUSAI_ROOTS = 0.04

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
    "plant_hakusai_stage3": {"anchorX": 0.527, "anchorY": 0.668, "isFlippedAllowed": True},
    "plant_hakusai_withered": {"anchorX": 0.527, "anchorY": 0.668, "isFlippedAllowed": True},
}

#: How much of the carrot's root shows above its bed at stage 3 (#60), as a share of the root,
#: at the front of the root (where the soil line is lowest). 0.22 left it all but buried beside the
#: daikon, which shows the whole of its root (device feedback, 2026-11-02).
CARROT_SHOULDER = 0.6

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
    "lavender": 0.56,
    "shiitake": ("log", 0.5),
    "poinsettia": 0.7,
    "hakusai": ("w", 0.76),
    "nanohana": ("w", 0.8),
    "kabu": 0.62,
    "satsumaimo": ("w", 0.7),
    "suisen": 0.6,
    "habotan": ("w", 0.6),
}

#: The narcissus' flowers (#book60): yellow is its plain stage 3; the others are made from it in
#: code, the way the tulip's colours are drawn ([suisen_colour]): white petals round a yellow cup
#: (the Japanese narcissus), and a yellow flower with an orange cup (the rare one).
SUISEN_COLOURS = ["white", "orange"]

#: Each narcissus' cup on crop_suisen_yellow_stage3.png (source pixels): ellipses (centre x,
#: centre y, half width, half height) round it, read off the picture. A cup seen from the side
#: is two: its flared mouth and its tube. Inside them, only the deep yellow is the cup
#: ([SUISEN_CUP_RATIO]); the petals lying over one stay petals.
SUISEN_CUPS = [
    (532, 258, 60, 55),   # the top flower, facing out
    (522, 472, 53, 55),   # the front flower, facing out
    (387, 333, 46, 44),   # the left one, three-quarter
    (275, 432, 29, 52), (325, 432, 35, 34),   # the left one seen from the side: mouth and tube
    (745, 391, 35, 56), (700, 388, 34, 34),   # the right one seen from the side
    (657, 267, 28, 30),   # the small one at the back right
]

#: How much less green than red the cup is, at most (green / red): the petals are paler, nearer 0.88.
SUISEN_CUP_RATIO = 0.87

#: The white of the Japanese narcissus' petals at their lightest, a little cream so that it never
#: melts into the snow or a pale path; shaded as the yellow ones were, a little more deeply.
SUISEN_WHITE = (244.0, 240.0, 226.0)
SUISEN_WHITE_SHADE = 1.3

#: FB1114: how far up the narcissus' bunch (a share of its height) the pale foot of its stems is
#: brought towards the leaves' green, and how far at most ([greener_foot]).
SUISEN_FOOT = 0.3
SUISEN_FOOT_GREEN = 0.6

#: How the rare cup's orange is made from its yellow: green and blue scaled down.
SUISEN_ORANGE = (1.0, 0.58, 0.36)

#: How far below the turnip's widest row its root tip is cut off: where the root is still this
#: share of that width ([without_tail]).
KABU_TAIL = 0.24

#: FB1114: the turnip stood on the bed like a white ball set on it, unlike the other crops. Its root
#: is cut a little below its widest row ([KABU_BURIED_BELOW] of its upper half's height under it),
#: along the near half of the ring where root meets earth, lower at the front ([KABU_SOIL_DIP] of
#: its half-width), and earth is drawn heaped round the cut ([buried_turnip]): a low mound
#: [KABU_HEAP_WIDTH] times as wide as the root and [KABU_HEAP_HEIGHT] of its half-width high, with
#: clods on it, in the bed's own colours (SoilPalette: DARK, RIDGE, DRY, EDGE).
KABU_BURIED_BELOW = -0.08
KABU_SOIL_DIP = 0.26
KABU_HEAP_WIDTH = 1.22
KABU_HEAP_HEIGHT = 0.42
KABU_EARTH_DARK = (58, 48, 41)
KABU_EARTH_RIDGE = (86, 72, 64)
KABU_EARTH_DRY = (110, 97, 88)
KABU_EARTH_EDGE = (35, 28, 24)

#: Where each turnip picture stands (anchor fractions), worked out as it is buried.
KABU_ANCHORS: dict[str, dict] = {}

#: FB1114: how many steps of erosion (source pixels) a hole shut in by a crop's leaves may outlast
#: and still be a speck of glare cut out or a fleck of the background left in, not a gap between
#: leaves ([without_specks]).
SPECK_WIDTH = 9

#: The withered turnip's root came grey, as dull as a stone: lifted ([KABU_ROOT_GAIN]) and warmed
#: a little ([KABU_ROOT_WARM], added to red, green and blue).
KABU_ROOT_GAIN = 1.12
KABU_ROOT_WARM = (10.0, 7.0, -6.0)

#: How far the ornamental cabbage's stem is cut off: where the plant is still this share of its
#: widest, it is the foot of its leaves ([without_stem]).
HABOTAN_STEM = 0.3

#: The sweet potato's leaves are a little grey beside the other crops': their colour away from grey
#: is raised by this much.
SATSUMAIMO_LEAF_SATURATION = 1.25


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


def hakusai_stages() -> list[np.ndarray]:
    stages = simple_stages("hakusai", "_stage")
    # The seedling's shadow left a few specks beside its foot.
    grown = stages[1]
    return [crop(keep_main_part(stages[0])), crop(grown[:int(grown.shape[0] * (1 - HAKUSAI_ROOTS))].copy()), stages[2]]


def deepened(rgba: np.ndarray, like: np.ndarray) -> np.ndarray:
    """
    [rgba], pale and fine, brought to the colour of [like]'s leaves: each channel scaled until its
    median meets theirs, with the contrast about that median raised, and its see-through strands
    firmed up, so that it keeps its shape on dark soil and on light ground alike.
    """
    solid = rgba[:, :, 3] > 128
    leaves = like[:, :, 3] > 200
    own = np.median(rgba[:, :, :3][solid], axis=0)
    target = np.median(like[:, :, :3][leaves], axis=0)
    rgb = rgba[:, :, :3].astype(float) * target / np.maximum(own, 1.0)
    rgb = target + (rgb - target) * DEEPEN_CONTRAST
    out = rgba.copy()
    out[:, :, :3] = np.clip(rgb, 0, 255).astype(np.uint8)
    out[:, :, 3] = (255.0 * (rgba[:, :, 3] / 255.0) ** DEEPEN_FIRMNESS).astype(np.uint8)
    return out


def firmed(rgba: np.ndarray, gain: float, contrast: float, power: float) -> np.ndarray:
    """[rgba] darker and with more contrast, and its half see-through parts firmed up."""
    out = rgba.astype(float)
    middle = out[:, :, :3][out[:, :, 3] > 128].mean(axis=0)
    out[:, :, :3] = np.clip((middle + (out[:, :, :3] - middle) * contrast) * gain, 0, 255)
    out[:, :, 3] = 255.0 * (out[:, :, 3] / 255.0) ** power
    return out.astype(np.uint8)


def cosmos_stages() -> list[np.ndarray]:
    stages = simple_stages("cosmos", "_stage")
    return [deepened(stages[0], stages[1])] + stages[1:]


def snowdrop_stages() -> list[np.ndarray]:
    stages = simple_stages("snowdrop", "_stage")
    return [without_roots(stages[0])] + stages[1:]


def faded_foot(rgba: np.ndarray, rows: int = 3) -> np.ndarray:
    """[rgba] with its last [rows] rows fading out, so that a cut across it has no hard edge."""
    out = rgba.copy()
    for i in range(1, rows + 1):
        if i <= out.shape[0]:
            out[-i, :, 3] = (out[-i, :, 3].astype(float) * i / (rows + 1)).astype(np.uint8)
    return out


def row_widths(rgba: np.ndarray) -> np.ndarray:
    solid = rgba[:, :, 3] > 128
    return np.array([(np.ptp(np.where(r)[0]) + 1) if r.any() else 0 for r in solid])


def without_tail(rgba: np.ndarray, share: float = KABU_TAIL) -> np.ndarray:
    """[rgba] (a turnip) cut off below its root: the thin tip under it goes, and it stands on the
    foot of the round root, as the daikon and the carrot stand in their beds (#book60)."""
    widths = row_widths(rgba)
    h = len(widths)
    widest = int(np.argmax(widths[int(h * 0.55):])) + int(h * 0.55)
    below = np.where(widths[widest:] >= widths[widest] * share)[0]
    foot = widest + int(below.max()) + 1
    return crop(faded_foot(rgba[:foot].copy()))


def turnip_neck(rgba: np.ndarray) -> int:
    """The row where the turnip's leaf stalks meet its root: the narrowest above the root's widest."""
    widths = row_widths(rgba)
    h = len(widths)
    widest = int(np.argmax(widths[int(h * 0.55):])) + int(h * 0.55)
    top = int(h * 0.5)
    return top + int(np.argmin(widths[top:widest]))


def without_specks(rgba: np.ndarray, source: np.ndarray, most: int = SPECK_WIDTH) -> np.ndarray:
    """
    [rgba] (cut from [source], the same size) without the little holes in it (FB1114): a speck of
    the leaves' bright glare that the cut took for background, or a fleck of the background's white
    left shut in between leaves, looked like a hole in a leaf. What is shut in all round by the
    picture and too narrow to outlast [most] steps of erosion is given back its own colour from [source] if it was
    a leaf's glare (not plain white), or the colour of the leaf round it if it was the background.
    Wider gaps between leaves stay open, as they are.
    """
    alpha = rgba[:, :, 3]
    v = source[:, :, :3].astype(int)
    mx, mn = v.max(axis=2), v.min(axis=2)
    plain_white = (mn >= 236) & ((mx - mn) <= 10)
    hole = (alpha < 128) | (plain_white & (alpha >= 128))
    # The background outside reaches in by ways wider than a speck's thread: the leaves' edges are
    # frayed with see-through pixels, through which a speck near one would count as outside.
    open_out = erode(hole, 2)
    outside = dilate(reconstruct(border(hole.shape) & open_out, open_out), 3) & hole
    shut_in = hole & ~outside
    # Small: none of it outlasts [most] steps of erosion (a speck, not a gap between leaves).
    specks = shut_in & ~reconstruct(erode(shut_in, most), shut_in)
    if not specks.any():
        return rgba
    out = rgba.copy()
    glare = specks & ~plain_white
    out[glare, :3] = source[glare, :3]
    # The background's white: the colour of the leaf round it, spread in from its edge.
    fill = specks & plain_white
    if fill.any():
        rgb = out[:, :, :3].astype(float)
        known = ~specks & (alpha >= 128)
        known |= glare
        for _ in range(40):
            if not (fill & ~known).any():
                break
            total = np.zeros_like(rgb)
            count = np.zeros(rgb.shape[:2])
            for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, 1), (-1, 1), (1, -1)):
                k = np.roll(np.roll(known, dy, 0), dx, 1)
                c = np.roll(np.roll(rgb, dy, 0), dx, 1)
                total += c * k[..., None]
                count += k
            new = fill & ~known & (count > 0)
            rgb[new] = total[new] / count[new][:, None]
            known = known | new
        out[fill, :3] = np.clip(rgb[fill], 0, 255).astype(np.uint8)
    out[specks, 3] = 255
    return out


def load_cut_clean(name: str, **kwargs) -> np.ndarray:
    """[load_cut] without the little holes in the leaves ([without_specks])."""
    source = np.array(Image.open(SOURCE / name).convert("RGB"))
    return without_specks(cut_out(source, **kwargs), source)


def buried_turnip(rgba: np.ndarray, asset_id: str, seed: int) -> np.ndarray:
    """
    The turnip [rgba] (cropped) with the lower part of its root in the bed (FB1114): cut along the
    near half of a ring a little below its widest row, stained with earth just above the cut, and a
    low mound of earth with clods heaped round it. Its anchor, the middle of the mound's foot, goes
    into [KABU_ANCHORS] under [asset_id].
    """
    # The root: its pale body (white, or the withered one's buff), not the green or straw of the leaves.
    v = rgba[:, :, :3].astype(int)
    solid = rgba[:, :, 3] > 128
    root = solid & (v.min(axis=2) > 120) & ((v.max(axis=2) - v.min(axis=2)) < 40)
    root[: int(rgba.shape[0] * 0.4)] = False
    root = reconstruct(erode(root, 3), root)
    counts = root.sum(axis=1)
    widest = int(np.argmax(counts))
    # Up from its widest row while the root is still wide: not on up the pale stalks.
    neck = widest
    while neck > 0 and counts[neck - 1] > counts.max() * 0.45:
        neck -= 1
    cut = min(widest + int((widest - neck) * KABU_BURIED_BELOW), rgba.shape[0] - 1)
    columns = np.where(root[cut])[0]
    left, right = columns.min(), columns.max()
    centre, half = (left + right) / 2, max(1.0, (right - left) / 2)
    h, w = rgba.shape[:2]
    rng = np.random.default_rng(seed)
    x = np.arange(w)
    across = np.clip((x - centre) / half, -1.0, 1.0)
    wander = np.convolve(rng.normal(0.0, 1.0, w + 30), np.ones(31) / 31, mode="valid")[:w]
    wander = wander / max(1e-6, np.abs(wander).max()) * max(1.0, half * 0.02)
    line = (cut - KABU_SOIL_DIP * half) + KABU_SOIL_DIP * half * np.sqrt(1.0 - across ** 2) + wander
    ready = rgba[: int(np.ceil(line.max())) + 1].copy()
    rows_below = np.arange(ready.shape[0])[:, None] - line[None, :]
    ready[:, :, 3] = (ready[:, :, 3] * np.clip(1.0 - (rows_below + 1.5) / 3.0, 0.0, 1.0)).astype(np.uint8)
    stain = np.clip(1.0 + rows_below / max(3.0, half * 0.08), 0.0, 1.0) ** 2 * 0.45
    for c in range(3):
        channel = ready[:, :, c].astype(float)
        ready[:, :, c] = np.clip(channel + (KABU_EARTH_DARK[c] - channel) * stain, 0, 255).astype(np.uint8)

    # The mound, on a canvas wide and deep enough for it.
    rx, ry = half * KABU_HEAP_WIDTH, half * KABU_HEAP_HEIGHT
    cy = cut - KABU_SOIL_DIP * half * 0.35
    pad_x = int(max(0.0, rx - centre, rx - (w - 1 - centre))) + 4
    height = int(np.ceil(cy + ry)) + 4
    canvas = np.zeros((max(height, ready.shape[0]), w + 2 * pad_x, 4), np.uint8)
    canvas[: ready.shape[0], pad_x: pad_x + w] = ready
    ch, cw = canvas.shape[:2]
    cx = centre + pad_x
    yy, xx = np.mgrid[0:ch, 0:cw].astype(float)
    # A ragged oval: its outline wanders a little, as heaped earth does.
    angle = np.arctan2(yy - cy, (xx - cx) * ry / rx)
    ragged = 1.0 + 0.06 * np.sin(angle * 7 + seed) + 0.04 * np.sin(angle * 13 + 2 * seed)
    d = np.sqrt(((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2) / ragged
    heap = np.clip((1.0 - d) * min(rx, ry) / 1.5, 0.0, 1.0)
    # Over the root only in front of it: below the soil line, and a little over the cut.
    line_c = np.full(cw, -1e9)
    root_cols = solid[: ready.shape[0]].any(axis=0) & (np.abs(across) < 1.0)
    line_c[pad_x: pad_x + w] = np.where(root_cols, line, -1e9)
    heap = heap * np.clip((yy - (line_c[None, :] - 2.0)) / 2.0, 0.0, 1.0)
    # Lit from above: drier on top, damp and dark at its foot, with a grain of crumbs.
    t = np.clip((yy - (cy - ry)) / (2 * ry), 0.0, 1.0)
    colour = np.empty((ch, cw, 3))
    for c in range(3):
        top = KABU_EARTH_RIDGE[c] * 0.6 + KABU_EARTH_DRY[c] * 0.4
        colour[..., c] = top + (KABU_EARTH_DARK[c] - top) * t ** 0.8
    grain = rng.normal(0.0, 1.0, (ch, cw))
    grain = np.where(grain > 1.6, 18.0, np.where(grain < -1.6, -16.0, grain * 3.0))
    colour += grain[..., None]
    # Clods: little lumps on the mound, lit on top, a dark crease under each.
    ridge, dry, edge = np.array(KABU_EARTH_RIDGE, float), np.array(KABU_EARTH_DRY, float), np.array(KABU_EARTH_EDGE, float)
    for _ in range(6 + int(half / 20)):
        a = rng.uniform(-np.pi * 0.95, np.pi * 0.95)
        r = rng.uniform(0.55, 1.0)
        px, py = cx + np.sin(a) * rx * r, cy + abs(np.cos(a)) * ry * r * 0.9 - ry * 0.15
        size = max(2.0, half * rng.uniform(0.035, 0.07))
        front = yy >= line_c[None, :] - 1
        dd = np.sqrt(((xx - px) / size) ** 2 + ((yy - py) / (size * 0.75)) ** 2)
        lump = np.clip((1.0 - dd) * size / 1.2, 0.0, 1.0) * front
        under = np.clip((1.0 - np.sqrt(((xx - px) / size) ** 2 + ((yy - py - size * 0.5) / (size * 0.5)) ** 2)) * 2, 0, 1) * (1 - lump) * (heap > 0) * front
        light = np.clip((py - yy) / size + 0.3, 0.0, 1.0)
        tone = ridge + (dry - ridge) * light[..., None] * rng.uniform(0.3, 0.7)
        colour = colour * (1 - lump[..., None]) + tone * lump[..., None]
        colour = colour * (1 - 0.5 * under[..., None]) + edge * 0.5 * under[..., None]
        heap = np.maximum(heap, lump)
    base = canvas.astype(float)
    a = heap[..., None]
    base_a = base[:, :, 3:4] / 255.0
    out_a = a + base_a * (1 - a)
    rgb = (colour * a + base[:, :, :3] * base_a * (1 - a)) / np.maximum(out_a, 1e-6)
    out = np.concatenate([np.clip(rgb, 0, 255), out_a * 255.0], axis=2).astype(np.uint8)
    seen = np.where(out[:, :, 3] > 8)
    y0, y1, x0, x1 = seen[0].min(), seen[0].max() + 1, seen[1].min(), seen[1].max() + 1
    out = out[y0:y1, x0:x1]
    foot = min(cy + ry * 0.92, y1 - 1) - y0
    KABU_ANCHORS[asset_id] = {"anchorX": round(float((cx - x0) / out.shape[1]), 4), "anchorY": round(float(foot / out.shape[0]), 4), "isFlippedAllowed": True}
    return out


def kabu_sprout(rgba: np.ndarray) -> np.ndarray:
    """Stage 1 of the turnip: its stage 2's leaves, without the root under them (a sprout)."""
    return crop(faded_foot(rgba[:turnip_neck(rgba) + 2].copy(), rows=4))


def kabu_withered(rgba: np.ndarray) -> np.ndarray:
    """The withered turnip: its root lifted and warmed, so that it is not read as a stone, and
    buried as the growing one is (FB1114)."""
    out = rgba.astype(float)
    neck = turnip_neck(rgba)
    rgb = out[neck:, :, :3]
    grey = (rgb.max(axis=2) - rgb.min(axis=2)) < 30
    lifted = np.clip(rgb * KABU_ROOT_GAIN + np.array(KABU_ROOT_WARM), 0, 255)
    rgb[grey] = lifted[grey]
    return buried_turnip(out.astype(np.uint8), "plant_kabu_withered", seed=13)


def without_stem(rgba: np.ndarray, share: float = HABOTAN_STEM) -> np.ndarray:
    """[rgba] (an ornamental cabbage) without the stem under its leaves: on its stem it read as a
    ball on a stick, a little tree. It stands on the foot of its leaves."""
    widths = row_widths(rgba)
    rows = np.where(widths >= widths.max() * share)[0]
    return crop(faded_foot(rgba[:int(rows.max()) + 1].copy()))


def without_earth(rgba: np.ndarray, withered: bool = False) -> np.ndarray:
    """
    The sweet potato without the mound of earth it came on (#book60): only its leaves, their
    stems and its potatoes stay, standing on the bed as the daikon does. Earth is what is neither
    green, nor the purple-red of the stems and potatoes, in the lower part of the picture (for a
    withered one, whose leaves are as brown as earth, what is darker than its pale leaves).
    The pale shadow on the right of the mound went with the white.
    """
    v = rgba[:, :, :3].astype(int)
    r, g, b = v[..., 0], v[..., 1], v[..., 2]
    seen = rgba[:, :, 3] > 0
    if withered:
        # Its pale leaves, not the grey-white shadow left on the right of the mound.
        pale_grey = (v.max(axis=2) - v.min(axis=2) < 28) & (v.min(axis=2) > 170)
        keep = seen & (v.mean(axis=2) > 120) & ~pale_grey
    else:
        green = (g > r + 5) & (g >= b)
        red = (r > g + 35) & (r > b + 10) & (r > 90)
        stems = (r > g) & (b > g) & (r - g > 15)
        keep = seen & (green | red | stems)
    earth = seen & ~keep
    earth[:int(rgba.shape[0] * 0.45)] = False
    # FB1114: only the mound itself, a wide mass of earth: a leaf's pale glare or a dark mark on a
    # potato below the middle of the picture was taken for earth and cut out, a hole in it.
    earth = reconstruct(erode(earth, 4), earth)
    # And the crumbs of it left at the plant's edge, out in the open where the mound was.
    loose = seen & ~keep & ~earth
    loose[:int(rgba.shape[0] * 0.45)] = False
    earth |= reconstruct(dilate(~seen | earth, 1) & loose & (v.mean(axis=2) < 110), loose & (v.mean(axis=2) < 110))
    plant = reconstruct(keep, seen & ~earth)
    out = rgba.copy()
    out[~plant, 3] = 0
    return crop(keep_main_part(out))


def with_earth_flattened(rgba: np.ndarray, darken: float = 0.62, squash: float = 0.55) -> np.ndarray:
    """The other way tried for the sweet potato (#book60, for the report only): its mound kept,
    darkened to the bed's earth and squashed lower."""
    h, w = rgba.shape[:2]
    v = rgba[:, :, :3].astype(int)
    r, g, b = v[..., 0], v[..., 1], v[..., 2]
    plant = ((g > r + 5) & (g >= b)) | ((r > g + 35) & (r > b + 10) & (r > 90)) | ((r > g) & (b > g) & (r - g > 15))
    earth = (rgba[:, :, 3] > 0) & ~dilate(plant, 1)
    earth[:int(h * 0.45)] = False
    out = rgba.astype(float)
    out[earth, :3] *= darken
    out = out.astype(np.uint8)
    rows = np.where(earth.sum(axis=1) > w * 0.2)[0]
    top, bottom = rows.min(), np.where(out[:, :, 3].any(axis=1))[0].max()
    band = np.array(Image.fromarray(out[top:bottom + 1]).resize((w, max(1, int((bottom + 1 - top) * squash))), Image.LANCZOS))
    return crop(np.concatenate([out[:top], band], axis=0))


def livelier(rgba: np.ndarray, factor: float) -> np.ndarray:
    """[rgba] with the colour of its green parts [factor] times as far from grey."""
    rgb = rgba[:, :, :3].astype(float)
    grey = rgb.mean(axis=2, keepdims=True)
    green = (rgb[..., 1] > rgb[..., 0]) & (rgb[..., 1] >= rgb[..., 2])
    out = rgba.copy()
    out[:, :, :3] = np.where(green[..., None], np.clip(grey + (rgb - grey) * factor, 0, 255), rgb).astype(np.uint8)
    return out


def satsumaimo_stages() -> list[np.ndarray]:
    two, three = (livelier(without_earth(crop(load_cut_clean(f"crop_satsumaimo_stage{n}.png"))), SATSUMAIMO_LEAF_SATURATION) for n in (2, 3))
    return [two.copy(), two, three]


def kabu_stages() -> list[np.ndarray]:
    two, three = (crop(load_cut_clean(f"crop_kabu_stage{n}.png", **CUT["kabu"])) for n in (2, 3))
    return [kabu_sprout(two), buried_turnip(two, "plant_kabu_stage2", seed=11), buried_turnip(three, "plant_kabu_stage3", seed=12)]


def habotan_stages() -> list[np.ndarray]:
    two, three = (without_stem(crop(load_cut(f"crop_habotan_stage{n}.png", **CUT["habotan"]))) for n in (2, 3))
    return [two.copy(), two, three]


def suisen_flower_masks(rgba: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """On the narcissus' stage 3 (uncropped): how much each pixel is petal, and how much cup, 0 to 1."""
    from PIL import ImageFilter
    h, w = rgba.shape[:2]
    rgb = rgba[:, :, :3].astype(float)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    flower = (rgba[:, :, 3] > 0) & (r > 110) & (r - b > 60) & (r >= g * 0.92)
    flower[int(h * 0.6):] = False   # the yellow-green foot of the stems is no flower
    yy, xx = np.mgrid[0:h, 0:w]
    near_cup = np.zeros((h, w), bool)
    for cx, cy, rx, ry in SUISEN_CUPS:
        near_cup |= ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 <= 1.0
    ratio = np.where(flower, g / np.maximum(r, 1.0), 1.0)
    smooth = np.array(Image.fromarray((ratio * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2))).astype(float) / 255
    cup = near_cup & flower & (smooth < SUISEN_CUP_RATIO)
    cup = dilate(erode(cup, 1), 1)

    def soft(mask: np.ndarray) -> np.ndarray:
        return np.array(Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2))).astype(float) / 255

    cup_share = soft(cup) * flower
    return flower * (1.0 - cup_share), cup_share


def suisen_colour(rgba: np.ndarray, colour: str) -> np.ndarray:
    """The narcissus' stage 3 (uncropped, yellow) in [colour] (SUISEN_COLOURS)."""
    petals, cup = suisen_flower_masks(rgba)
    out = rgba.astype(float)
    rgb = out[:, :, :3]
    if colour == "white":
        lum = rgb.mean(axis=2)
        top = np.percentile(lum[petals > 0.8], 97)
        shade = np.clip(lum / top, 0.0, 1.0) ** SUISEN_WHITE_SHADE
        white = shade[..., None] * np.array(SUISEN_WHITE)
        rgb[:] = rgb * (1 - petals[..., None]) + white * petals[..., None]
    elif colour == "orange":
        orange = rgb * np.array(SUISEN_ORANGE)
        rgb[:] = rgb * (1 - cup[..., None]) + orange * cup[..., None]
    return out.clip(0, 255).astype(np.uint8)


def greener_foot(rgba: np.ndarray, share: float = SUISEN_FOOT, strength: float = SUISEN_FOOT_GREEN) -> np.ndarray:
    """
    The narcissus' bunch with the pale foot of its stems brought towards the green of its leaves
    (FB1114): a whitish yellow-green bundle at the bottom [share] of the picture, it looked like a
    bunch of leeks or cut flowers. Pale pixels there go [strength] of the way (more lower down) to
    the leaves' own colour at their own brightness; the shading stays.
    """
    out = rgba.astype(float)
    rgb = out[:, :, :3]
    h = rgba.shape[0]
    seen = rgba[:, :, 3] > 128
    lum = rgb.mean(axis=2)
    middle = seen.copy()
    middle[: int(h * 0.45)] = False
    middle[int(h * (1 - share)):] = False
    leafy = middle & (rgb[..., 2] >= rgb[..., 0]) & (lum < 160)
    leaf = np.median(rgb[leafy], axis=0) if leafy.sum() > 50 else np.array([92.0, 122.0, 118.0])
    top = int(h * (1 - share))
    ramp = np.zeros(h)
    ramp[top:] = np.linspace(0.4, 1.0, h - top)
    pale = np.clip((lum - 90.0) / 60.0, 0.0, 1.0) * (rgb[..., 1] >= rgb[..., 2] - 4)
    t = strength * ramp[:, None] * pale * seen
    target = leaf[None, None, :] * (lum / max(leaf.mean(), 1.0))[..., None]
    rgb[:] = rgb * (1 - t[..., None]) + np.clip(target, 0, 255) * t[..., None]
    return out.clip(0, 255).astype(np.uint8)


def suisen_stages() -> list[np.ndarray]:
    two = greener_foot(crop(load_cut("crop_suisen_stage2.png")))
    return [two.copy(), two, greener_foot(crop(load_cut("crop_suisen_yellow_stage3.png")))]


#: How each new crop's withered picture is made, where the plain cut does not do.
WITHERED_MAKERS = {
    "kabu": lambda: kabu_withered(crop(load_cut_clean("crop_kabu_withered.png", **CUT["kabu"]))),
    "satsumaimo": lambda: without_earth(crop(load_cut("crop_satsumaimo_withered.png")), withered=True),
    "habotan": lambda: without_stem(crop(load_cut("crop_habotan_withered.png", **CUT["habotan"]))),
}


def stage_icons() -> dict[str, np.ndarray]:
    """
    The icons of the crops of #book60 that came with none, cut from their stage 3: the narcissus'
    bunch with its flowers and the ornamental cabbage's head. (The turnip's and the sweet potato's
    came as pictures of their own in FB1114, icon_kabu.png and icon_satsumaimo.png.)
    """
    stages = {"suisen": suisen_stages()[2], "habotan": habotan_stages()[2]}
    return {f"icon_{name}": to_icon(picture) for name, picture in stages.items()}


def simple_stages(name: str, suffix: str) -> list[np.ndarray]:
    return [crop(load_cut(f"crop_{name}{suffix}{n}.png", **CUT.get(name, {}))) for n in (1, 2, 3)]


def without_cast_shadow(rgba: np.ndarray, share: float = 0.5) -> np.ndarray:
    """[rgba] without the bluish shadow its stems cast on the white ground (the sunflower's, left as
    pale spikes at their feet), below the top [share] of the picture: nothing that grows is bluer
    than it is green."""
    out = rgba.copy()
    r, g, b = (rgba[:, :, c].astype(int) for c in range(3))
    shadow = (b >= g) & (b > r) & (rgba[:, :, 3] > 0)
    shadow[:int(shadow.shape[0] * share)] = False
    out[dilate(shadow, 1), 3] = 0
    return crop(out)


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
    return KABU_ANCHORS.get(asset_id) or ANCHORS.get(asset_id)


def prepare_crops() -> dict[str, tuple[np.ndarray, dict | None]]:
    stages = {
        "corn": corn_stages(),
        "eggplant": eggplant_stages(),
        "carrot": carrot_stages(),
        "wheat": simple_stages("wheat", "_stage"),
        "tomato": simple_stages("tomato", "_"),
        "strawberry": simple_stages("strawberry", "_"),
        "tulip": [crop(load_cut(f"crop_tulip_stage{n}.png")) for n in (1, 2)] + [crop(load_cut("crop_tulip_red_stage3.png"))],
        "sunflower": [without_cast_shadow(p) for p in simple_stages("sunflower", "_stage")],
        "mint": simple_stages("mint", "_stage"),
        "cosmos": cosmos_stages(),
        "snowdrop": snowdrop_stages(),
        "watermelon": simple_stages("watermelon", "_stage"),
        "lavender": simple_stages("lavender", "_stage"),
        "shiitake": simple_stages("shiitake", "_stage"),
        "poinsettia": simple_stages("poinsettia", "_stage"),
        "hakusai": hakusai_stages(),
        "nanohana": simple_stages("nanohana", "_stage"),
        "kabu": kabu_stages(),
        "satsumaimo": satsumaimo_stages(),
        "suisen": suisen_stages(),
        "habotan": habotan_stages(),
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
        if crop_name in WITHERED_MAKERS:
            dead = to_size(WITHERED_MAKERS[crop_name](), WITHERED[crop_name])
            out[f"plant_{crop_name}_withered"] = (dead, anchor_for(crop_name, dead, f"plant_{crop_name}_withered"))
        elif crop_name in WITHERED:
            cut = crop(load_cut(f"crop_{crop_name}_withered.png", **WITHERED_CUT.get(crop_name, CUT.get(crop_name, {}))))
            dead = to_size(firmed(cut, *FIRMED[crop_name]) if crop_name in FIRMED else cut, WITHERED[crop_name])
            out[f"plant_{crop_name}_withered"] = (dead, anchor_for(crop_name, dead, f"plant_{crop_name}_withered"))
    for colour in TULIP_COLOURS:
        bloom = to_size(crop(load_cut(f"crop_tulip_{colour}_stage3.png")), STAGES["tulip"][2])
        out[f"plant_tulip_stage3_{colour}"] = (bloom, None)
    yellow = load_cut("crop_suisen_yellow_stage3.png")
    for colour in SUISEN_COLOURS:
        bloom = to_size(greener_foot(crop(suisen_colour(yellow, colour))), STAGES["suisen"][2])
        out[f"plant_suisen_stage3_{colour}"] = (bloom, None)
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
    out.update(stage_icons())
    out["icon_ground_moss"] = moss_icon()
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
    # #book60: seen from above already; made by moss_texture, not from a slab.
    "ground_moss": ("tile_moss_top.png", 0.0, "moss"),
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
    # #book60: the scarecrow and the bird bath, from the pictures their sprites are cut from.
    "deco_scarecrow": "deco_scarecrow.png",
    "deco_birdbath": "deco_birdbath.png",
}

#: Icons cut from pictures that are already cut out (transparent), as the garden draws them: the
#: cottage and the greenhouse from their sprites, the pond from its own picture. The windmill is
#: put together from its body and its sails ([windmill_icon]).
CUT_ICONS: dict[str, Path] = {
    "struct_cottage": RAW / "struct_cottage.png",
    # The panel of fence the garden draws for one standing alone (item_fence_wood.png is a sheet of parts).
    "struct_fence": RAW / "struct_fence.png",
    "deco_weathervane": RAW / "deco_weathervane.png",
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


#: The moss (#book60) came seen from straight above already (tile_moss_top.png), with broad
#: diagonal waves of light and shade across it: those larger than this (pixels, at
#: [GROUND_SIZE] + [GROUND_MARGIN]) go, as the wasteland's do (FlatGround.evenOut).
MOSS_EVEN_OUT = 26

#: A faint mottling put back over the evened-out moss, so that it is not as flat as felt: its
#: size (pixels) and strength (share of brightness).
MOSS_MOTTLE = (40.0, 0.05)


def moss_texture() -> np.ndarray:
    """The moss over one cell, made to tile: the picture evened out, mottled faintly, blended round."""
    from PIL import ImageFilter
    size = GROUND_SIZE + GROUND_MARGIN
    rgb = np.array(Image.open(SOURCE / "tile_moss_top.png").convert("RGB").resize((size, size), Image.LANCZOS)).astype(float)
    flat = even_out(rgb, MOSS_EVEN_OUT)
    radius, strength = MOSS_MOTTLE
    rng = np.random.default_rng(160)
    noise = Image.fromarray((rng.random((size, size)) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius))
    n = np.array(noise).astype(float)
    n = (n - n.mean()) / max(1e-6, n.std())
    flat = flat * (1.0 + strength * np.clip(n, -2.5, 2.5))[..., None]
    return tile_by_blending(flat, GROUND_MARGIN, joints=False).clip(0, 255).astype(np.uint8)


def moss_icon() -> np.ndarray:
    """The moss' icon: a slab of it, as the other ground's icons are (its top a 2:1 diamond, its
    sides the same moss in shade over a band of earth)."""
    tex = Image.fromarray(moss_texture())
    w, h, depth = 640, 320, 70
    top = tex.resize((w, w)).rotate(45, resample=Image.BICUBIC, expand=True)
    top = top.resize((w, h), Image.LANCZOS)
    diamond = Image.new("L", (w, h), 0)
    from PIL import ImageDraw
    ImageDraw.Draw(diamond).polygon([(w / 2, 0), (w, h / 2), (w / 2, h), (0, h / 2)], fill=255)
    canvas = Image.new("RGBA", (w, h + depth), (0, 0, 0, 0))
    side = np.array(tex.resize((w, depth))).astype(float)
    earth = np.array([92.0, 70.0, 48.0])
    rows = np.linspace(0, 1, depth)[:, None, None]
    side = side * (1 - rows) * 0.55 + earth * rows * 0.75
    for x0, x1, gain in ((0, w // 2, 0.9), (w // 2, w, 0.7)):
        face = Image.new("L", (w, h + depth), 0)
        if x0 == 0:
            ImageDraw.Draw(face).polygon([(0, h / 2), (w / 2, h), (w / 2, h + depth), (0, h / 2 + depth)], fill=255)
        else:
            ImageDraw.Draw(face).polygon([(w / 2, h), (w, h / 2), (w, h / 2 + depth), (w / 2, h + depth)], fill=255)
        band = np.zeros((h + depth, w, 3))
        for y in range(h + depth):
            band[y] = side[min(depth - 1, max(0, y - h // 2))] * gain
        canvas.paste(Image.fromarray(band.clip(0, 255).astype(np.uint8)), (0, 0), face)
    canvas.paste(top, (0, 0), diamond)
    return to_icon(crop(np.array(canvas)))


def ground_texture(object_id: str) -> np.ndarray:
    if object_id == "ground_moss":
        return moss_texture()
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
    for name in ("soil_wasteland", "soil_tilled", "soil_revived"):
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
