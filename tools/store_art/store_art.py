"""Makes the Play Store art: the feature graphic and the phone screenshots (tools/store_art/README.md).

Everything but the game itself is drawn here, in code: the sky, the paper's grain, the soft
specks of light, the leaves and petals at the edges, the wooden table. The words, the colours and
which game screens are used come from config.yaml, so that a season or an event can swap them.

    python tools/store_art/store_art.py feature            # docs/release/store/feature_graphic_<A|B>.png
    python tools/store_art/store_art.py shots --frame A B  # docs/release/store/screenshot_01_<A|B>.png
    python tools/store_art/store_art.py shots --frame A --final   # screenshot_01.png ... (the chosen frame)

Output: PNG, 24-bit RGB (no transparency).
"""
from __future__ import annotations

import argparse
import math
import random
from pathlib import Path

import numpy as np
import yaml
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
OUT = ROOT / "docs/release/store"
RAW = ROOT / "build/store_art/raw"

FEATURE_SIZE = (1024, 500)
SHOT_SIZE = (1080, 1920)


# --- Colours and type --------------------------------------------------------------------------

def rgb(hex_colour: str) -> tuple[int, int, int]:
    h = hex_colour.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def mix(a, b, t: float):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def font(config: dict, size: int, weight: str = "Bold") -> ImageFont.FreeTypeFont:
    f = ImageFont.truetype(str(ROOT / config["font"]), size)
    f.set_variation_by_name(weight)
    return f


def text_width(f: ImageFont.FreeTypeFont, text: str, spacing: float = 0.0) -> float:
    return sum(f.getlength(ch) for ch in text) + spacing * max(len(text) - 1, 0)


def draw_text(img: Image.Image, xy, text: str, f, fill, spacing: float = 0.0, glow=None, glow_radius: int = 10) -> None:
    """Text from its top-left [xy], letter by letter with [spacing]; a soft halo of [glow] behind it."""
    layer = Image.new("L", img.size, 0)
    d = ImageDraw.Draw(layer)
    x, y = xy
    for ch in text:
        d.text((x, y), ch, font=f, fill=255)
        x += f.getlength(ch) + spacing
    if glow is not None:
        halo = layer.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.GaussianBlur(glow_radius))
        halo = halo.point(lambda v: min(255, int(v * 0.85)))
        img.paste(Image.new("RGB", img.size, glow), (0, 0), halo)
    img.paste(Image.new("RGB", img.size, fill), (0, 0), layer)


# --- Drawn things: paper, light, leaves, petals, wood -------------------------------------------

def vertical_gradient(size, top, bottom) -> Image.Image:
    w, h = size
    t = np.linspace(0, 1, h)[:, None, None]
    arr = np.array(top)[None, None, :] * (1 - t) + np.array(bottom)[None, None, :] * t
    return Image.fromarray(np.repeat(arr, w, axis=1).astype(np.uint8))


def smooth_noise(size, scale: int, rng: np.random.Generator) -> np.ndarray:
    """Noise in 0..1 that changes slowly (every [scale] pixels or so)."""
    w, h = size
    small = rng.random((max(2, h // scale), max(2, w // scale)))
    return np.asarray(Image.fromarray((small * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC), dtype=np.float32) / 255


def paper(img: Image.Image, rng: np.random.Generator, strength: float = 1.0) -> Image.Image:
    """A watercolour paper's grain and blotches over [img], a few percent either way."""
    arr = np.asarray(img, dtype=np.float32)
    w, h = img.size
    blotch = smooth_noise((w, h), 90, rng) - 0.5
    blotch2 = smooth_noise((w, h), 26, rng) - 0.5
    grain = rng.normal(0, 1, (h, w)).astype(np.float32)
    grain = np.asarray(Image.fromarray(((grain * 18) + 128).clip(0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.7)), dtype=np.float32) / 255 - 0.5
    factor = 1 + strength * (blotch * 0.05 + blotch2 * 0.025 + grain * 0.035)
    return Image.fromarray((arr * factor[:, :, None]).clip(0, 255).astype(np.uint8))


def light_specks(img: Image.Image, rng: random.Random, count: int, colour, size_range, area=None, alpha_range=(0.10, 0.28)) -> None:
    """Out-of-focus specks of light (bokeh): soft discs, each drawn on its own small patch."""
    w, h = img.size
    x0, y0, x1, y1 = area or (0, 0, w, h)
    for _ in range(count):
        r = rng.uniform(*size_range)
        cx, cy = rng.uniform(x0, x1), rng.uniform(y0, y1)
        a = rng.uniform(*alpha_range)
        blur = r * 0.35
        pad = int(r + blur * 3) + 2
        patch = Image.new("L", (pad * 2, pad * 2), 0)
        ImageDraw.Draw(patch).ellipse((pad - r, pad - r, pad + r, pad + r), fill=int(255 * a))
        patch = patch.filter(ImageFilter.GaussianBlur(blur))
        img.paste(Image.new("RGBA", patch.size, colour + (255,)), (int(cx) - pad, int(cy) - pad), patch)


def leaf_polygon(cx, cy, length, width, angle, n=24):
    """A leaf's outline: two arcs meeting at a point, from its stalk at (cx, cy) along [angle]."""
    pts_top, pts_bottom = [], []
    for i in range(n + 1):
        t = i / n
        along = t * length
        across = width / 2 * math.sin(math.pi * t) * (1 - 0.25 * t)
        pts_top.append((along, across))
        pts_bottom.append((along, -across))
    pts = pts_top + pts_bottom[::-1]
    ca, sa = math.cos(angle), math.sin(angle)
    return [(cx + x * ca - y * sa, cy + x * sa + y * ca) for x, y in pts]


def _patch(layer: Image.Image, points, draw, blur: float, scale: int = 3) -> None:
    """Draws on a small patch round [points] at [scale] times the size, shrinks it and lays it on [layer].

    [draw] gets an ImageDraw and a function mapping layer coordinates onto the patch.
    """
    xs, ys = [p[0] for p in points], [p[1] for p in points]
    margin = int(blur * 3) + 4
    x0, y0 = int(min(xs)) - margin, int(min(ys)) - margin
    x1, y1 = int(max(xs)) + margin + 1, int(max(ys)) + margin + 1
    w, h = x1 - x0, y1 - y0
    big = Image.new("RGBA", (w * scale, h * scale), (0, 0, 0, 0))
    draw(ImageDraw.Draw(big), lambda x, y: ((x - x0) * scale, (y - y0) * scale))
    small = big.resize((w, h), Image.LANCZOS)
    if blur:
        small = small.filter(ImageFilter.GaussianBlur(blur))
    layer.alpha_composite(small, (x0, y0)) if x0 >= 0 and y0 >= 0 and x1 <= layer.width and y1 <= layer.height else _composite_clipped(layer, small, x0, y0)


def _composite_clipped(layer: Image.Image, patch: Image.Image, x0: int, y0: int) -> None:
    full = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    full.paste(patch, (x0, y0))
    layer.alpha_composite(full)


def draw_leaf(layer: Image.Image, cx, cy, length, width, angle, colour, vein, blur: float = 0.0) -> None:
    """One leaf, with its far half a shade darker (light from the top-left) and a lighter midrib."""
    outline = leaf_polygon(cx, cy, length, width, angle)
    tip = (cx + length * math.cos(angle), cy + length * math.sin(angle))
    rib = (cx + length * 0.92 * math.cos(angle), cy + length * 0.92 * math.sin(angle))

    def draw(d, at):
        d.polygon([at(x, y) for x, y in outline], fill=colour + (235,))
        d.polygon([at(x, y) for x, y in outline[:25] + [tip]], fill=mix(colour, (40, 60, 30), 0.12) + (235,))
        d.line([at(cx, cy), at(*rib)], fill=vein + (170,), width=max(2, int(width * 0.06 * 3)))

    _patch(layer, outline, draw, blur)


def draw_petal(layer: Image.Image, cx, cy, r, angle, colour, blur: float = 0.0) -> None:
    """A cherry-blossom petal: a rounded teardrop with a small notch, faintly darker at the base."""
    pts = []
    ca, sa = math.cos(angle), math.sin(angle)
    for i in range(40):
        t = i / 40 * 2 * math.pi
        rr = r * (0.75 + 0.25 * math.cos(t)) * (1 - 0.18 * math.exp(-(t ** 2) * 30) - 0.18 * math.exp(-((t - 2 * math.pi) ** 2) * 30))
        x, y = rr * math.cos(t), rr * math.sin(t) * 0.62
        pts.append((cx + x * ca - y * sa, cy + x * sa + y * ca))
    base = (cx - r * 0.55 * ca, cy - r * 0.55 * sa)

    def draw(d, at):
        d.polygon([at(x, y) for x, y in pts], fill=colour + (225,))
        (bx0, by0), (bx1, by1) = at(base[0] - r * 0.2, base[1] - r * 0.2), at(base[0] + r * 0.2, base[1] + r * 0.2)
        d.ellipse((bx0, by0, bx1, by1), fill=mix(colour, (200, 110, 130), 0.35) + (120,))

    _patch(layer, pts, draw, blur)


def sprig(layer, x, y, angle, scale, palette, rng: random.Random, blur=0.0) -> None:
    """A small twig with leaves along it, reaching in from an edge at [angle]."""
    leaf, leaf_dark = rgb(palette["leaf"]), rgb(palette["leaf_dark"])
    stem = (122, 96, 62)
    length = 260 * scale
    end = (x + length * math.cos(angle), y + length * math.sin(angle))
    _patch(layer, [(x, y), end], lambda d, at: d.line([at(x, y), at(*end)], fill=stem + (230,), width=max(2, int(7 * scale * 3))), blur)
    for i in range(5):
        t = 0.2 + i * 0.18
        px, py = x + length * t * math.cos(angle), y + length * t * math.sin(angle)
        side = 1 if i % 2 == 0 else -1
        a = angle + side * rng.uniform(0.6, 0.95)
        colour = mix(leaf, leaf_dark, rng.uniform(0, 0.6))
        draw_leaf(layer, px, py, rng.uniform(90, 120) * scale, rng.uniform(42, 54) * scale, a, colour, mix(colour, (240, 245, 220), 0.45), blur)
    draw_leaf(layer, end[0], end[1], 100 * scale, 46 * scale, angle, leaf, mix(leaf, (240, 245, 220), 0.45), blur)


def wood(size, rng: np.random.Generator, light=(228, 196, 150), dark=(178, 132, 86), plank: int = 120) -> Image.Image:
    """A pale, warm tabletop: planks running across, with grain and soft seams."""
    w, h = size
    long_noise = np.asarray(Image.fromarray((rng.random((h // 2, max(2, w // 60))) * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC), dtype=np.float32) / 255
    y = np.arange(h, dtype=np.float32)[:, None]
    grain = 0.5 + 0.5 * np.sin((y / 7.0 + long_noise * 9.0) * 1.0)
    rings = smooth_noise(size, 140, rng)
    t = (0.55 * grain + 0.45 * rings) * 0.55
    # Each plank its own shade.
    shade = np.zeros((h, 1), dtype=np.float32)
    for top in range(0, h, plank):
        shade[top: top + plank] = rng.uniform(-0.08, 0.08)
    t = (t + shade).clip(0, 1)
    arr = np.array(light, dtype=np.float32)[None, None, :] * (1 - t[:, :, None]) + np.array(dark, dtype=np.float32)[None, None, :] * t[:, :, None]
    img = Image.fromarray(arr.clip(0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    for top in range(plank, h, plank):
        d.line([(0, top), (w, top)], fill=mix(dark, (90, 60, 35), 0.35), width=2)
        d.line([(0, top + 2), (w, top + 2)], fill=mix(light, (255, 255, 255), 0.3), width=1)
    return img.filter(ImageFilter.GaussianBlur(0.6))


def radial(size, centre, radius, inner: float, outer: float) -> np.ndarray:
    w, h = size
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.sqrt((xx - centre[0]) ** 2 + (yy - centre[1]) ** 2) / radius
    t = np.clip(d, 0, 1)
    t = t * t * (3 - 2 * t)
    return inner * (1 - t) + outer * t


# --- The garden cut out of a promo frame ----------------------------------------------------------

def cut_out_garden(path: Path) -> Image.Image:
    """The garden alone, transparent around it, from a `backdrop: none` recording.

    The time of day lays a faint veil of one colour over the whole frame (the morning's warm light,
    alpha ~26): taken off where there is no garden, kept over the garden (it is the game's light).
    """
    im = np.asarray(Image.open(path).convert("RGBA"), dtype=np.float32) / 255
    rgbc, a = im[:, :, :3], im[:, :, 3]
    veil_a = float(np.median(np.concatenate([a[:8, :8].ravel(), a[-8:, -8:].ravel()])))
    veil = np.median(rgbc[:8, :8].reshape(-1, 3), axis=0)
    if veil_a > 0.5:
        raise SystemExit(f"{path}: not a backdrop: none frame (alpha {veil_a:.2f} round the edge)")
    garden_a = np.clip((a - veil_a) / (1 - veil_a), 0, 1)
    premult = rgbc * a[:, :, None] - veil[None, None, :] * veil_a * (1 - garden_a[:, :, None])
    colour = np.where(garden_a[:, :, None] > 1e-3, premult / np.maximum(garden_a[:, :, None], 1e-3), 0)
    out = np.dstack([np.clip(colour, 0, 1), garden_a])
    img = Image.fromarray((out * 255).round().astype(np.uint8), "RGBA")
    box = img.getchannel("A").point(lambda v: 255 if v > 6 else 0).getbbox()
    return img.crop(box)


def with_shadow(garden: Image.Image, drop: float, blur: float, opacity: float, squash: float = 0.35, colour=(70, 52, 30)) -> tuple[Image.Image, tuple[int, int]]:
    """The garden over a soft shadow cast [drop] (of its height) below it; and where the garden sits in the result."""
    w, h = garden.size
    pad = int(blur * 3 + drop * h + 10)
    canvas = Image.new("RGBA", (w + pad * 2, h + pad * 2), (0, 0, 0, 0))
    # The shadow is the garden's footprint, flattened towards its base.
    mask = garden.getchannel("A")
    flat = mask.resize((w, max(1, int(h * squash))), Image.LANCZOS)
    shadow_mask = Image.new("L", canvas.size, 0)
    shadow_mask.paste(flat, (pad, pad + h - flat.height + int(drop * h)))
    shadow_mask = shadow_mask.filter(ImageFilter.GaussianBlur(blur)).point(lambda v: int(v * opacity))
    canvas.paste(Image.new("RGBA", canvas.size, colour + (255,)), (0, 0), shadow_mask)
    canvas.alpha_composite(garden, (pad, pad))
    return canvas, (pad, pad)


# --- Backgrounds ---------------------------------------------------------------------------------

def sky_background(size, palette, seed: int, tint=None, leaves=True, specks=True) -> Image.Image:
    rng_np = np.random.default_rng(seed)
    rng = random.Random(seed)
    w, h = size
    bottom = rgb(tint) if tint else rgb(palette["sky_bottom"])
    img = vertical_gradient(size, rgb(palette["sky_top"]), bottom)
    # Morning light from the top-left.
    glow = radial(size, (w * 0.18, -h * 0.1), max(w, h) * 0.9, 0.22, 0.0)
    arr = np.asarray(img, dtype=np.float32)
    arr = arr * (1 - glow[:, :, None]) + np.array(rgb(palette["glow"]), dtype=np.float32) * glow[:, :, None]
    img = paper(Image.fromarray(arr.clip(0, 255).astype(np.uint8)), rng_np)
    img = img.convert("RGBA")
    if specks:
        light_specks(img, rng, int(w * h / 26000), rgb(palette["glow"]), (w / 110, w / 40), alpha_range=(0.25, 0.5))
        light_specks(img, rng, int(w * h / 60000), (255, 255, 255), (w / 60, w / 28), alpha_range=(0.12, 0.25))
    if leaves:
        edge_greenery(img, palette, rng)
    return img.convert("RGB")


def edge_greenery(img: Image.Image, palette, rng: random.Random, scale: float | None = None) -> None:
    """A few sprigs reaching in from the corners and petals drifting near the edges; the far ones blurred."""
    w, h = img.size
    sc = scale or min(w, h) / 900
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    sprig(layer, -20 * sc, h * 0.08, 0.35, 1.05 * sc, palette, rng, blur=1.6 * sc)
    sprig(layer, w + 20 * sc, h * 0.9, math.pi + 0.45, 1.15 * sc, palette, rng, blur=1.2 * sc)
    sprig(layer, w * 0.97, -30 * sc, math.pi * 0.62, 0.8 * sc, palette, rng, blur=3.0 * sc)
    petal = rgb(palette["petal"])
    for _ in range(9):
        side = rng.random()
        if side < 0.5:
            x, y = rng.uniform(0, w * 0.16), rng.uniform(0, h)
        else:
            x, y = rng.uniform(w * 0.84, w), rng.uniform(0, h)
        draw_petal(layer, x, y, rng.uniform(13, 22) * sc, rng.uniform(0, 2 * math.pi), mix(petal, (255, 255, 255), rng.uniform(0, 0.3)), blur=rng.choice([0, 0, 1.5, 3]) * sc)
    img.alpha_composite(layer)


def table_background(size, palette, seed: int) -> Image.Image:
    rng_np = np.random.default_rng(seed)
    rng = random.Random(seed)
    w, h = size
    img = wood(size, rng_np, plank=int(h / 4.2))
    arr = np.asarray(img, dtype=np.float32)
    # A pool of soft window light on the table, and the edges a little darker.
    light = radial(size, (w * 0.42, h * 0.38), w * 0.75, 0.28, 0.0)
    shade = radial(size, (w * 0.5, h * 0.5), w * 0.8, 0.0, 0.16)
    arr = arr * (1 - light[:, :, None]) + np.array(rgb(palette["cream"]), dtype=np.float32) * light[:, :, None]
    arr = arr * (1 - shade[:, :, None])
    img = paper(Image.fromarray(arr.clip(0, 255).astype(np.uint8)), rng_np, strength=0.6).convert("RGBA")
    # Petals and a leaf or two fallen on the table.
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    sc = min(w, h) / 900
    petal = rgb(palette["petal"])
    for x, y in [(w * 0.06, h * 0.86), (w * 0.12, h * 0.93), (w * 0.93, h * 0.14), (w * 0.88, h * 0.9), (w * 0.95, h * 0.78), (w * 0.04, h * 0.2)]:
        draw_petal(layer, x, y, rng.uniform(14, 20) * sc, rng.uniform(0, 6.28), mix(petal, (255, 255, 255), rng.uniform(0, 0.25)))
    draw_leaf(layer, w * 0.97, h * 0.55, 110 * sc, 50 * sc, math.pi + 0.5, rgb(palette["leaf"]), (230, 240, 210))
    draw_leaf(layer, w * 0.02, h * 0.6, 95 * sc, 44 * sc, -0.4, rgb(palette["leaf_dark"]), (220, 235, 200))
    # A soft shadow under each, as things lying on a table have.
    shadow = layer.getchannel("A").filter(ImageFilter.GaussianBlur(4 * sc)).point(lambda v: int(v * 0.22))
    shadow_img = Image.new("RGBA", img.size, (90, 60, 30, 255))
    img.paste(shadow_img, (int(3 * sc), int(5 * sc)), shadow)
    img.alpha_composite(layer)
    return img.convert("RGB")


# --- The feature graphic -------------------------------------------------------------------------

def feature(config: dict, variant: str) -> Image.Image:
    spec = config["feature"]
    palette = config["palette"]
    w, h = FEATURE_SIZE
    kind = spec["variants"][variant]["background"]
    if kind == "table":
        base = table_background(FEATURE_SIZE, palette, seed=31)
    else:
        base = sky_background(FEATURE_SIZE, palette, seed=17)
    img = base.convert("RGBA")

    # Safe area: 15 % top, 20 % each side, 20 % bottom stay background.
    safe = (int(w * 0.20), int(h * 0.15), int(w * 0.80), int(h * 0.80))

    garden = cut_out_garden(ROOT / spec["garden"])
    target_w = int(w * spec.get("garden_width", 0.30))
    garden = garden.resize((target_w, round(garden.height * target_w / garden.width)), Image.LANCZOS)
    if kind == "table":
        framed, (ox, oy) = with_shadow(garden, drop=0.02, blur=10, opacity=0.38, squash=0.55)
    else:
        framed, (ox, oy) = with_shadow(garden, drop=0.10, blur=16, opacity=0.22, squash=0.45, colour=(70, 80, 70))
    gx = safe[2] - garden.width - int(w * 0.005)
    gy = (safe[1] + safe[3]) // 2 - garden.height // 2 + int(h * 0.01)
    img.alpha_composite(framed, (gx - ox, gy - oy))

    # The name, large, with a little leaf over it; the line under it smaller, in two lines.
    ink, ink_soft = rgb(palette["ink"]), rgb(palette["ink_soft"])
    halo = rgb(palette["cream"])
    title = spec["title"]
    title_font = font(config, 46, "Black")
    tracking = 2
    tx = safe[0] + 4
    ty = safe[1] + int(h * 0.10)
    rgb_img = img.convert("RGB")
    draw_text(rgb_img, (tx, ty), title, title_font, ink, spacing=tracking, glow=halo, glow_radius=12)
    leaf_layer = Image.new("RGBA", rgb_img.size, (0, 0, 0, 0))
    draw_leaf(leaf_layer, tx + text_width(title_font, title, tracking) - 6, ty + 6, 34, 17, -0.9, rgb(palette["leaf"]), (235, 245, 220))
    draw_leaf(leaf_layer, tx + text_width(title_font, title, tracking) - 6, ty + 6, 26, 13, -2.2, rgb(palette["leaf_dark"]), (225, 238, 210))
    rgb_img = rgb_img.convert("RGBA")
    rgb_img.alpha_composite(leaf_layer)
    rgb_img = rgb_img.convert("RGB")
    lines = spec["tagline"].split("、", 1)
    lines = [lines[0] + "、", lines[1]] if len(lines) == 2 else lines
    tag_font = font(config, 21, "Bold")
    y = ty + 72
    for line in lines:
        draw_text(rgb_img, (tx + 2, y), line, tag_font, ink_soft, spacing=1, glow=halo, glow_radius=8)
        y += 32
    return rgb_img


# --- The screenshots ------------------------------------------------------------------------------

def rounded_mask(size, radius: int, scale: int = 3) -> Image.Image:
    w, h = size
    big = Image.new("L", (w * scale, h * scale), 0)
    ImageDraw.Draw(big).rounded_rectangle((0, 0, w * scale - 1, h * scale - 1), radius=radius * scale, fill=255)
    return big.resize(size, Image.LANCZOS)


def soft_shadow(size, box, radius, blur, opacity, offset=(0, 14), colour=(60, 45, 30)) -> Image.Image:
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    mask = Image.new("L", size, 0)
    x0, y0, x1, y1 = box
    ImageDraw.Draw(mask).rounded_rectangle((x0 + offset[0], y0 + offset[1], x1 + offset[0], y1 + offset[1]), radius=radius, fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(blur)).point(lambda v: int(v * opacity))
    layer.paste(Image.new("RGBA", size, colour + (255,)), (0, 0), mask)
    return layer


def panorama(config: dict, count: int) -> Image.Image:
    """One long background for all the screenshots side by side, so that they run on into each other."""
    palette = config["palette"]
    tints = config["screenshots"]["tints"]
    w, h = SHOT_SIZE
    total = (w * count, h)
    rng_np = np.random.default_rng(5)
    rng = random.Random(5)
    # The sky's lower colour drifts from one season's to the next across the strip.
    xs = np.linspace(0, len(tints) - 1, total[0])
    cols = np.array([rgb(t) for t in tints], dtype=np.float32)
    i0 = np.floor(xs).astype(int).clip(0, len(tints) - 1)
    i1 = (i0 + 1).clip(0, len(tints) - 1)
    f = (xs - i0)[:, None]
    bottom = cols[i0] * (1 - f) + cols[i1] * f
    top = np.array(rgb(palette["sky_top"]), dtype=np.float32)
    t = np.linspace(0, 1, h)[:, None, None]
    arr = top[None, None, :] * (1 - t) + bottom[None, :, :] * t
    img = paper(Image.fromarray(arr.clip(0, 255).astype(np.uint8)), rng_np).convert("RGBA")
    light_specks(img, rng, 14 * count, rgb(palette["glow"]), (20, 50), alpha_range=(0.25, 0.5))
    light_specks(img, rng, 5 * count, (255, 255, 255), (36, 70), alpha_range=(0.12, 0.22))
    # Sprigs on the seams between pictures (half on each), and one at each outer end.
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    sc = 1.0
    for k in range(count + 1):
        x = k * w
        if k % 2 == 0:
            sprig(layer, x - 120, h * 0.50, -0.35, 0.9 * sc, palette, rng, blur=1.5)
        else:
            sprig(layer, x + 110, h * 0.97, math.pi + 0.55, 0.95 * sc, palette, rng, blur=1.2)
    petal = rgb(palette["petal"])
    for k in range(count):
        for _ in range(4):
            x = k * w + rng.choice([rng.uniform(10, 110), rng.uniform(w - 110, w - 10)])
            draw_petal(layer, x, rng.uniform(h * 0.25, h * 0.95), rng.uniform(14, 22), rng.uniform(0, 6.28),
                       mix(petal, (255, 255, 255), rng.uniform(0, 0.3)), blur=rng.choice([0, 1.5, 3]))
    img.alpha_composite(layer)
    return img.convert("RGB")


def screenshot(config: dict, item: dict, background: Image.Image, frame: str) -> Image.Image:
    palette = config["palette"]
    w, h = SHOT_SIZE
    img = background.convert("RGBA")
    screen = Image.open(RAW / f"{item['capture']}.png").convert("RGB")

    # The heading: up to two lines in the top 20 %.
    head_h = int(h * 0.20)
    lines = item["title"].split("\n")[:2]
    size = 76 if len(lines) == 1 else 66
    f = font(config, size, "Black")
    for limit in range(size, 40, -2):
        f = font(config, limit, "Black")
        if max(text_width(f, ln, 3) for ln in lines) <= w - 140:
            break
    line_h = int(f.size * 1.32)
    y = (head_h - line_h * len(lines)) // 2 + 18
    rgb_img = img.convert("RGB")
    for ln in lines:
        x = (w - text_width(f, ln, 3)) / 2
        draw_text(rgb_img, (x, y), ln, f, rgb(palette["ink"]), spacing=3, glow=rgb(palette["cream"]), glow_radius=14)
        y += line_h
    img = rgb_img.convert("RGBA")

    # The game's screen below it.
    area_top, area_bottom = head_h + 10, h - 40
    if frame == "A":
        bezel, outer_r = 26, 78
        sh = area_bottom - area_top
        sw = round((sh - bezel * 2) * screen.width / screen.height) + bezel * 2
        x0 = (w - sw) // 2
        box = (x0, area_top, x0 + sw, area_top + sh)
        img.alpha_composite(soft_shadow(img.size, box, outer_r, 26, 0.32))
        body = Image.new("RGBA", (sw, sh), (24, 24, 26, 255))
        # A faint rim of light round the body's edge.
        rim = Image.new("RGBA", (sw, sh), (0, 0, 0, 0))
        ImageDraw.Draw(rim).rounded_rectangle((1, 1, sw - 2, sh - 2), radius=outer_r - 1, outline=(92, 92, 96, 255), width=3)
        body.alpha_composite(rim)
        inner = (sw - bezel * 2, sh - bezel * 2)
        shot = screen.resize(inner, Image.LANCZOS)
        body.paste(shot, (bezel, bezel), rounded_mask(inner, outer_r - bezel))
        img.paste(body, box[:2], rounded_mask((sw, sh), outer_r))
    else:
        radius = 54
        sh = area_bottom - area_top
        sw = round(sh * screen.width / screen.height)
        x0 = (w - sw) // 2
        box = (x0, area_top, x0 + sw, area_top + sh)
        img.alpha_composite(soft_shadow(img.size, box, radius, 24, 0.28))
        shot = screen.resize((sw, sh), Image.LANCZOS)
        img.paste(shot, box[:2], rounded_mask((sw, sh), radius))
        # A thin cream edge, so the screen reads as a card.
        edge = Image.new("RGBA", img.size, (0, 0, 0, 0))
        ImageDraw.Draw(edge).rounded_rectangle(box, radius=radius, outline=rgb(palette["cream"]) + (200,), width=4)
        img.alpha_composite(edge)
    return img.convert("RGB")


def save(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGB").save(path, optimize=True)
    print("wrote", path.relative_to(ROOT), img.size, img.mode)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("what", choices=["feature", "shots"])
    ap.add_argument("--config", default=str(HERE / "config.yaml"))
    ap.add_argument("--variant", nargs="*", help="feature: A B (default all)")
    ap.add_argument("--frame", nargs="*", default=["A", "B"], help="shots: A (phone frame) and/or B (rounded screen)")
    ap.add_argument("--final", action="store_true", help="shots: write screenshot_NN.png with the one --frame")
    args = ap.parse_args()
    config = yaml.safe_load(Path(args.config).read_text(encoding="utf-8"))
    if args.what == "feature":
        for v in args.variant or list(config["feature"]["variants"]):
            save(feature(config, v), OUT / f"feature_graphic_{v}.png")
        return
    items = config["screenshots"]["items"]
    count = max(6, max(it["n"] for it in items))
    strip = panorama(config, count)
    w, h = SHOT_SIZE
    for item in items:
        bg = strip.crop(((item["n"] - 1) * w, 0, item["n"] * w, h))
        for frame in args.frame:
            name = f"screenshot_{item['n']:02d}.png" if args.final else f"screenshot_{item['n']:02d}_{frame}.png"
            save(screenshot(config, item, bg, frame), OUT / name)


if __name__ == "__main__":
    main()
