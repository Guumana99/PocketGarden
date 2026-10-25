#!/usr/bin/env python3
"""Prepare the visitors' pictures (#71): cut out, split into parts, sized, and packed into an atlas of their own.

The originals are the eight Recraft pictures in tools/source_art/ (creature_*.png, opaque, 1024 x
1024, on white), kept as they came. This script:

  * cuts each out of its white (cut_out from prepare_drawable_art.py), with what each needs:
      - the squirrel: its fur is mixed with the white far into the outline (the fine hairs of the
        tail); the outline is un-mixed deeper, so that no pale haze is left round the tail;
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
    The running squirrel and the flying bird are brought to the same scale as the sitting one and
    the standing one by the size of their heads (SAME_SCALE), so that switching does not jump;
  * anchors: the feet of those on the ground; the middle of the underside of the running squirrel;
    the middle of the body of the flying bird; the middle of the thorax of the butterfly and the
    dragonfly (the point they fly about by);
  * writes them to tools/raw_creatures/ with their anchors in tools/creature_overrides.json, and
    packs them (asset_packer.py) into app/src/main/assets/atlas/creatures.png and
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
ATLAS_DIR = TOOLS.parent / "app" / "src" / "main" / "assets" / "atlas"

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
    "dragonfly": ("w", 0.2),
}

#: Pictures brought to the scale of another: (that one, how much larger its head is there). The
#: heads were measured on the originals (nose or beak to the back of the head, in pixels):
#: sitting squirrel 190, running 123; standing bird 228, flying 135.
SAME_SCALE: dict[str, tuple[str, float]] = {
    "squirrel_run": ("squirrel", 190 / 123),
    "bird_fly": ("bird", 228 / 135),
}

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


def cut(name: str) -> np.ndarray:
    rgb = source(name)
    if name.startswith("squirrel"):
        return cut_out(rgb, edge=14, solid=120.0)
    if name.startswith("bird"):
        return without_white_pockets(rgb, cut_out(rgb, light=244, tol=10, pocket=60, edge=3))
    if name == "dragonfly":
        return clear_wings(rgb)
    return cut_out(rgb)


# --- Parts -------------------------------------------------------------------------------------

def polygon_mask(shape, points) -> np.ndarray:
    image = Image.new("L", (shape[1], shape[0]), 0)
    ImageDraw.Draw(image).polygon(points, fill=255)
    return np.array(image) > 0


def only(rgba: np.ndarray, mask: np.ndarray) -> np.ndarray:
    out = rgba.copy()
    out[~mask, 3] = 0
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
            ["squirrel", "squirrel_run", "bird", "bird_fly", "frog", "ladybug", "butterfly", "dragonfly"]}
    scales: dict[str, float] = {}
    for name, (side, share) in SIZES.items():
        x0, y0, x1, y1 = bounds(cuts[name])
        scales[name] = share * TILE / ((x1 - x0) if side == "w" else (y1 - y0))
    for name, (other, ratio) in SAME_SCALE.items():
        scales[name] = scales[other] * ratio

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
    add("creature_squirrel_run", cuts["squirrel_run"], scales["squirrel_run"], run_belly(cuts["squirrel_run"]))

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
        RAW, ATLAS_DIR, tile_width=TILE, max_width=1024, padding=2, overrides_path=OVERRIDES,
        atlas_name="creatures.png", manifest_name="creatures_manifest.json",
    )
    size = (ATLAS_DIR / "creatures.png").stat().st_size
    print(f"packed {len(manifest['assets'])} into {manifest['atlasWidth']}x{manifest['atlasHeight']}, {size / 1024:.0f} KiB")
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
