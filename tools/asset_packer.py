#!/usr/bin/env python3
"""Crop transparent PNG sprites and pack them into one texture atlas.

Takes a folder of PNGs with transparent margins (e.g. exported from Recraft.ai) and writes:

  <out>/atlas.png             all sprites, trimmed, packed into one image
  <out>/assets_manifest.json  where each sprite sits in the atlas, plus its anchor point

The manifest is what the app's AssetManifest.kt / AtlasManifest reads. Copy both files to
app/src/main/assets/atlas/ to use them in the app.

Usage:
  python tools/asset_packer.py raw_assets/ app/src/main/assets/atlas/
  python tools/asset_packer.py raw_assets/ out/ --tile-width 256 --overrides raw_assets/overrides.json

The file name (without .png) becomes the assetId, e.g. plant_tulip_stage3.png -> "plant_tulip_stage3".

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

Requires Pillow:  pip install Pillow
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path

from PIL import Image

MANIFEST_VERSION = 1
MANIFEST_NAME = "assets_manifest.json"
ATLAS_NAME = "atlas.png"

DEFAULT_ANCHOR_X = 0.5
DEFAULT_ANCHOR_Y = 1.0
OVERRIDE_KEYS = {"anchorX", "anchorY", "isFlippedAllowed"}


@dataclass
class Sprite:
    asset_id: str
    image: Image.Image  # cropped, RGBA
    anchor_x: float = DEFAULT_ANCHOR_X
    anchor_y: float = DEFAULT_ANCHOR_Y
    is_flipped_allowed: bool = True
    atlas_x: int = 0
    atlas_y: int = 0

    @property
    def width(self) -> int:
        return self.image.width

    @property
    def height(self) -> int:
        return self.image.height


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


def load_sprites(input_dir: Path, alpha_threshold: int = 0, scale: float = 1.0) -> list[Sprite]:
    """Load, crop and (when scale != 1) resample every PNG in input_dir."""
    sprites = []
    for path in sorted(input_dir.glob("*.png")):
        with Image.open(path) as source:
            cropped = crop_to_alpha(source, alpha_threshold)
        if cropped is None:
            print(f"warning: {path.name} is fully transparent, skipped", file=sys.stderr)
            continue
        sprites.append(Sprite(asset_id=path.stem, image=scale_image(cropped, scale)))
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


def pack(sprites: list[Sprite], max_width: int = 2048, padding: int = 2) -> tuple[int, int]:
    """Assign atlas positions with shelf packing (tallest first). Returns the atlas size.

    Every sprite keeps `padding` transparent pixels on all sides so that filtered sampling
    never bleeds a neighbour into it.
    """
    widest = max((sprite.width for sprite in sprites), default=0)
    if widest + 2 * padding > max_width:
        raise ValueError(f"a sprite is {widest}px wide and does not fit in --max-width {max_width}")

    x = y = padding
    shelf_height = 0
    used_width = 0
    for sprite in sorted(sprites, key=lambda s: (-s.height, s.asset_id)):
        if x + sprite.width + padding > max_width:
            x = padding
            y += shelf_height + padding
            shelf_height = 0
        sprite.atlas_x, sprite.atlas_y = x, y
        x += sprite.width + padding
        shelf_height = max(shelf_height, sprite.height)
        used_width = max(used_width, x)
    if not sprites:
        return 0, 0
    return used_width, y + shelf_height + padding


def build_atlas(sprites: list[Sprite], size: tuple[int, int]) -> Image.Image:
    atlas = Image.new("RGBA", size, (0, 0, 0, 0))
    for sprite in sprites:
        atlas.paste(sprite.image, (sprite.atlas_x, sprite.atlas_y))
    return atlas


def build_manifest(sprites: list[Sprite], size: tuple[int, int], tile_width: int) -> dict:
    return {
        "version": MANIFEST_VERSION,
        "atlas": ATLAS_NAME,
        "atlasWidth": size[0],
        "atlasHeight": size[1],
        "tileWidth": tile_width,
        "assets": [
            {
                "assetId": sprite.asset_id,
                "anchorX": round(sprite.anchor_x, 4),
                "anchorY": round(sprite.anchor_y, 4),
                "width": sprite.width,
                "height": sprite.height,
                "isFlippedAllowed": sprite.is_flipped_allowed,
                "atlasX": sprite.atlas_x,
                "atlasY": sprite.atlas_y,
            }
            for sprite in sorted(sprites, key=lambda s: s.asset_id)
        ],
    }


def run(
    input_dir: Path,
    output_dir: Path,
    *,
    tile_width: int = 256,
    source_tile_width: int | None = None,
    max_width: int = 2048,
    padding: int = 2,
    alpha_threshold: int = 0,
    overrides_path: Path | None = None,
) -> dict:
    """Run the whole pipeline and return the manifest that was written.

    tile_width is how wide one grid tile is in the atlas. When the source art was drawn at a
    different size (source_tile_width), every sprite is resampled by tile_width / source_tile_width.
    """
    if tile_width <= 0 or (source_tile_width is not None and source_tile_width <= 0):
        raise ValueError("tile widths must be positive")
    scale = 1.0 if source_tile_width is None else tile_width / source_tile_width
    sprites = load_sprites(input_dir, alpha_threshold, scale)
    if not sprites:
        raise ValueError(f"no usable .png files in {input_dir}")
    if overrides_path is not None:
        apply_overrides(sprites, json.loads(overrides_path.read_text(encoding="utf-8")))

    size = pack(sprites, max_width, padding)
    manifest = build_manifest(sprites, size, tile_width)

    output_dir.mkdir(parents=True, exist_ok=True)
    build_atlas(sprites, size).save(output_dir / ATLAS_NAME)
    (output_dir / MANIFEST_NAME).write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return manifest


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Crop sprites and pack them into a texture atlas.")
    parser.add_argument("input_dir", type=Path, help="folder of transparent .png files")
    parser.add_argument("output_dir", type=Path, help=f"where {ATLAS_NAME} and {MANIFEST_NAME} are written")
    parser.add_argument("--tile-width", type=int, default=256,
                        help="pixel width of one grid tile in the atlas (default: 256)")
    parser.add_argument("--source-tile-width", type=int, default=None,
                        help="pixel width of one grid tile in the source art, if different from "
                             "--tile-width; sprites are resampled by tile-width / source-tile-width")
    parser.add_argument("--max-width", type=int, default=2048, help="maximum atlas width in pixels (default: 2048)")
    parser.add_argument("--padding", type=int, default=2, help="transparent pixels around each sprite (default: 2)")
    parser.add_argument("--alpha-threshold", type=int, default=0,
                        help="pixels with alpha at or below this (0-255) count as empty when cropping (default: 0)")
    parser.add_argument("--overrides", type=Path, default=None,
                        help="JSON file with per-asset anchorX / anchorY / isFlippedAllowed")
    args = parser.parse_args(argv)

    try:
        manifest = run(
            args.input_dir,
            args.output_dir,
            tile_width=args.tile_width,
            source_tile_width=args.source_tile_width,
            max_width=args.max_width,
            padding=args.padding,
            alpha_threshold=args.alpha_threshold,
            overrides_path=args.overrides,
        )
    except (OSError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1

    print(f"packed {len(manifest['assets'])} sprites into "
          f"{manifest['atlasWidth']}x{manifest['atlasHeight']} -> {args.output_dir / ATLAS_NAME}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
