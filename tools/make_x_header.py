#!/usr/bin/env python3
"""The header picture of the X (Twitter) profile: docs/store/x_header.png, 1500 x 500.

Not part of the app: a picture for the store pages, so it is not in docs/ASSET_LEDGER.md.

  * the background is tools/source_art/app_icon_bg_sky.png (the app icon's blurred sky and green
    hills), scaled to 1500 wide and cut to 500 tall round the line of the hills;
  * on the hills, right of the middle, stand five crops at their last stage, cut out of their white
    as in the game (prepare_drawable_art.cut_out, with the crop's own CUT), one beside another with
    a little room between them, by turns at the front (larger, lower) and at the back (smaller,
    higher up);
  * across the bottom, a strip of near grass (grass_strip), less blurred than the hills, with
    blades along its top: the crops' feet are hidden in it, so that they stand in the grass
    instead of floating over the hills, each with a faint shadow on it;
  * a butterfly (cut out as prepare_creature_art does), tilted, in the sky above them.

Only crops and the butterfly that a new player meets early are shown (no later crops, no other
creatures, no buildings). The bottom left (about 400 x 200) is kept clear for the profile picture,
and nothing that matters is within 60 px of the top or bottom edge, which some phones cut off.

Usage:
  python tools/make_x_header.py                     # writes docs/store/x_header.png
  python tools/make_x_header.py --preview out/      # also writes the check pictures

Requires Pillow and numpy.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
from prepare_drawable_art import ALPHA_MIN, ANCHORS, CUT, SOURCE, STAGES, crop, load_cut  # noqa: E402
from prepare_creature_art import cut as cut_creature  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "store" / "x_header.png"

WIDTH, HEIGHT = 1500, 500

#: Where the 500 rows are taken from the background once it is scaled to [WIDTH] (1500 x 1500):
#: the hills' line about 340 px down.
SKY_TOP = 770

#: The crops, left to right: (crop, source picture, row).
CROPS: list[tuple[str, str, str]] = [
    ("tomato", "crop_tomato_3.png", "front"),
    ("sunflower", "crop_sunflower_stage3.png", "back"),
    ("strawberry", "crop_strawberry_3.png", "front"),
    ("lavender", "crop_lavender_stage3.png", "back"),
    ("cosmos", "crop_cosmos_stage3.png", "front"),
]

#: Each row: (y of the crops' feet, pixels a tile). Pixels a tile scale the game's own sizes
#: (STAGES), so that the crops keep their sizes against one another; the back row is about four
#: fifths of the front one, and stands further up the strip. Both feet are below the top of the
#: grass strip, hidden in it.
ROWS: dict[str, tuple[int, int]] = {"front": (484, 300), "back": (452, 245)}

#: Where the right-hand crop ends, and the room between one crop's picture and the next.
CROPS_RIGHT = 1465
CROPS_GAP = 18

#: The top of the near grass; its blades rise up to [BLADE_HEIGHT] above where they stand.
STRIP_TOP = 440
BLADE_HEIGHT = (10, 30)

#: The green the near grass is drawn towards, from the hills' own (which is yellow on the left).
GRASS_GREEN = np.array([74.0, 122.0, 42.0])

#: The butterfly: middle, width in pixels, and turn (degrees, anticlockwise).
BUTTERFLY = ((1150, 118), 104, 18)


def premultiplied_resize(rgba: np.ndarray, size: tuple[int, int]) -> Image.Image:
    """[rgba] scaled to [size], its colour weighted by its alpha so that no pale fringe comes in
    from the colour of the transparent pixels round it."""
    return Image.fromarray(rgba).convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")


def foot_x(rgba: np.ndarray, asset_id: str) -> float:
    """Where along its width (a share) the picture stands: ANCHORS, else the middle of its
    lowest rows."""
    if asset_id in ANCHORS:
        return ANCHORS[asset_id]["anchorX"]
    h, w = rgba.shape[:2]
    solid = rgba[:, :, 3] > ALPHA_MIN
    ys = np.where(solid.any(axis=1))[0]
    low = solid[max(0, ys.max() - h // 25):ys.max() + 1]
    return float(np.where(low.any(axis=0))[0].mean() / w)


def ground_shadow(canvas: Image.Image, x: int, y: int, width: int) -> None:
    """A soft, faint dark green oval at a crop's foot."""
    pad = 30
    w, h = int(width) + 2 * pad, 18 + 2 * pad
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse((pad, pad, w - pad, h - pad), fill=(28, 52, 18, 70))
    layer = layer.filter(ImageFilter.GaussianBlur(7))
    canvas.alpha_composite(layer, (int(x - w / 2), int(y - h / 2 - 2)))


def grass_strip(rng: np.random.Generator) -> Image.Image:
    """The near grass across the bottom: rows of thin leaning blades, one row every few pixels
    from [STRIP_TOP] down, each nearer row a little lighter; under them a body of the same green,
    darker below. The green at each column is the hills' own (from the bottom rows of the
    background, smoothed along) drawn halfway to [GRASS_GREEN]. Drawn three times larger and
    scaled down, for smooth edges, and only a little softened: sharper than the hills behind."""
    k = 3
    rows = np.array(background())[HEIGHT - 40:, :, :3].astype(float).mean(axis=0)
    kernel = np.ones(121) / 121
    hills = np.stack([np.convolve(np.pad(rows[:, c], 60, mode="edge"), kernel, "valid") for c in range(3)], axis=1)
    base = (hills + GRASS_GREEN) / 2 * 0.8

    layer = Image.new("RGBA", (WIDTH * k, HEIGHT * k), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    body = STRIP_TOP + 14
    for y in range(body, HEIGHT):
        colour = (base.mean(axis=0) * (1 - 0.25 * (y - body) / (HEIGHT - body))).astype(int)
        d.rectangle((0, y * k, WIDTH * k, (y + 1) * k), fill=tuple(colour) + (255,))
    depths = list(range(STRIP_TOP + 4, HEIGHT + 24, 7))
    for i, foot_line in enumerate(depths):
        near = i / (len(depths) - 1)
        x = -4.0
        while x < WIDTH + 4:
            foot = foot_line + rng.uniform(-3, 3)
            tall = rng.uniform(*BLADE_HEIGHT) * (0.7 + 0.5 * near) * (1.0 if i < 2 else 0.8)
            half = rng.uniform(1.3, 2.4) * (0.8 + 0.5 * near)
            lean = rng.uniform(-8, 8)
            light = (0.72 + 0.28 * near) * rng.uniform(0.82, 1.15) * (1 - 0.2 * max(0.0, (foot - body) / (HEIGHT - body)))
            fill = tuple(int(c) for c in (base[int(np.clip(x, 0, WIDTH - 1))] * light).clip(0, 255)) + (255,)
            d.polygon([((x - half) * k, (foot + 6) * k), ((x + half) * k, (foot + 6) * k), ((x + lean) * k, (foot - tall) * k)], fill=fill)
            x += rng.uniform(1.2, 3.0)
    small = layer.convert("RGBa").resize((WIDTH, HEIGHT), Image.LANCZOS)
    return small.filter(ImageFilter.GaussianBlur(0.6)).convert("RGBA")


def background() -> Image.Image:
    sky = Image.open(SOURCE / "app_icon_bg_sky.png").convert("RGB")
    sky = sky.resize((WIDTH, round(sky.height * WIDTH / sky.width)), Image.LANCZOS)
    return sky.crop((0, SKY_TOP, WIDTH, SKY_TOP + HEIGHT)).convert("RGBA")


def compose() -> Image.Image:
    canvas = background()
    placed = []
    right = CROPS_RIGHT
    for name, source, row in reversed(CROPS):
        rgba = crop(load_cut(source, **CUT.get(name, {})))
        h, w = rgba.shape[:2]
        y, per_tile = ROWS[row]
        height = STAGES[name][2] * per_tile
        size = (max(1, round(w * height / h)), round(height))
        left = right - size[0]
        placed.append((row, premultiplied_resize(rgba, size), left, y, left + foot_x(rgba, f"plant_{name}_stage3") * size[0]))
        right = left - CROPS_GAP
    for row in ("back", "front"):
        for r, sprite, left, y, _ in placed:
            if r == row:
                canvas.alpha_composite(sprite, (left, y - sprite.height))
    canvas.alpha_composite(grass_strip(np.random.default_rng(7)))
    for _, sprite, _, _, x in placed:
        ground_shadow(canvas, x, STRIP_TOP + 8, sprite.width * 0.7)
    (cx, cy), width, turn = BUTTERFLY
    fly = crop(cut_creature("butterfly"))
    fh, fw = fly.shape[:2]
    sprite = premultiplied_resize(fly, (width, round(fh * width / fw)))
    sprite = sprite.convert("RGBa").rotate(turn, Image.BICUBIC, expand=True).convert("RGBA")
    canvas.alpha_composite(sprite, (round(cx - sprite.width / 2), round(cy - sprite.height / 2)))
    return canvas.convert("RGB")


def with_guides(header: Image.Image, areas: bool = True) -> Image.Image:
    """[header] with the profile picture's circle (270 px across, over the bottom left) and, with
    [areas], the clear area and the 60 px bands top and bottom marked."""
    out = header.convert("RGBA")
    over = Image.new("RGBA", out.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(over)
    if areas:
        d.rectangle((0, 0, WIDTH, 60), fill=(255, 0, 0, 40))
        d.rectangle((0, HEIGHT - 60, WIDTH, HEIGHT), fill=(255, 0, 0, 40))
        d.rectangle((0, HEIGHT - 200, 400, HEIGHT), outline=(255, 140, 0, 255), width=3)
    r = 135
    centre = (30 + r, HEIGHT - 60)
    d.ellipse((centre[0] - r, centre[1] - r, centre[0] + r, centre[1] + r), fill=(240, 240, 240, 255),
              outline=(255, 255, 255, 255), width=6)
    out.alpha_composite(over)
    return out.convert("RGB")


def on_phone(header: Image.Image, width: int = 390) -> Image.Image:
    """[header] at a phone's width, with the profile picture's circle at that scale."""
    return with_guides(header, areas=False).resize((width, round(HEIGHT * width / WIDTH)), Image.LANCZOS)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--preview", type=Path, help="folder for the check pictures")
    args = parser.parse_args()
    header = compose()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    header.save(OUT, optimize=True)
    print(f"wrote {OUT.relative_to(ROOT)} {header.size}")
    if args.preview:
        args.preview.mkdir(parents=True, exist_ok=True)
        with_guides(header).save(args.preview / "x_header_guides.png")
        on_phone(header).save(args.preview / "x_header_phone.png")
        print(f"wrote check pictures to {args.preview}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
