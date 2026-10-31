"""Takes the before/after pictures for the device feedback fixes on the emulator (debug builds only).

It loads the feedback garden (app/src/debug/.../FeedbackGarden.kt) through the showcase receiver,
fixes the look, and takes each shot of STILLS cropped to the middle of the screen, where the view
is centred on the cell being looked at. The same names are used for before and after, so the two
sets can be laid side by side (--sheet).

    python tools/fb_capture.py --out <dir>                 # install the debug APK, then every shot
    python tools/fb_capture.py --out <dir> --no-install --only lantern_night
    python tools/fb_capture.py --sheet <before dir> <after dir> <sheet.jpg>
"""

from __future__ import annotations

import argparse
import sys
import time
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
from promo_capture import APK, PACKAGE, Device, Look, View  # noqa: E402


@dataclass
class Shot:
    name: str
    caption: str
    look: Look
    view: View
    # The part of the 1080 x 2424 screen kept: half its width and height round the middle, by default.
    crop: tuple[int, int] = (720, 900)


NIGHT = Look(time="NIGHT")
DAY = Look()

STILLS = [
    Shot("whole_day", "全体（昼）", DAY, View(0.27), (1080, 1100)),
    Shot("whole_night", "全体（夜）", NIGHT, View(0.27), (1080, 1100)),
    Shot("lantern_night", "1 石灯籠の明かり（夜）", NIGHT, View(1.3, cell=(1.5, 5.5), lift=150)),
    Shot("hut_night", "2 小屋の窓（夜）", NIGHT, View(1.1, cell=(4.5, 0.5), lift=250)),
    Shot("cottage_night", "2 コテージの窓（夜）", NIGHT, View(0.9, cell=(1.0, 1.0), lift=300)),
    Shot("soil_day", "3 耕した土の境目", DAY, View(1.8, cell=(5.5, 7.5))),
    Shot("edge_right", "4 お庭の枠（右の縁）", DAY, View(1.3, cell=(10.0, 6.0), lift=60)),
    Shot("edge_bottom", "4 お庭の枠（手前の角）", DAY, View(1.3, cell=(9.0, 10.0), lift=60)),
    Shot("fence_day", "5 建築中の柵の隣", DAY, View(1.8, cell=(5.5, 4.0), lift=60)),
    Shot("status_day", "6 状態アイコン", DAY, View(1.6, cell=(10.0, 5.5), lift=120)),
    Shot("crops_day", "10 ニンジン", DAY, View(2.2, cell=(6.5, 10.0), lift=120)),
    Shot("shiitake_day", "9 しいたけの原木", DAY, View(2.2, cell=(5.0, 10.0), lift=60), (720, 600)),
    Shot("cottage_plinth", "17 コテージの土台と石畳", DAY, View(0.9, cell=(1.5, 2.5), lift=60), (900, 700)),
    Shot("daikon_day", "18 大根（収穫どき）", DAY, View(2.6, cell=(5.5, 8.5), lift=60), (720, 600)),
    Shot("rock_bench_day", "15・19 岩とベンチ", DAY, View(1.8, cell=(7.0, 5.5), lift=60), (720, 600)),
]

#: Screens reached through the buttons (garden UI shown): name, caption, what to tap in turn
#: (texts on screen; the first that is found of each "a|b" is tapped), the part kept (l, t, r, b).
UI_SHOTS = [
    ("book_buildings", "7 図鑑のたてもの", ["@ガーデン図鑑", "たてもの"], (60, 880, 1020, 1780)),
    ("shop_seeds", "8 種と苗の店", ["ショップ"], (0, 380, 1080, 2000)),
    ("shop_workshop", "8 大工の工房", ["ショップ", "大工の工房"], (0, 380, 1080, 2000)),
    ("shop_sundries", "8 雑貨店", ["ショップ", "お庭の雑貨店|庭の雑貨店"], (0, 380, 1080, 2000)),
]


def open_feedback_garden(device: Device, install: bool) -> None:
    device.display(None)
    if install:
        print("installing", APK.name)
        device.adb("install", "-r", str(APK))
    device.shell("settings", "put", "secure", "immersive_mode_confirmations", "confirmed")
    device.shell("am", "force-stop", PACKAGE)
    device.broadcast("--ez", "load", "true", "--es", "garden", "fb")
    time.sleep(4)
    device.shell("am", "start", "-n", f"{PACKAGE}/.MainActivity")
    seen = False
    for _ in range(40):
        time.sleep(3)
        spot = device.find_text("つづきから")
        if spot is None:
            if seen:
                break
            continue
        seen = True
        device.shell("input", "tap", str(spot[0]), str(spot[1]))
    else:
        raise RuntimeError("could not get past the title screen")
    time.sleep(6)


def find_any(device: Device, wanted: str) -> tuple[int, int] | None:
    """The middle of the first of [wanted] ("a|b") on screen; "@x" looks for a content description."""
    import re
    device.adb("shell", "rm", "-f", "/sdcard/fb_ui.xml", check=False)
    device.adb("shell", "uiautomator", "dump", "/sdcard/fb_ui.xml", check=False)
    xml = device.adb("exec-out", "cat", "/sdcard/fb_ui.xml", capture=True, check=False).decode("utf-8", errors="replace")
    for text in wanted.split("|"):
        attr = "content-desc" if text.startswith("@") else "text"
        match = re.search(rf'{attr}="{re.escape(text.lstrip("@"))}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def take_ui(device: Device, name: str, taps: list[str], box: tuple[int, int, int, int], out: Path) -> None:
    device.broadcast("--ez", "clear", "true")
    time.sleep(2)
    # Back on the title screen (an earlier Back may have left it there): into the garden again.
    spot = find_any(device, "つづきから")
    if spot is not None:
        device.shell("input", "tap", str(spot[0]), str(spot[1]))
        time.sleep(6)
    for wanted in taps:
        spot = find_any(device, wanted)
        if spot is None:
            print("not found:", wanted.encode("unicode_escape").decode())
            return
        device.shell("input", "tap", str(spot[0]), str(spot[1]))
        time.sleep(2.5)
    raw = out / f"{name}_full.png"
    device.screencap(raw)
    Image.open(raw).crop(box).save(out / f"{name}.png")
    raw.unlink()
    # Out again by the screen's own button: the book's "close", the shop's "back to the garden".
    spot = find_any(device, "閉じる|← お庭へ戻る|← 庭へ戻る")
    if spot is not None:
        device.shell("input", "tap", str(spot[0]), str(spot[1]))
        time.sleep(2)
    print("took", name)


def take(device: Device, shot: Shot, out: Path) -> Path:
    device.broadcast(*shot.look.extras(), *shot.view.extras())
    time.sleep(4)
    raw = out / f"{shot.name}_full.png"
    device.screencap(raw)
    image = Image.open(raw)
    w, h = shot.crop
    left, top = (image.width - w) // 2, (image.height - h) // 2
    target = out / f"{shot.name}.png"
    image.crop((left, top, left + w, top + h)).save(target)
    raw.unlink()
    print("took", target.name)
    return target


def sheet(before: Path, after: Path, target: Path) -> None:
    """Before on the left, after on the right, one row per shot, each scaled to [cell] wide."""
    cell = 420
    font = None
    for name in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        try:
            font = ImageFont.truetype(name, 26)
            break
        except OSError:
            continue
    rows = []
    for shot in [(s.name, s.caption) for s in STILLS] + [(n, c) for n, c, _, _ in UI_SHOTS]:
        shot = Shot(shot[0], shot[1], DAY, View(1.0))
        pair = [before / f"{shot.name}.png", after / f"{shot.name}.png"]
        if not all(p.exists() for p in pair):
            continue
        images = [Image.open(p).convert("RGB") for p in pair]
        images = [im.resize((cell, round(im.height * cell / im.width))) for im in images]
        height = max(im.height for im in images) + 40
        row = Image.new("RGB", (cell * 2 + 30, height), "white")
        ImageDraw.Draw(row).text((10, 5), f"{shot.caption}　（左：前　右：後）", fill="black", font=font)
        for i, im in enumerate(images):
            row.paste(im, (10 + i * (cell + 10), 40))
        rows.append(row)
    total = Image.new("RGB", (cell * 2 + 30, sum(r.height for r in rows)), "white")
    y = 0
    for row in rows:
        total.paste(row, (0, y))
        y += row.height
    total.save(target, quality=88)
    print("sheet", target)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path)
    parser.add_argument("--no-install", action="store_true")
    parser.add_argument("--only", nargs="*")
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--apk", type=Path, help="install this APK instead of the project's debug build")
    parser.add_argument("--sheet", nargs=3, type=Path, metavar=("BEFORE", "AFTER", "TARGET"))
    args = parser.parse_args()
    if args.sheet:
        sheet(*args.sheet)
        return 0
    args.out.mkdir(parents=True, exist_ok=True)
    device = Device(args.serial)
    if args.apk:
        device.adb("install", "-r", str(args.apk))
    open_feedback_garden(device, install=not args.no_install and not args.apk)
    try:
        for shot in STILLS:
            if args.only and shot.name not in args.only:
                continue
            take(device, shot, args.out)
        for name, _, taps, box in UI_SHOTS:
            if args.only and name not in args.only:
                continue
            take_ui(device, name, taps, box, args.out)
    finally:
        device.broadcast("--ez", "clear", "true")
    return 0


if __name__ == "__main__":
    sys.exit(main())
