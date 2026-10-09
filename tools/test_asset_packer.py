"""Tests for asset_packer.py. Run from the repository root:

    python -m unittest discover tools
"""

import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image

import asset_packer


def make_png(path: Path, canvas: tuple[int, int], box: tuple[int, int, int, int], color=(255, 0, 0, 255)):
    """Transparent canvas with one opaque rectangle at box = (left, top, right, bottom)."""
    image = Image.new("RGBA", canvas, (0, 0, 0, 0))
    image.paste(color, box)
    image.save(path)


class AssetPackerTest(unittest.TestCase):

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        self.src = self.root / "src"
        self.out = self.root / "out"
        self.src.mkdir()

    def tearDown(self):
        self._tmp.cleanup()

    def test_crop_trims_transparent_margins(self):
        image = Image.new("RGBA", (100, 80), (0, 0, 0, 0))
        image.paste((0, 255, 0, 255), (10, 20, 40, 70))
        cropped = asset_packer.crop_to_alpha(image)
        self.assertEqual((30, 50), cropped.size)

    def test_crop_ignores_pixels_at_or_below_threshold(self):
        image = Image.new("RGBA", (50, 50), (0, 0, 0, 0))
        image.paste((0, 0, 255, 8), (0, 0, 50, 50))      # faint halo over everything
        image.paste((0, 0, 255, 255), (20, 20, 30, 30))  # the actual sprite
        self.assertEqual((50, 50), asset_packer.crop_to_alpha(image, alpha_threshold=0).size)
        self.assertEqual((10, 10), asset_packer.crop_to_alpha(image, alpha_threshold=8).size)

    def test_fully_transparent_image_is_skipped(self):
        Image.new("RGBA", (10, 10), (0, 0, 0, 0)).save(self.src / "empty.png")
        make_png(self.src / "real.png", (10, 10), (2, 2, 6, 6))
        self.assertEqual(["real"], [s.asset_id for s in asset_packer.load_sprites(self.src)])

    def test_run_writes_atlas_and_manifest(self):
        make_png(self.src / "plant_tulip_stage3.png", (200, 200), (50, 20, 110, 180), (255, 0, 0, 255))
        make_png(self.src / "deco_rock.png", (200, 200), (80, 120, 160, 170), (0, 0, 255, 255))

        asset_packer.run(self.src, self.out, tile_width=128)

        manifest = json.loads((self.out / "assets_manifest.json").read_text(encoding="utf-8"))
        atlas = Image.open(self.out / "atlas.png")
        self.assertEqual((manifest["atlasWidth"], manifest["atlasHeight"]), atlas.size)
        self.assertEqual("atlas.png", manifest["atlas"])
        self.assertEqual(128, manifest["tileWidth"])

        assets = {a["assetId"]: a for a in manifest["assets"]}
        self.assertEqual({"plant_tulip_stage3", "deco_rock"}, set(assets))
        tulip, rock = assets["plant_tulip_stage3"], assets["deco_rock"]
        self.assertEqual((60, 160), (tulip["width"], tulip["height"]))
        self.assertEqual((80, 50), (rock["width"], rock["height"]))
        # Default anchor: bottom center of the cropped sprite.
        self.assertEqual((0.5, 1.0), (tulip["anchorX"], tulip["anchorY"]))
        self.assertTrue(tulip["isFlippedAllowed"])

        # The pixels at each recorded rectangle are that sprite's pixels.
        for asset, color in ((tulip, (255, 0, 0, 255)), (rock, (0, 0, 255, 255))):
            x, y, w, h = asset["atlasX"], asset["atlasY"], asset["width"], asset["height"]
            self.assertEqual(color, atlas.getpixel((x, y)))
            self.assertEqual(color, atlas.getpixel((x + w - 1, y + h - 1)))

    def test_packed_sprites_do_not_overlap_and_keep_padding(self):
        for i in range(12):
            make_png(self.src / f"s{i:02}.png", (64, 64), (0, 0, 20 + i * 3, 30 + i * 2))
        sprites = asset_packer.load_sprites(self.src)
        width, height = asset_packer.pack(sprites, max_width=128, padding=2)

        self.assertLessEqual(width, 128)
        rects = [(s.atlas_x - 2, s.atlas_y - 2, s.atlas_x + s.width + 2, s.atlas_y + s.height + 2) for s in sprites]
        for left, top, right, bottom in rects:
            self.assertGreaterEqual(left, 0)
            self.assertGreaterEqual(top, 0)
            self.assertLessEqual(right, width)
            self.assertLessEqual(bottom, height)
        # Padded rectangles may share the padding strip but the sprites themselves stay 2px apart.
        for i, a in enumerate(sprites):
            for b in sprites[i + 1:]:
                apart_x = a.atlas_x + a.width + 2 <= b.atlas_x or b.atlas_x + b.width + 2 <= a.atlas_x
                apart_y = a.atlas_y + a.height + 2 <= b.atlas_y or b.atlas_y + b.height + 2 <= a.atlas_y
                self.assertTrue(apart_x or apart_y, f"{a.asset_id} and {b.asset_id} are too close")

    def test_overrides_set_manual_anchor_and_flip(self):
        make_png(self.src / "tile_grass.png", (64, 64), (0, 0, 64, 40))
        overrides = self.root / "overrides.json"
        overrides.write_text(json.dumps({"tile_grass": {"anchorY": 0.4, "isFlippedAllowed": False}}))

        manifest = asset_packer.run(self.src, self.out, overrides_path=overrides)

        tile = manifest["assets"][0]
        self.assertEqual((0.5, 0.4, False), (tile["anchorX"], tile["anchorY"], tile["isFlippedAllowed"]))

    def test_bad_overrides_are_rejected(self):
        make_png(self.src / "a.png", (10, 10), (0, 0, 5, 5))
        sprites = asset_packer.load_sprites(self.src)
        with self.assertRaises(ValueError):
            asset_packer.apply_overrides(sprites, {"missing": {"anchorX": 0.5}})
        with self.assertRaises(ValueError):
            asset_packer.apply_overrides(sprites, {"a": {"anchorY": 1.5}})
        with self.assertRaises(ValueError):
            asset_packer.apply_overrides(sprites, {"a": {"anchor_y": 0.5}})

    def test_sprite_wider_than_atlas_is_an_error(self):
        make_png(self.src / "wide.png", (300, 10), (0, 0, 300, 10))
        with self.assertRaises(ValueError):
            asset_packer.pack(asset_packer.load_sprites(self.src), max_width=256)


if __name__ == "__main__":
    unittest.main()
