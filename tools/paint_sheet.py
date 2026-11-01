#!/usr/bin/env python3
"""Lay out every choice of colours for the paintable buildings on one sheet, to look them over.

The colours are mixed as the game mixes them (PaintMix in ui/render/BuildingPainter.kt), from the
pictures and masks in tools/raw_assets. Each building's roofs run down the sheet and its walls across.

    python tools/paint_sheet.py <sheet.png>
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

RAW = Path(__file__).resolve().parent / "raw_assets"

#: BuildingPaint's colours: the picture's own first.
PAINTS = {
    "struct_cottage": (
        [("red", None), ("blue", 0x3E6FB0), ("green", 0x4E8A5E), ("dark_brown", 0x5A3A28)],
        [("white", None), ("cream", 0xF2E3BE), ("pale_blue", 0xCFE3EE), ("pale_pink", 0xF3D6D6)],
    ),
    "struct_hut": (
        [("grey", None), ("dark_brown", 0x5A3A28), ("green", 0x4E8A5E), ("blue", 0x3E6FB0)],
        [("wood", None), ("dark_brown", 0x5A3A28), ("white", 0xF4F0E8), ("pale_green", 0xCFE2C8)],
    ),
    "struct_well": (
        [("red", None), ("blue", 0x3E6FB0), ("green", 0x4E8A5E), ("dark_brown", 0x5A3A28)],
        [],
    ),
}

ROOF_SHADE = 1.0
WALL_SHADE = 0.4

#: The light behind the buildings on the sheet: pale, so that a white fringe shows up less than
#: on the dark one, which shows it plainly.
BACKGROUND = (92, 96, 84)


def rgb(colour: int) -> np.ndarray:
    return np.array([(colour >> 16) & 255, (colour >> 8) & 255, colour & 255], float)


def lay(out: np.ndarray, picture: np.ndarray, share: np.ndarray, colour: int, shade_share: float) -> None:
    light = picture[..., :3].astype(float).mean(axis=2)
    marked = (share > 128 / 255) & (picture[..., 3] > 128)
    average = max(float(light[marked].mean()) if marked.any() else 1.0, 1.0)
    current = out[..., :3].astype(float)
    light_now = current.mean(axis=2)
    shade = 1.0 + (light_now / average - 1.0) * shade_share
    painted = np.clip(rgb(colour)[None, None, :] * shade[..., None], 0, 255)
    mixed = (current + (painted - current) * share[..., None]).astype(int)
    out[..., :3] = np.where(share[..., None] > 0, mixed, out[..., :3])


def painted(picture: np.ndarray, mask: np.ndarray, roof: int | None, wall: int | None) -> np.ndarray:
    out = picture.copy()
    if roof is not None:
        lay(out, picture, mask[..., 0] / 255.0, roof, ROOF_SHADE)
    if wall is not None:
        lay(out, picture, mask[..., 1] / 255.0, wall, WALL_SHADE)
    return out


def font(size: int) -> ImageFont.ImageFont:
    for name in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def sheet_of(asset: str, cell: int) -> Image.Image:
    roofs, walls = PAINTS[asset]
    walls = walls or [("", None)]
    picture = np.array(Image.open(RAW / f"{asset}.png").convert("RGBA"))
    mask = np.array(Image.open(RAW / f"mask_{asset}.png").convert("RGBA"))
    high = cell * picture.shape[0] // picture.shape[1]
    sheet = Image.new("RGB", (cell * len(walls), (high + 26) * len(roofs) + 34), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    draw.text((6, 4), asset, fill="white", font=font(20))
    for row, (roof_name, roof) in enumerate(roofs):
        for column, (wall_name, wall) in enumerate(walls):
            done = Image.fromarray(painted(picture, mask, roof, wall), "RGBA").resize((cell, high), Image.LANCZOS)
            y = 34 + row * (high + 26)
            sheet.paste(done, (column * cell, y), done)
            draw.text((column * cell + 6, y + high + 2), f"{roof_name} / {wall_name}", fill="white", font=font(16))
    return sheet


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("target", type=Path)
    parser.add_argument("--assets", nargs="*", default=["struct_cottage", "struct_hut"])
    parser.add_argument("--cell", type=int, default=340)
    args = parser.parse_args()
    sheets = [sheet_of(asset, args.cell) for asset in args.assets]
    whole = Image.new("RGB", (max(s.width for s in sheets), sum(s.height + 10 for s in sheets)), "white")
    y = 0
    for sheet in sheets:
        whole.paste(sheet, (0, y))
        y += sheet.height + 10
    whole.save(args.target, quality=90)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
