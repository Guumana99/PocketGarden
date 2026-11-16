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
import subprocess
import sys
import time
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
from promo_capture import GARDEN_SIZE, ffmpeg_exe, PACKAGE, TALL_SIZE, TILE_H, TILE_W, Device, Look, View, open_showcase, pan_to, set_look  # noqa: E402

ROOT = HERE.parent.parent
RAW = ROOT / "build/store_art/raw"


def cell_point(view: View, cell) -> tuple[int, int]:
    """Where on the 1080 x 1920 display the middle of [cell] is, with [view]."""
    px, py = pan_to(view.cell, view.scale, view.lift)
    middle = (GARDEN_SIZE - 1) / 2
    x, y = cell[0] - middle, cell[1] - middle
    sx = (x - y) * TILE_W / 2 * view.scale + px
    sy = (x + y) * TILE_H / 2 * view.scale + py
    return round(TALL_SIZE[0] / 2 + sx), round(TALL_SIZE[1] / 2 + sy)


def record_and_pick(device: Device, shot: dict, action) -> None:
    """Records the screen while [action] is done, and keeps the frame `record.pick` seconds in.

    A screencap takes seconds on the emulator and lags behind the screen, so a moment that does not
    last (the coins of a harvest, a butterfly landing) is recorded instead. All the frames, a tenth
    of a second apart, are kept beside it (<name>_frames/) to choose another `pick` from.
    """
    device.shell("rm", "-f", "/sdcard/store_clip.mp4")
    recorder = subprocess.Popen(["adb", "-s", device.serial, "shell", "screenrecord", "--bit-rate", "20000000",
                                 "--time-limit", str(shot["record"]["seconds"]), "/sdcard/store_clip.mp4"])
    time.sleep(1.5)
    action()
    recorder.wait()
    time.sleep(1.0)
    clip = RAW / f"{shot['name']}.mp4"
    device.adb("pull", "/sdcard/store_clip.mp4", str(clip))
    frames = RAW / f"{shot['name']}_frames"
    frames.mkdir(exist_ok=True)
    for old in frames.glob("*.png"):
        old.unlink()
    subprocess.run([ffmpeg_exe(), "-y", "-loglevel", "error", "-i", str(clip), "-vf", "fps=10", str(frames / "f_%03d.png")], check=True)
    pick = frames / f"f_{round(shot['record']['pick'] * 10) + 1:03d}.png"
    (RAW / f"{shot['name']}.png").write_bytes(pick.read_bytes())
    print("took", pick, "from the recording")


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
        more = list(shot.get("extras", []))
        # A butterfly to this flower and a bird to this building, called as the look is set.
        if "butterfly" in shot:
            bx, by = shot["butterfly"]
            more += ["--ez", "butterfly", "true", "--ei", "butterfly_x", str(bx), "--ei", "butterfly_y", str(by)]
        if "bird" in shot:
            more += ["--es", "bird", shot["bird"]]
        look = Look(
            season=shot.get("season", "SPRING"), time=shot.get("time", "MORNING"), weather=shot.get("weather", "SUNNY"),
            creatures=shot.get("creatures", "none"), hide_ui=False, more=tuple(more),
        )
        view = View(shot.get("scale", 0.34), cell=tuple(shot.get("cell", (5, 5))), lift=shot.get("lift", 0.0))
        # Set twice: right after the display has changed size, the first view can land on the old size.
        set_look(device, look, view, settle=2.0)
        if "record" in shot and "tap" not in shot:
            # Visitors on their way: recorded from the moment they are called, one frame kept.
            record_and_pick(device, shot, lambda: set_look(device, look, view, settle=0))
            continue
        set_look(device, look, view, settle=shot.get("settle", 5.0))
        # A tap on a cell (a harvest: its coins float up for a moment), then the picture straight after.
        if "tap" in shot:
            x, y = cell_point(view, shot["tap"])
            # Then buttons in what opens (収穫, then 閉じる so that the coins floating up from the
            # middle are not behind the panel). Reading the screen (uiautomator) holds the app up
            # for a while, so the buttons are found on a first opening, which is shut again, and
            # pressed on a second one without reading the screen in between.
            spots = []
            if shot.get("press"):
                device.shell("input", "tap", str(x), str(y))
                time.sleep(3.0)
                for label in shot["press"]:
                    spot = None
                    for _ in range(3):
                        spot = device.find_text(label)
                        if spot is not None:
                            break
                        time.sleep(1.5)
                    if spot is None:
                        raise SystemExit(f"{label} is not on screen")
                    spots.append(spot)
                close = device.find_text("閉じる")
                if close is not None:
                    device.shell("input", "tap", str(close[0]), str(close[1]))
                time.sleep(4.0)
            device.shell("input", "tap", str(x), str(y))
            time.sleep(3.0)
            # A screencap takes seconds on the emulator and the coins float for 2.6 s only: the
            # moment is recorded instead (`record: { seconds, pick }`) and one frame taken out of it.
            def press() -> None:
                for spot in spots:
                    device.shell("input", "tap", str(spot[0]), str(spot[1]))
                    time.sleep(0.25)

            if "record" in shot:
                record_and_pick(device, shot, press)
                continue
            press()
            time.sleep(shot.get("after_tap", 0.6))
        # A button down the edge opened (by its label, e.g. ガーデン図鑑), and a tab in it.
        if "open" in shot:
            spot = device.find_text(shot["open"], attribute="content-desc")
            if spot is None:
                raise SystemExit(f"{shot['open']} is not on screen")
            device.shell("input", "tap", str(spot[0]), str(spot[1]))
            # The book opens slowly; until it has, the screen is only dimmed.
            time.sleep(5.0)
            for label in shot.get("then", []):
                spot = device.find_text(label)
                if spot is None:
                    raise SystemExit(f"{label} is not on screen")
                device.shell("input", "tap", str(spot[0]), str(spot[1]))
                time.sleep(3.0)
        target = RAW / f"{shot['name']}.png"
        device.screencap(target)
        print("took", target)
        # Whatever the tap opened is shut again for the next shot.
        if "tap" in shot:
            spot = device.find_text("閉じる")
            if spot is not None:
                device.shell("input", "tap", str(spot[0]), str(spot[1]))
                time.sleep(1.0)
        if "open" in shot:
            # Back to the garden for the next shot.
            device.shell("input", "keyevent", "KEYCODE_BACK")
            time.sleep(1.5)
    device.display(None)


if __name__ == "__main__":
    main()
