#!/usr/bin/env python3
"""The header picture of the X (Twitter) profile: docs/store/x_header.png, 1500 x 500.

Not part of the app: a picture for the store pages, so it is not in docs/ASSET_LEDGER.md.

  * the background is tools/source_art/app_icon_bg_sky.png (the app icon's blurred sky and green
    hills), scaled to 1500 wide and cut to 500 tall round the line of the hills;
  * on the hills, right of the middle, stand seven crops at their last stage, cut out of their white
    as in the game (prepare_drawable_art.cut_out, with the crop's own CUT), two rows: the back one
    smaller and higher up. Each has a faint shadow at its foot, to sit in the grass;
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

#: The crops: (crop, source picture, x and y of its foot, pixels a tile at that depth). Drawn in
#: this order, back to front. Pixels a tile scale the game's own sizes (STAGES), so that the crops
#: keep their sizes against one another; the back row is about three quarters of the front one.
CROPS: list[tuple[str, str, int, int, int]] = [
    ("sunflower", "crop_sunflower_stage3.png", 1005, 368, 250),
    ("cosmos", "crop_cosmos_stage3.png", 1300, 364, 245),
    ("lavender", "crop_lavender_stage3.png", 1160, 374, 250),
    ("tomato", "crop_tomato_3.png", 865, 426, 320),
    ("tulip", "crop_tulip_red_stage3.png", 1050, 438, 320),
    ("strawberry", "crop_strawberry_3.png", 1220, 434, 330),
    ("snowdrop", "crop_snowdrop_stage3.png", 1385, 428, 320),
]

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


def background() -> Image.Image:
    sky = Image.open(SOURCE / "app_icon_bg_sky.png").convert("RGB")
    sky = sky.resize((WIDTH, round(sky.height * WIDTH / sky.width)), Image.LANCZOS)
    return sky.crop((0, SKY_TOP, WIDTH, SKY_TOP + HEIGHT)).convert("RGBA")


def compose() -> Image.Image:
    canvas = background()
    for name, source, x, y, per_tile in CROPS:
        rgba = crop(load_cut(source, **CUT.get(name, {})))
        h, w = rgba.shape[:2]
        height = STAGES[name][2] * per_tile
        size = (max(1, round(w * height / h)), round(height))
        sprite = premultiplied_resize(rgba, size)
        left = round(x - foot_x(rgba, f"plant_{name}_stage3") * size[0])
        ground_shadow(canvas, x, y, size[0] * 0.6)
        canvas.alpha_composite(sprite, (left, y - size[1]))
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
