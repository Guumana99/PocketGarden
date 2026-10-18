#!/usr/bin/env python3
"""Take the ball of earth out from under a crop's picture (#47).

Every crop picture in raw_assets/ (plant_*) came with its own clump of earth under it. In the game a
crop grows in a bed of tilled soil that is drawn by the game, so the clump showed as a second, clashing
lump of earth on the bed, and it leaned in the wind with the plant. This module makes the clump
transparent and leaves the plant: stems, leaves, flowers, fruit, and the white root of a daikon.

How the clump is found, working on one picture:

  * earth is told by its colour: not green, not a bright or saturated colour (fruit, petals, roots);
  * the clump is the bottom of the picture: scanning up from the bottom row, the rows where at least
    half of the picture is earth, after the loose crumbs under it (rows only a few pixels wide);
  * inside that band, earth-coloured pixels go, and so do the pale crumbs and halo round the clump
    (half see-through, or at the picture's edge). Nothing above the band is touched.

A withered plant is itself earth-brown, so for it only the darkest pixels count when the band is
found; if there is no dark clump at all (a pale, sandy one), the band is where the picture is wide,
below the point where it narrows to the stem.

The game then stands the plant on the base of its stem ([stem_base]), in the middle of its cell.
A few stray crumbs that are left in the band, not joined to the plant, are cleared as well.

Used by asset_packer.py (--strip-crop-soil); run this file on its own to preview the result:

  python tools/crop_soil.py tools/raw_assets preview.png plant_tulip_stage4 plant_tomato_stage3
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

#: Asset ids that are crop pictures, and so have a clump of earth to take out.
CROP_PREFIX = "plant_"


def is_crop(asset_id: str) -> bool:
    return asset_id.startswith(CROP_PREFIX)


def soil_mask(rgb: np.ndarray, dark_only: bool = False) -> np.ndarray:
    """Pixels the colour of earth: not green, not a saturated colour, not bright."""
    r, g, b = (rgb[..., i].astype(float) for i in range(3))
    mx = np.maximum(np.maximum(r, g), b)
    mn = np.minimum(np.minimum(r, g), b)
    v = mx / 255.0
    s = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1), 0)
    not_green = g <= np.maximum(r, b) * 1.06
    not_fruit = (s < 0.6) | (v < 0.22)
    not_bright = v < (0.36 if dark_only else 0.62)
    return not_green & not_fruit & not_bright


def _ball_by_colour(alpha: np.ndarray, soil: np.ndarray) -> int:
    """The top row of the clump: up from the bottom while at least half of each row is earth."""
    ys = np.where(alpha.any(axis=1))[0]
    top, bottom = int(ys.min()), int(ys.max())
    widest = alpha.sum(axis=1).max()
    last, misses, started = bottom, 0, False
    for y in range(bottom, top - 1, -1):
        n = alpha[y].sum()
        if n == 0:
            continue
        if not started and n < widest * 0.25:
            # Loose crumbs under the clump: a few pixels a row, part of it whatever their colour.
            last = y
            continue
        if soil[y].sum() / n >= 0.5:
            last, misses, started = y, 0, True
        elif not started:
            # The light rim along the clump's bottom.
            last = y
        else:
            misses += 1
            if misses > max(3, (bottom - top) // 60):
                break
    return last


def _ball_by_width(alpha: np.ndarray, bottom: int) -> int:
    """For a pale clump: up from the bottom while the picture stays wide; it narrows to the stem."""
    widths = alpha.sum(axis=1)
    widest = widths[max(0, bottom - 400):bottom + 1].max()
    last = bottom
    for y in range(bottom, 0, -1):
        if widths[y] >= widest * 0.3:
            last = y
        elif last - y > 6:
            break
    return last


def strip_soil_ball(image: Image.Image, withered: bool = False) -> Image.Image:
    """[image] with the clump of earth under the plant made transparent."""
    a = np.array(image.convert("RGBA"))
    alpha = a[..., 3] > 8
    if not alpha.any():
        return image.convert("RGBA")
    ys = np.where(alpha.any(axis=1))[0]
    bottom = int(ys.max())
    height = bottom - int(ys.min()) + 1

    top_row = _ball_by_colour(alpha, soil_mask(a[..., :3], dark_only=withered) & alpha)
    if withered and bottom - top_row < height * 0.04:
        top_row = _ball_by_width(alpha, bottom)
    soil = soil_mask(a[..., :3]) & alpha

    band = np.zeros_like(alpha)
    band[top_row:] = True
    remove = soil & band
    # The pale crumbs and halo round the clump: half see-through, or right at the picture's edge.
    # (A daikon's white root is opaque and inside the clump, so it stays.)
    rgb = a[..., :3].astype(int)
    pale = (rgb.min(axis=2) > 150) & ((rgb.max(axis=2) - rgb.min(axis=2)) < 40)
    near_edge = np.array(Image.fromarray((~alpha * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(13))) > 0
    remove |= band & alpha & pale & ((a[..., 3] < 230) | near_edge)
    # A little more of what is left of the clump's soft edge.
    grown = np.array(Image.fromarray((remove * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(5))) > 0
    remove |= grown & band & (soil | (a[..., 3] < 160))
    a[remove, 3] = 0
    # Stray crumbs left in the band, not joined to the plant above it, go too: otherwise they
    # hang below the stem and the picture's foot is not where the plant stands.
    kept = a[..., 3] > 8
    attached = _grow(kept & ~band, kept)
    a[band & kept & ~attached, 3] = 0
    return Image.fromarray(a)


def _grow(seed: np.ndarray, within: np.ndarray) -> np.ndarray:
    """Everything in [within] that is joined to [seed] (8 neighbours), by growing it step by step."""
    grown = seed & within
    while True:
        wider = grown.copy()
        wider[1:] |= grown[:-1]
        wider[:-1] |= grown[1:]
        wider[:, 1:] |= grown[:, :-1]
        wider[:, :-1] |= grown[:, 1:]
        wider[1:, 1:] |= grown[:-1, :-1]
        wider[1:, :-1] |= grown[:-1, 1:]
        wider[:-1, 1:] |= grown[1:, :-1]
        wider[:-1, :-1] |= grown[1:, 1:]
        wider &= within
        if (wider == grown).all():
            return grown
        grown = wider


def stem_base(image: Image.Image, alpha_threshold: int = 8) -> tuple[float, float]:
    """
    Where a cropped crop picture stands: the base of its stem, as fractions of the picture
    (anchorX, anchorY).

    The middle of the plant is taken from the bottom fifth of the picture (the median of its
    pixels across, so a leaf drooping to one side does not pull it over); the foot is the lowest
    pixel in a narrow strip round that middle. A drooping leaf may hang a little lower than the
    foot, as it would over the edge of a bed.
    """
    a = np.array(image.convert("RGBA"))[..., 3] > alpha_threshold
    h, w = a.shape
    ys = np.where(a.any(axis=1))[0]
    bottom = int(ys.max())
    low = a[max(0, bottom - h // 5):bottom + 1]
    xs = np.where(low)[1]
    cx = float(np.median(xs)) if len(xs) else w / 2
    half = max(2, int(w * 0.06))
    strip = a[:, max(0, int(cx) - half):min(w, int(cx) + half + 1)]
    rows = np.where(strip.any(axis=1))[0]
    foot = int(rows.max()) + 1 if len(rows) else h
    return round(cx / w, 4), round(min(1.0, foot / h), 4)


def _preview(raw: Path, out: Path, names: list[str]) -> None:
    tiles = []
    for name in names:
        source = Image.open(raw / f"{name}.png").convert("RGBA")
        for im in (source, strip_soil_ball(source, withered="withered" in name)):
            im = im.crop(im.getbbox())
            tiles.append(im.resize((max(1, int(im.width * 220 / im.height)), 220)))
    canvas = Image.new("RGBA", (sum(t.width + 8 for t in tiles), 240), (150, 120, 90, 255))
    x = 0
    for tile in tiles:
        canvas.alpha_composite(tile, (x, 10))
        x += tile.width + 8
    canvas.save(out)


if __name__ == "__main__":
    _preview(Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3:])
