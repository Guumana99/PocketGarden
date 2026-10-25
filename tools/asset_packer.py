#!/usr/bin/env python3
"""Crop transparent PNG sprites and pack them into texture atlas pages.

Takes a folder of PNGs with transparent margins (e.g. exported from Recraft.ai) and writes:

  <out>/<sheet>_<n>.png       the sprites, trimmed, packed into pages of at most --page-size square
  <out>/assets_manifest.json  every page, and where each sprite sits on which page, plus its anchor

The manifest is what the app's AssetManifest.kt / AtlasManifest reads; the app finds a sprite by
its assetId whatever page it is on. Write straight into app/src/main/assets/atlas/: page files of
the previous manifest that are no longer written are deleted.

Usage:
  python tools/asset_packer.py raw_assets/ app/src/main/assets/atlas/
  python tools/asset_packer.py raw_assets/ out/ --tile-width 256 --overrides raw_assets/overrides.json

The file name (without .png) becomes the assetId, e.g. plant_tulip_stage3.png -> "plant_tulip_stage3".

Sheets and pages
  A sprite's sheet follows from the start of its assetId (SHEETS): the crops (plant_*) on
  "crops", everything else (buildings, ground, decorations) on "world". There is nothing to
  choose when adding a picture: its name decides. Each sheet is packed tallest first onto pages
  of at most --page-size pixels a side (2048 by default, which every device can take as one
  texture); when one is full the next is started, crops_0.png, crops_1.png and so on. A page is
  cut to what is used on it. --sheet puts every sprite on one sheet of that name instead (the
  visitors' own atlas, "creatures").

Scaling
  --tile-width is how many atlas pixels one grid tile spans. If the source art is drawn larger
  than that, also pass --source-tile-width and every sprite is shrunk to match, e.g. art drawn
  at 720 px per tile packed into a lighter 512 px-per-tile atlas:

    python tools/asset_packer.py raw_assets/ out/ --tile-width 512 --source-tile-width 720

  Changing --tile-width alone does not resize anything; it only changes how large the sprites
  are drawn in the app.

Anchors
  The anchor is the point of the sprite that stands on the ground. After cropping it defaults
  to the bottom center (0.5, 1.0). Override it per sprite in the overrides file, e.g. for a
  ground tile whose anchor is the middle of its top face:

    {
      "tile_grass":   {"anchorX": 0.5, "anchorY": 0.5},
      "deco_signpost": {"isFlippedAllowed": false}
    }

  Override anchors are fractions of the *cropped* image, 0..1 from the top-left.

  Two more keys, written into the manifest only when given:
    "scale"              draw the sprite this many times larger than the atlas scale (for a
                         building whose picture is smaller than its footprint; enlarging it in
                         the atlas would only make the atlas bigger)
    "attachX", "attachY" a point on the sprite (fractions, like the anchor) where another sprite
                         is hung, e.g. the hub of a windmill's sails

Requires Pillow:  pip install Pillow
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path

from PIL import Image

import crop_soil

MANIFEST_VERSION = 2
MANIFEST_NAME = "assets_manifest.json"

#: Largest page, in pixels a side: a texture every device can take.
PAGE_SIZE = 2048

#: (sheet, assetId prefix): a sprite whose assetId starts with the prefix goes on that sheet...
SHEETS = (("crops", "plant_"),)
#: ...and any other on this one.
DEFAULT_SHEET = "world"

DEFAULT_ANCHOR_X = 0.5
DEFAULT_ANCHOR_Y = 1.0
OVERRIDE_KEYS = {"anchorX", "anchorY", "isFlippedAllowed", "scale", "attachX", "attachY"}


@dataclass
class Sprite:
    asset_id: str
    image: Image.Image  # cropped, RGBA
    anchor_x: float = DEFAULT_ANCHOR_X
    anchor_y: float = DEFAULT_ANCHOR_Y
    is_flipped_allowed: bool = True
    # How much larger than the atlas scale to draw it (a building that would otherwise have to
    # be enlarged in the atlas to cover its footprint).
    draw_scale: float = 1.0
    # A point on the sprite where another one is hung (the hub a windmill's sails turn on).
    attach_x: float | None = None
    attach_y: float | None = None
    sheet: str = DEFAULT_SHEET
    page: int = 0
    atlas_x: int = 0
    atlas_y: int = 0

    @property
    def width(self) -> int:
        return self.image.width

    @property
    def height(self) -> int:
        return self.image.height


def sheet_of(asset_id: str) -> str:
    """The sheet a sprite named [asset_id] is packed on (SHEETS)."""
    return next((sheet for sheet, prefix in SHEETS if asset_id.startswith(prefix)), DEFAULT_SHEET)


def crop_to_alpha(image: Image.Image, alpha_threshold: int = 0) -> Image.Image | None:
    """Trim to the smallest box containing every pixel with alpha > alpha_threshold.

    Returns None when the image has no such pixel.
    """
    rgba = image.convert("RGBA")
    mask = rgba.getchannel("A").point(lambda a: 255 if a > alpha_threshold else 0)
    box = mask.getbbox()
    return None if box is None else rgba.crop(box)


def scale_image(image: Image.Image, scale: float) -> Image.Image:
    """Resample an RGBA image by `scale`.

    The resize runs on premultiplied alpha: otherwise the colour stored in fully transparent
    pixels (usually white or black) bleeds into the sprite's edges as a halo.
    """
    if scale == 1.0:
        return image
    size = (max(1, round(image.width * scale)), max(1, round(image.height * scale)))
    return image.convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")


def load_sprites(
    input_dir: Path, alpha_threshold: int = 0, scale: float = 1.0, strip_crop_soil: bool = False,
) -> list[Sprite]:
    """Load, crop and (when scale != 1) resample every PNG in input_dir.

    With strip_crop_soil, a crop picture (plant_*) first has the clump of earth under it taken
    out (see crop_soil.py), and stands on the base of its stem rather than the default anchor.
    """
    sprites = []
    for path in sorted(input_dir.glob("*.png")):
        crop = strip_crop_soil and crop_soil.is_crop(path.stem)
        with Image.open(path) as source:
            if crop and crop_soil.has_soil(path.stem):
                source = crop_soil.strip_soil_ball(source, withered="withered" in path.stem)
            cropped = crop_to_alpha(source, alpha_threshold)
        if cropped is None:
            print(f"warning: {path.name} is fully transparent, skipped", file=sys.stderr)
            continue
        sprite = Sprite(asset_id=path.stem, image=scale_image(cropped, scale), sheet=sheet_of(path.stem))
        if crop:
            sprite.anchor_x, sprite.anchor_y = crop_soil.stem_base(sprite.image)
        sprites.append(sprite)
    return sprites


def apply_overrides(sprites: list[Sprite], overrides: dict) -> None:
    """Apply manual per-sprite anchors / flip flags. Raises ValueError on bad input."""
    by_id = {sprite.asset_id: sprite for sprite in sprites}
    for asset_id, values in overrides.items():
        if asset_id not in by_id:
            raise ValueError(f"override for unknown asset '{asset_id}'")
        unknown = set(values) - OVERRIDE_KEYS
        if unknown:
            raise ValueError(f"'{asset_id}': unknown override keys {sorted(unknown)}")
        sprite = by_id[asset_id]
        for key, attribute in (("anchorX", "anchor_x"), ("anchorY", "anchor_y")):
            if key in values:
                value = float(values[key])
                if not 0.0 <= value <= 1.0:
                    raise ValueError(f"'{asset_id}': {key} must be within 0..1, got {value}")
                setattr(sprite, attribute, value)
        if "isFlippedAllowed" in values:
            sprite.is_flipped_allowed = bool(values["isFlippedAllowed"])
        if "scale" in values:
            value = float(values["scale"])
            if not value > 0.0:
                raise ValueError(f"'{asset_id}': scale must be positive, got {value}")
            sprite.draw_scale = value
        if ("attachX" in values) != ("attachY" in values):
            raise ValueError(f"'{asset_id}': attachX and attachY go together")
        if "attachX" in values:
            sprite.attach_x, sprite.attach_y = float(values["attachX"]), float(values["attachY"])


@dataclass
class Page:
    sheet: str
    width: int
    height: int
    file: str = ""
    # The shelf being filled: where the next sprite goes on it, and how tall it is.
    x: int = 0
    y: int = 0
    shelf_height: int = 0


def pack(sprites: list[Sprite], page_size: int = PAGE_SIZE, padding: int = 2) -> list[Page]:
    """Assign each sprite a page and a place on it, with shelf packing (tallest first), sheet by
    sheet. Returns the pages in the order of their index, each cut to what is used on it.

    A sprite goes on the first page of its sheet with room for it, so that the shorter ones fill
    what is left at the foot of a page before the next is started.
    Every sprite keeps `padding` transparent pixels on all sides so that filtered sampling
    never bleeds a neighbour into it.
    """
    for sprite in sprites:
        if max(sprite.width, sprite.height) + 2 * padding > page_size:
            raise ValueError(f"'{sprite.asset_id}' is {sprite.width}x{sprite.height} and does not fit on a {page_size} px page")
    order = [sheet for sheet, _ in SHEETS] + [DEFAULT_SHEET]
    sheets = sorted({sprite.sheet for sprite in sprites}, key=lambda sheet: (order.index(sheet) if sheet in order else len(order), sheet))
    pages: list[Page] = []
    for sheet in sheets:
        first = len(pages)
        for sprite in sorted((s for s in sprites if s.sheet == sheet), key=lambda s: (-s.height, s.asset_id)):
            w, h = sprite.width, sprite.height
            for index in range(first, len(pages)):
                page = pages[index]
                if page.x + w + padding <= page_size and page.y + h + padding <= page_size:
                    break  # on the shelf being filled (never taller than it, tallest first)
                below = page.y + page.shelf_height + padding
                if padding + w + padding <= page_size and below + h + padding <= page_size:
                    page.x, page.y, page.shelf_height = padding, below, 0
                    break  # on a new shelf below it
            else:
                index = len(pages)
                page = Page(sheet, 0, 0, f"{sheet}_{index - first}.png", x=padding, y=padding)
                pages.append(page)
            sprite.page = index
            sprite.atlas_x, sprite.atlas_y = page.x, page.y
            page.x += w + padding
            page.shelf_height = max(page.shelf_height, h)
            page.width = max(page.width, page.x)
            page.height = max(page.height, page.y + page.shelf_height + padding)
    return pages


def build_page(sprites: list[Sprite], pages: list[Page], index: int) -> Image.Image:
    page = pages[index]
    image = Image.new("RGBA", (page.width, page.height), (0, 0, 0, 0))
    for sprite in sprites:
        if sprite.page == index:
            image.paste(sprite.image, (sprite.atlas_x, sprite.atlas_y))
    return image


def build_manifest(sprites: list[Sprite], pages: list[Page], tile_width: int) -> dict:
    return {
        "version": MANIFEST_VERSION,
        "tileWidth": tile_width,
        "pages": [{"file": page.file, "width": page.width, "height": page.height} for page in pages],
        "assets": [
            sprite_entry(sprite)
            for sprite in sorted(sprites, key=lambda s: s.asset_id)
        ],
    }


def sprite_entry(sprite: Sprite) -> dict:
    entry = {
        "assetId": sprite.asset_id,
        "anchorX": round(sprite.anchor_x, 4),
        "anchorY": round(sprite.anchor_y, 4),
        "width": sprite.width,
        "height": sprite.height,
        "isFlippedAllowed": sprite.is_flipped_allowed,
        "page": sprite.page,
        "atlasX": sprite.atlas_x,
        "atlasY": sprite.atlas_y,
    }
    # Only written when used, so that the manifest of a plain sprite stays as it was.
    if sprite.draw_scale != 1.0:
        entry["drawScale"] = round(sprite.draw_scale, 4)
    if sprite.attach_x is not None:
        entry["attachX"] = round(sprite.attach_x, 4)
        entry["attachY"] = round(sprite.attach_y, 4)
    return entry


def written_pages(manifest_path: Path) -> list[str]:
    """The page files a manifest written before names (version 1 had a single "atlas")."""
    if not manifest_path.is_file():
        return []
    old = json.loads(manifest_path.read_text(encoding="utf-8"))
    return [page["file"] for page in old.get("pages", [])] + ([old["atlas"]] if old.get("atlas") else [])


def run(
    input_dir: Path,
    output_dir: Path,
    *,
    tile_width: int = 256,
    source_tile_width: int | None = None,
    page_size: int = PAGE_SIZE,
    padding: int = 2,
    alpha_threshold: int = 0,
    overrides_path: Path | None = None,
    strip_crop_soil: bool = False,
    sheet: str | None = None,
    manifest_name: str = MANIFEST_NAME,
) -> dict:
    """Run the whole pipeline and return the manifest that was written.

    tile_width is how wide one grid tile is in the atlas. When the source art was drawn at a
    different size (source_tile_width), every sprite is resampled by tile_width / source_tile_width.
    sheet puts every sprite on that one sheet rather than the one its name gives (sheet_of), and
    manifest_name is the manifest written: the visitors have an atlas of their own
    (creatures_<n>.png, creatures_manifest.json; see prepare_creature_art.py).
    """
    if tile_width <= 0 or (source_tile_width is not None and source_tile_width <= 0):
        raise ValueError("tile widths must be positive")
    scale = 1.0 if source_tile_width is None else tile_width / source_tile_width
    sprites = load_sprites(input_dir, alpha_threshold, scale, strip_crop_soil)
    if not sprites:
        raise ValueError(f"no usable .png files in {input_dir}")
    if overrides_path is not None:
        apply_overrides(sprites, json.loads(overrides_path.read_text(encoding="utf-8")))

    if sheet is not None:
        for sprite in sprites:
            sprite.sheet = sheet

    pages = pack(sprites, page_size, padding)
    manifest = build_manifest(sprites, pages, tile_width)

    output_dir.mkdir(parents=True, exist_ok=True)
    stale = set(written_pages(output_dir / manifest_name)) - {page.file for page in pages}
    for index, page in enumerate(pages):
        build_page(sprites, pages, index).save(output_dir / page.file, optimize=True)
    for name in sorted(stale):
        (output_dir / name).unlink(missing_ok=True)
    (output_dir / manifest_name).write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return manifest


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Crop sprites and pack them into a texture atlas.")
    parser.add_argument("input_dir", type=Path, help="folder of transparent .png files")
    parser.add_argument("output_dir", type=Path, help=f"where the pages and {MANIFEST_NAME} are written")
    parser.add_argument("--tile-width", type=int, default=256,
                        help="pixel width of one grid tile in the atlas (default: 256)")
    parser.add_argument("--source-tile-width", type=int, default=None,
                        help="pixel width of one grid tile in the source art, if different from "
                             "--tile-width; sprites are resampled by tile-width / source-tile-width")
    parser.add_argument("--page-size", type=int, default=PAGE_SIZE,
                        help=f"largest page, in pixels a side (default: {PAGE_SIZE})")
    parser.add_argument("--padding", type=int, default=2, help="transparent pixels around each sprite (default: 2)")
    parser.add_argument("--alpha-threshold", type=int, default=0,
                        help="pixels with alpha at or below this (0-255) count as empty when cropping (default: 0)")
    parser.add_argument("--strip-crop-soil", action="store_true",
                        help="take the clump of earth out from under crop pictures (plant_*), see crop_soil.py")
    parser.add_argument("--overrides", type=Path, default=None,
                        help="JSON file with per-asset anchorX / anchorY / isFlippedAllowed")
    parser.add_argument("--sheet", default=None,
                        help="put every sprite on this one sheet, rather than by its name (e.g. creatures)")
    parser.add_argument("--manifest-name", default=MANIFEST_NAME, help=f"manifest file name (default: {MANIFEST_NAME})")
    args = parser.parse_args(argv)

    try:
        manifest = run(
            args.input_dir,
            args.output_dir,
            tile_width=args.tile_width,
            source_tile_width=args.source_tile_width,
            page_size=args.page_size,
            padding=args.padding,
            alpha_threshold=args.alpha_threshold,
            overrides_path=args.overrides,
            strip_crop_soil=args.strip_crop_soil,
            sheet=args.sheet,
            manifest_name=args.manifest_name,
        )
    except (OSError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1

    pages = ", ".join(f"{page['file']} {page['width']}x{page['height']}" for page in manifest["pages"])
    print(f"packed {len(manifest['assets'])} sprites into {len(manifest['pages'])} pages: {pages}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
