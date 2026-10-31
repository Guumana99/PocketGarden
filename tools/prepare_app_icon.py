#!/usr/bin/env python3
"""Make the app's icon from the box garden and the sky (#87).

Inputs (tools/source_art, Recraft, 1024 x 1024):
  app_icon_frame.png   the wooden box garden on white
  app_icon_bg_sky.png  sky and hills, opaque

Outputs:
  res/mipmap-<density>/ic_launcher_foreground.png    the box, cut out, with a faint shadow under it
  res/mipmap-<density>/ic_launcher_background.png    the sky
  res/mipmap-<density>/ic_launcher_monochrome.png    one colour, for themed icons (Android 13 and up)
  res/mipmap-<density>/ic_launcher.png, _round.png   the two together, for launchers without
                                                     adaptive icons: a rounded square and a circle
  docs/store/icon_512.png                            for the Play listing: square, corners not rounded

An adaptive icon is a 108 dp canvas of which about the middle 72 dp show. The box is 72 dp wide,
in the middle, a little above it; the butterfly and the sunflower may reach out of the safe 66 dp.

Usage:
  python tools/prepare_app_icon.py                 # writes the icons
  python tools/prepare_app_icon.py --preview f.png # also a sheet of them under launcher masks

Requires Pillow and numpy.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter

from prepare_building_art import SOURCE, border, components, crop, dilate, reconstruct

ROOT = SOURCE.parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
STORE = ROOT / "docs" / "store" / "icon_512.png"

#: Each density's canvas sides: the adaptive layers' 108 dp and the plain icon's 48 dp.
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

#: The size everything is made at before it is brought down.
CANVAS = 1296

#: The box's width on the 108 dp canvas, and how far above the middle it goes (a share of the canvas).
BOX_SHARE = 72 / 108
LIFT = 0.015

#: The box from its back corner to its front one, as a share of its width.
BOX_TALL = 0.54

#: The part of the sky's square that is used, a share of its side, from the bottom: the hills
#: otherwise lie below what an adaptive icon shows.
SKY_SHARE = 0.68

#: The ground shadow: how dark, and how soft (a share of the canvas).
SHADOW_ALPHA = 0.22
SHADOW_BLUR = 0.018

#: The least of plain white, in pixels of the original, that counts as a gap and not a highlight.
POCKET = 24

#: Share of the plain icon's side that is rounded off.
CORNER = 0.22


def cut_box() -> np.ndarray:
    """
    The box garden cut out of its white. What is white or pale and joined to the picture's edge goes,
    and pale pockets that white shows through; the outline is taken back out of the mix with the
    white, so that no pale line is left along the butterfly's wings or the sunflower's stem.
    """
    rgb = np.array(Image.open(SOURCE / "app_icon_frame.png").convert("RGB"))
    v = rgb.astype(float)
    darkest = v.min(axis=2)
    chroma = v.max(axis=2) - darkest
    pale = (darkest > 205) & (chroma < 26)
    background = reconstruct(border(pale.shape), pale)
    # White seen through the leaves and round the butterfly, shut in by them.
    shut_in = (darkest > 232) & (chroma < 16) & ~background
    for part in components(shut_in):
        if part.sum() >= POCKET:
            background |= part
    near = dilate(background, 3) & ~background
    alpha = np.where(background, 0.0, 1.0)
    alpha[near] = np.clip((245.0 - darkest[near]) / 60.0, 0.0, 1.0)
    a = alpha[..., None]
    clean = np.clip((v - (1 - a) * 255.0) / np.maximum(a, 0.05), 0, 255)
    return crop(np.dstack([clean, alpha * 255.0]).round().astype(np.uint8))


def box_columns(rgba: np.ndarray) -> tuple[int, int]:
    """The left and right ends of the box: the widest part of the picture, in its lower half."""
    solid = rgba[:, :, 3] > 128
    h = solid.shape[0]
    xs = np.where(solid[h // 2:].any(axis=0))[0]
    return int(xs.min()), int(xs.max())


def foreground() -> Image.Image:
    box = cut_box()
    left, right = box_columns(box)
    scale = CANVAS * BOX_SHARE / (right - left + 1)
    h, w = box.shape[:2]
    size = (round(w * scale), round(h * scale))
    sprite = Image.fromarray(box, "RGBA").convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")
    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    # The middle of the box, not of what stands on it, goes on the middle of the canvas.
    x = round(CANVAS / 2 - (left + right + 1) / 2 * scale)
    y = round(CANVAS * (0.5 - LIFT) - (h - (right - left + 1) * BOX_TALL / 2) * scale)
    shadow = Image.new("L", (CANVAS, CANVAS), 0)
    shadow.paste(sprite.getchannel("A"), (x, y + round(CANVAS * 0.012)))
    shadow = shadow.filter(ImageFilter.GaussianBlur(CANVAS * SHADOW_BLUR)).point(lambda v: int(v * SHADOW_ALPHA))
    # Only the part below the box's own outline shows; the rest lies under it.
    canvas.paste((30, 22, 12, 255), (0, 0), shadow)
    canvas.alpha_composite(sprite, (x, y))
    return canvas


def background() -> Image.Image:
    sky = Image.open(SOURCE / "app_icon_bg_sky.png").convert("RGBA")
    side = round(sky.width * SKY_SHARE)
    left = (sky.width - side) // 2
    return sky.crop((left, sky.height - side, left + side, sky.height)).resize((CANVAS, CANVAS), Image.LANCZOS)


def monochrome(front: Image.Image) -> Image.Image:
    """One colour for a themed icon: the outline of the box, house, sunflower and butterfly, solid. Without the shadow."""
    alpha = np.array(front).astype(float)[:, :, 3]
    out = np.zeros(alpha.shape + (4,), np.uint8)
    out[:, :, :3] = 255
    out[:, :, 3] = np.clip((alpha - 120.0) * 3.0, 0, 255).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def flattened(front: Image.Image) -> Image.Image:
    """Sky and box on one square, cut to the 72 dp that an adaptive icon shows."""
    whole = background()
    whole.alpha_composite(front)
    inset = round(CANVAS * 18 / 108)
    return whole.crop((inset, inset, CANVAS - inset, CANVAS - inset))


def masked(picture: Image.Image, mask: Image.Image) -> Image.Image:
    out = picture.copy()
    out.putalpha(ImageChops.multiply(picture.getchannel("A"), mask.resize(picture.size, Image.LANCZOS)))
    return out


def rounded_mask(side: int, corner: float) -> Image.Image:
    mask = Image.new("L", (side * 4, side * 4), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, side * 4 - 1, side * 4 - 1), radius=side * 4 * corner, fill=255)
    return mask


def round_mask(side: int) -> Image.Image:
    mask = Image.new("L", (side * 4, side * 4), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, side * 4 - 1, side * 4 - 1), fill=255)
    return mask


def teardrop_mask(side: int) -> Image.Image:
    """The drop some launchers cut with: a circle with its bottom right corner left square."""
    mask = round_mask(side)
    ImageDraw.Draw(mask).rectangle((side * 2, side * 2, side * 4 - 1, side * 4 - 1), fill=255)
    return mask


def write(front: Image.Image, back: Image.Image, mono: Image.Image, flat: Image.Image) -> None:
    for density, factor in DENSITIES.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(exist_ok=True)
        layer = round(108 * factor)
        for name, picture in (("foreground", front), ("background", back), ("monochrome", mono)):
            small = picture.convert("RGBa").resize((layer, layer), Image.LANCZOS).convert("RGBA")
            if name == "background":
                small = small.convert("RGB")
            small.save(folder / f"ic_launcher_{name}.png", optimize=True)
        icon = round(48 * factor)
        small = flat.resize((icon, icon), Image.LANCZOS)
        masked(small, rounded_mask(icon, CORNER)).save(folder / "ic_launcher.png", optimize=True)
        masked(small, round_mask(icon)).save(folder / "ic_launcher_round.png", optimize=True)
    flat.resize((512, 512), Image.LANCZOS).save(STORE, optimize=True)


def preview(flat: Image.Image, mono: Image.Image, path: Path) -> None:
    masks = [("circle", round_mask), ("square", lambda s: rounded_mask(s, CORNER)), ("drop", teardrop_mask)]
    sizes = [360, 144, 96]
    margin = 24
    width = len(masks) * (sizes[0] + margin) + margin + sizes[0] + margin
    sheet = Image.new("RGBA", (width, sum(sizes) + margin * 4), (236, 232, 222, 255))
    for column, (_, make) in enumerate(masks):
        y = margin
        for side in sizes:
            sheet.alpha_composite(masked(flat.resize((side, side), Image.LANCZOS), make(side)), (margin + column * (sizes[0] + margin), y))
            y += side + margin
    # The themed icon, as a launcher tints it.
    side = sizes[0]
    tile = Image.new("RGBA", (side, side), (214, 228, 204, 255))
    inset = round(CANVAS * 18 / 108)
    shape = mono.crop((inset, inset, CANVAS - inset, CANVAS - inset)).resize((side, side), Image.LANCZOS)
    ink = Image.new("RGBA", (side, side), (44, 74, 44, 255))
    tile.paste(ink, (0, 0), shape.getchannel("A"))
    sheet.alpha_composite(masked(tile, round_mask(side)), (margin + len(masks) * (sizes[0] + margin), margin))
    sheet.convert("RGB").save(path)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--preview", type=Path, help="also write a sheet of the icon under launcher masks")
    args = parser.parse_args()
    front = foreground()
    back = background()
    mono = monochrome(front.copy())
    flat = flattened(front)
    write(front, back, mono, flat)
    if args.preview:
        preview(flat, mono, args.preview)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
