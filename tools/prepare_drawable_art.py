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
      - carrot: no stage 2 came; it is stage 3's leaves alone, the root still in the ground.
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
      - withered pictures (#58) came for every crop here but the tulip, which does not wither;
        each is as tall (or as wide) as WITHERED says;
  * icons: icon_<crop>.png, cut out and brought to ICON_SIZE, to res/drawable-nodpi for the bag;
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

#: Each stage's size in tiles: its height, or ("w", width) for a field laid over the whole cell.
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
}

#: How a crop's pictures (stages, withered and icon) are cut out, where the defaults do not do:
#: the cosmos' leaves (#63) are hair-fine, and the white showing through them left them in a pale
#: haze; it is taken out of the whole picture, not only its outline (cut_out's [throughout]).
#: The snowdrop's flowers (#64) are white, and the white is taken out more gently round them.
CUT: dict[str, dict] = {
    "cosmos": {"throughout": True, "solid": 100.0},
    "snowdrop": {"white_petals": True},
}

#: Icons in source_art that are not the picture they are named for, and so are not made (the item
#: keeps its emoji): icon_snowdrop.png came as a copy of crop_snowdrop_withered.png (#64).
WRONG_ICONS = {"icon_snowdrop"}

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

#: How much of the carrot's root shows above its bed at stage 3 (#60), as a share of the root.
CARROT_SHOULDER = 0.22

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
}


# --- Cutting out -------------------------------------------------------------------------------

def erode(mask: np.ndarray, times: int) -> np.ndarray:
    return ~dilate(~mask, times)


def cut_out(rgb: np.ndarray, light: int = 190, tol: int = 30, pocket: int = 3, edge: int = 2,
            solid: float = 70.0, throughout: bool = False, white_petals: bool = False) -> np.ndarray:
    """[rgb] (opaque, on white) with the white made transparent; RGBA.

    [edge]: how deep (pixels) the outline is un-mixed from the white. Two is enough for leaves with
    a clean outline; hair-thin foliage (the cosmos, #63) is mixed with the white much further in.
    [solid]: how far from white (its darkest channel) a pixel of the outline must be to be opaque.
    [throughout]: un-mix the whole picture, not only its outline: for foliage so fine that the white
    shows through it everywhere (the cosmos' leaves). Its petals (pink, and a little round them) are
    left solid, so that their pale parts do not turn see-through.
    [white_petals]: the flowers are white (the snowdrop, #64): round them, only the plain white of
    the background goes, not the pale grey of their shading, which the usual test took for
    background and bit out of them. Only in the upper four fifths of the picture: at its foot the
    pale grey is the shadow on the ground, and goes.
    """
    v = rgb.astype(int)
    mx, mn = v.max(axis=2), v.min(axis=2)
    pale = (mn >= light) & ((mx - mn) <= tol)
    background = reconstruct(border(pale.shape) & pale, pale)
    if white_petals:
        plain_white = (mn >= 246) & ((mx - mn) <= 8)
        strict = reconstruct(border(pale.shape) & plain_white, plain_white)
        petals = pale & ~strict
        petals[int(petals.shape[0] * 0.8):] = False
        zone = dilate(petals, 3)
        background = np.where(zone, strict, background)
    # White shut in by leaves: near-white and grey-free, in patches too wide to be a highlight or
    # a petal (they survive [pocket] steps of erosion), so that small light spots stay.
    plain = (mn >= 222) & ((mx - mn) <= 16) & ~background
    if white_petals:
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
    grown = crop(load_cut("crop_carrot_3.png"))
    # Stage 2: the leaves of stage 3, down to where the orange of the root begins.
    rgb = grown[:, :, :3].astype(int)
    orange = (rgb[:, :, 0] > 170) & (rgb[:, :, 1] < 150) & (rgb[:, :, 2] < 90) & (grown[:, :, 3] > 128)
    rows = np.where(orange.sum(axis=1) > 6)[0]
    leaves = crop(grown[:rows.min() - 6].copy())
    # Stage 3: the leaves and the shoulder of the root, fading into the bed below it (#60).
    cut = rows.min() + int((rows.max() - rows.min()) * CARROT_SHOULDER)
    ready = grown[:cut].copy()
    fade = min(10, cut - rows.min())
    ramp = np.linspace(1.0, 0.0, fade + 2)[1:-1]
    for i, k in enumerate(ramp):
        row = cut - fade + i
        ready[row, :, 3] = (ready[row, :, 3] * k).astype(ready.dtype)
    return [crop(load_cut("crop_carrot_1.png")), leaves, crop(ready)]


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


def to_size(rgba: np.ndarray, size) -> np.ndarray:
    h, w = rgba.shape[:2]
    if isinstance(size, tuple):
        scale = size[1] * TILE / w
    else:
        scale = size * TILE / h
    return resize(rgba, scale, scale)


def field_anchor(rgba: np.ndarray) -> dict:
    """A field stands on the middle of its cell: a quarter of its width above its front corner."""
    h, w = rgba.shape[:2]
    ys = np.where(rgba[:, :, 3] > ALPHA_MIN)[0]
    return {"anchorX": 0.5, "anchorY": round(float((ys.max() - w / 4) / h), 4), "isFlippedAllowed": True}


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
    }
    out: dict[str, tuple[np.ndarray, dict | None]] = {}
    for crop_name, pictures in stages.items():
        sized = [to_size(p, s) for p, s in zip(pictures, STAGES[crop_name])]
        for n, picture in enumerate(sized, start=1):
            override = field_anchor(picture) if crop_name == "wheat" else ANCHORS.get(f"plant_{crop_name}_stage{n}")
            out[f"plant_{crop_name}_stage{n}"] = (picture, override)
        if crop_name in WITHERED:
            dead = to_size(crop(load_cut(f"crop_{crop_name}_withered.png", **CUT.get(crop_name, {}))), WITHERED[crop_name])
            out[f"plant_{crop_name}_withered"] = (dead, field_anchor(dead) if crop_name == "wheat" else ANCHORS.get(f"plant_{crop_name}_withered"))
    for colour in TULIP_COLOURS:
        bloom = to_size(crop(load_cut(f"crop_tulip_{colour}_stage3.png")), STAGES["tulip"][2])
        out[f"plant_tulip_stage3_{colour}"] = (bloom, None)
    return out


# --- Icons, lawn, ground -----------------------------------------------------------------------

def prepare_icons() -> dict[str, np.ndarray]:
    out = {}
    for path in sorted(SOURCE.glob("icon_*.png")):
        if path.stem in WRONG_ICONS:
            continue
        rgba = crop(load_cut(path.name, **CUT.get(path.stem.removeprefix("icon_"), {})))
        h, w = rgba.shape[:2]
        scale = ICON_SIZE * 0.92 / max(h, w)
        small = resize(rgba, scale, scale)
        canvas = np.zeros((ICON_SIZE, ICON_SIZE, 4), np.uint8)
        sh, sw = small.shape[:2]
        paste(canvas, small, (ICON_SIZE - sw) // 2, (ICON_SIZE - sh) // 2)
        out[path.stem] = canvas
    return out


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
