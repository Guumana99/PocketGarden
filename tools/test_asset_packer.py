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


def make_crop(path: Path, stem_x: int = 50):
    """A plant on a clump of earth: a green stem and leaves on a dark brown ball."""
    image = Image.new("RGBA", (100, 200), (0, 0, 0, 0))
    image.paste((40, 140, 50, 255), (stem_x - 3, 30, stem_x + 3, 150))   # stem
    image.paste((60, 160, 60, 255), (20, 30, 80, 60))                    # leaves
    image.paste((70, 50, 40, 255), (15, 140, 85, 195))                   # the clump of earth
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

    def test_crop_pictures_lose_their_clump_of_earth_and_stand_on_their_stem(self):
        make_crop(self.src / "plant_test_stage1.png", stem_x=60)
        make_png(self.src / "rock.png", (40, 40), (5, 5, 35, 35), color=(70, 50, 40, 255))
        manifest = asset_packer.run(self.src, self.out, strip_crop_soil=True)
        by_id = {asset["assetId"]: asset for asset in manifest["assets"]}
        plant = by_id["plant_test_stage1"]
        # The clump (55 px tall, 70 px wide) is gone: what is left is the stem and leaves.
        self.assertLessEqual(plant["height"], 125)
        self.assertLessEqual(plant["width"], 62)
        # It stands on the base of its stem, at the bottom of what is left.
        self.assertEqual(1.0, plant["anchorY"])
        stem_from_left = (60 - 20) / plant["width"]
        self.assertAlmostEqual(stem_from_left, plant["anchorX"], delta=0.06)
        # Anything that is not a crop keeps its colours, even earth-brown ones.
        self.assertEqual(30, by_id["rock"]["width"])

    def test_without_the_option_crop_pictures_are_left_alone(self):
        make_crop(self.src / "plant_test_stage1.png")
        manifest = asset_packer.run(self.src, self.out)
        self.assertEqual(165, manifest["assets"][0]["height"])

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

    def test_source_tile_width_shrinks_sprites_and_keeps_anchor_fractions(self):
        make_png(self.src / "plant.png", (400, 400), (100, 40, 300, 360))  # 200x320 of content
        overrides = self.root / "overrides.json"
        overrides.write_text(json.dumps({"plant": {"anchorX": 0.4, "anchorY": 0.9}}))

        manifest = asset_packer.run(
            self.src, self.out, tile_width=128, source_tile_width=256, overrides_path=overrides)

        plant = manifest["assets"][0]
        self.assertEqual(128, manifest["tileWidth"])
        self.assertEqual((100, 160), (plant["width"], plant["height"]))
        self.assertEqual((0.4, 0.9), (plant["anchorX"], plant["anchorY"]))
        with Image.open(self.out / "atlas.png") as atlas:
            self.assertEqual((manifest["atlasWidth"], manifest["atlasHeight"]), atlas.size)

    def test_tile_width_alone_does_not_resize(self):
        make_png(self.src / "plant.png", (400, 400), (100, 40, 300, 360))
        manifest = asset_packer.run(self.src, self.out, tile_width=128)
        self.assertEqual((200, 320), (manifest["assets"][0]["width"], manifest["assets"][0]["height"]))

    def test_scaling_does_not_bleed_the_transparent_background_colour(self):
        # Opaque red on fully transparent *white*: a naive resize would turn the edge pink.
        image = Image.new("RGBA", (64, 64), (255, 255, 255, 0))
        image.paste((255, 0, 0, 255), (16, 16, 48, 48))

        scaled = asset_packer.scale_image(image, 0.5)

        self.assertEqual((32, 32), scaled.size)
        for x in range(32):
            for y in range(32):
                r, g, b, a = scaled.getpixel((x, y))
                if a > 16:
                    self.assertLess(g, 40, f"green leaked in at {(x, y)}: {(r, g, b, a)}")
                    self.assertLess(b, 40, f"blue leaked in at {(x, y)}: {(r, g, b, a)}")

    def test_sprite_wider_than_atlas_is_an_error(self):
        make_png(self.src / "wide.png", (300, 10), (0, 0, 300, 10))
        with self.assertRaises(ValueError):
            asset_packer.pack(asset_packer.load_sprites(self.src), max_width=256)


if __name__ == "__main__":
    unittest.main()
