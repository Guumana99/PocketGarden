"""Estimate a crop sprite's ground point: the middle of the soil mound's base.

The mound is a squat cone seen from the isometric angle; its base is an ellipse twice as wide as
it is deep. Its front edge is the lowest opaque pixel, so the middle of the base sits a quarter
of the base's width above that. The base width is read off the silhouette just above the bottom.
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image


def estimate(path, band=0.06):
    rgba = np.array(Image.open(path).convert("RGBA"))
    a = rgba[:, :, 3] > 8
    ys, xs = np.where(a)
    top, bottom, left, right = ys.min(), ys.max(), xs.min(), xs.max()
    a = a[top:bottom + 1, left:right + 1]
    rgb = rgba[top:bottom + 1, left:right + 1, :3].astype(int)
    # The mound is dark soil. Pale wisps left round it by the background removal (white
    # "roots") are not ground; they would make the mound look wider and lower than it is.
    a = a & (rgb.sum(axis=2) < 3 * 150)
    h, w = a.shape
    best = 0
    centers = []
    for y in range(int(h * (1 - band * 3)), h):
        row = np.where(a[y])[0]
        if len(row) == 0:
            continue
        # The longest run of opaque pixels: the mound, not a stray leaf tip.
        runs = np.split(row, np.where(np.diff(row) > 3)[0] + 1)
        run = max(runs, key=len)
        width = run[-1] - run[0]
        if width > best:
            best = width
        centers.append((run[0] + run[-1]) / 2)
    soil_rows = np.where(a.any(axis=1))[0]
    floor = soil_rows.max() if len(soil_rows) else h - 1
    anchor_y = (floor - best / 4) / h
    anchor_x = float(np.median(centers[-max(1, len(centers) // 3):])) / w
    return round(anchor_x, 3), round(anchor_y, 3), h, w


if __name__ == "__main__":
    raw = Path(sys.argv[1])
    overrides = json.loads(Path(sys.argv[2]).read_text())
    names = sys.argv[3:] or sorted(k for k in overrides if k.startswith("plant_"))
    for n in names:
        ax, ay, h, w = estimate(raw / f"{n}.png")
        o = overrides.get(n, {})
        print(f"{n:30s} est=({ax:.3f},{ay:.3f})  set=({o.get('anchorX', '-')},{o.get('anchorY', '-')})  h={h} w={w}")
