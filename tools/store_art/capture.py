"""Takes the game screens the store screenshots are made from, on the emulator (debug build).

The real game, with its buttons, in the showcase garden (app/src/debug/.../ShowcaseGarden.kt)
loaded through the debug receiver; the display set to 1080 x 1920 for the moment. The app's
language is set to Japanese first, so that the kanji take their Japanese shapes.

    python tools/store_art/capture.py                 # every shot in config.yaml's captures
    python tools/store_art/capture.py --only s01_spring_morning --no-install

The pictures go to build/store_art/raw/<name>.png (store_art.py reads them from there).
"""
from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
from promo_capture import PACKAGE, TALL_SIZE, Device, Look, View, open_showcase, set_look  # noqa: E402

ROOT = HERE.parent.parent
RAW = ROOT / "build/store_art/raw"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--config", default=str(HERE / "config.yaml"))
    ap.add_argument("--only", nargs="*")
    ap.add_argument("--no-install", action="store_true")
    ap.add_argument("--serial", default="emulator-5554")
    args = ap.parse_args()
    if not args.serial.startswith("emulator-"):
        sys.exit("only the emulator is used (--serial emulator-NNNN)")
    config = yaml.safe_load(Path(args.config).read_text(encoding="utf-8"))
    shots = [c for c in config.get("captures", []) if not args.only or c["name"] in args.only]
    RAW.mkdir(parents=True, exist_ok=True)
    device = Device(args.serial)
    device.shell("cmd", "locale", "set-app-locales", PACKAGE, "--locales", "ja-JP")
    loaded = None
    for shot in shots:
        garden = shot.get("garden", "spring")
        if garden != loaded:
            open_showcase(device, install=not args.no_install and loaded is None, load=("--es", "garden", garden))
            loaded = garden
        device.display(TALL_SIZE)
        time.sleep(3)
        look = Look(
            season=shot.get("season", "SPRING"), time=shot.get("time", "MORNING"), weather=shot.get("weather", "SUNNY"),
            creatures=shot.get("creatures", "none"), hide_ui=False, more=tuple(shot.get("extras", [])),
        )
        view = View(shot.get("scale", 0.34), cell=tuple(shot.get("cell", (5, 5))), lift=shot.get("lift", 0.0))
        set_look(device, look, view, settle=shot.get("settle", 5.0))
        target = RAW / f"{shot['name']}.png"
        device.screencap(target)
        print("took", target)
    device.display(None)


if __name__ == "__main__":
    main()
