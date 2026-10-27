"""Takes the promotional pictures and clips of the garden (docs/promo) on the emulator.

Debug builds only: it loads the showcase garden (app/src/debug/.../ShowcaseGarden.kt) through the
debug receiver, fixes the season, the time of day and the weather, hides everything but the
garden, and takes each shot upright (the device as it is) and 16:9 (the display switched to
2400x1350 for the moment, then scaled to 1600x900). The clips are made with screenrecord and
cut and re-encoded to H.264 with ffmpeg. See docs/promo/README.md.

    python tools/promo_capture.py                 # everything
    python tools/promo_capture.py --only rain.mp4 garden_night.png
    python tools/promo_capture.py --no-install    # the APK on the emulator is already the latest

ffmpeg comes from PATH, or from the imageio-ffmpeg package (pip install imageio-ffmpeg).
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
PACKAGE = "com.studioguu.miniaturegarden"
RECEIVER = f"{PACKAGE}/.debug.ShowcaseReceiver"

# The emulator's own display, and the 16:9 one the wide shots are taken on.
UPRIGHT_SIZE = (1080, 2424)
WIDE_SIZE = (2400, 1350)
WIDE_OUT = (1600, 900)

# A tile at zoom 1 is 128 dp across, 64 dp high: 336 x 168 px at 420 dpi.
TILE_W, TILE_H = 336, 168
GARDEN_SIZE = 11


def pan_to(cell: tuple[float, float], scale: float, lift: float = 0.0) -> tuple[float, float]:
    """The pan that puts the middle of [cell] in the middle of the screen at [scale], looking [lift] px higher."""
    middle = (GARDEN_SIZE - 1) / 2
    x, y = cell[0] - middle, cell[1] - middle
    return -(x - y) * TILE_W / 2 * scale, -(x + y) * TILE_H / 2 * scale + lift


@dataclass
class View:
    scale: float
    cell: tuple[float, float] = ((GARDEN_SIZE - 1) / 2, (GARDEN_SIZE - 1) / 2)
    lift: float = 0.0

    def extras(self) -> list[str]:
        px, py = pan_to(self.cell, self.scale, self.lift)
        return ["--ef", "scale", str(self.scale), "--ef", "pan_x", f"{px:.1f}", "--ef", "pan_y", f"{py:.1f}"]


@dataclass
class Look:
    """What the garden looks like for one shot."""
    time: str = "DAY"
    weather: str = "SUNNY"
    season: str = "AUTUMN"
    creatures: str = "none"
    butterfly: bool = False

    def extras(self) -> list[str]:
        out = ["--es", "season", self.season, "--es", "time", self.time, "--es", "weather", self.weather,
               "--ez", "hide_ui", "true", "--es", "creatures", self.creatures]
        if self.butterfly:
            out += ["--ez", "butterfly", "true"]
        return out


@dataclass
class Still:
    name: str
    look: Look
    upright: View
    wide: View


@dataclass
class Clip:
    name: str
    look: Look
    view: View
    record_seconds: int
    start: float
    length: float
    # Set the look only once recording has begun (for the butterfly, which sets off then).
    set_after_start: bool = False
    before: Look | None = field(default=None)


WHOLE_UPRIGHT = View(0.27)
WHOLE_WIDE = View(0.30)

STILLS = [
    Still("garden_day_autumn.png", Look(), WHOLE_UPRIGHT, WHOLE_WIDE),
    Still("garden_evening_autumn.png", Look(time="EVENING"), WHOLE_UPRIGHT, WHOLE_WIDE),
    Still("garden_night.png", Look(time="NIGHT"), WHOLE_UPRIGHT, WHOLE_WIDE),
    Still("garden_rain.png", Look(weather="RAINY"), WHOLE_UPRIGHT, WHOLE_WIDE),
    # Sunflowers, tomatoes and cosmos, close.
    Still("garden_close_crops.png", Look(), View(1.2, cell=(6.5, 8.0), lift=380), View(1.5, cell=(6.3, 8.1), lift=330)),
]

CLIPS = [
    # The butterfly sets off from outside the garden when it is summoned, stays about 20 s in all
    # (Showcase.BUTTERFLY_STAY_MILLIS) and leaves again.
    Clip("butterfly.mp4", Look(butterfly=True), View(1.6, cell=(8, 8), lift=120), record_seconds=24,
         start=3.0, length=15.0, set_after_start=True, before=Look()),
    Clip("rain.mp4", Look(weather="RAINY"), WHOLE_WIDE, record_seconds=10, start=1.5, length=7.0),
]


class Device:
    def __init__(self, serial: str):
        self.serial = serial

    def adb(self, *args: str, capture: bool = False, check: bool = True) -> bytes:
        cmd = ["adb", "-s", self.serial, *args]
        result = subprocess.run(cmd, capture_output=True, check=False)
        if check and result.returncode != 0:
            raise RuntimeError(f"{' '.join(cmd)} failed: {result.stderr.decode(errors='replace')}")
        return result.stdout if capture else b""

    def shell(self, *args: str) -> str:
        return self.adb("shell", *args, capture=True).decode(errors="replace")

    def broadcast(self, *extras: str) -> None:
        # Explicit and with FLAG_INCLUDE_STOPPED_PACKAGES, so that it also reaches a stopped app.
        self.shell("am", "broadcast", "-n", RECEIVER, "-f", "0x20", *extras)

    def screencap(self, path: Path) -> None:
        path.write_bytes(self.adb("exec-out", "screencap", "-p", capture=True))

    def display(self, size: tuple[int, int] | None) -> None:
        self.shell("wm", "size", "reset" if size is None else f"{size[0]}x{size[1]}")

    def find_text(self, text: str) -> tuple[int, int] | None:
        """The middle of what shows [text] on screen, or None (also when the screen cannot be read)."""
        self.adb("shell", "rm", "-f", "/sdcard/promo_ui.xml", check=False)
        self.adb("shell", "uiautomator", "dump", "/sdcard/promo_ui.xml", check=False)
        xml = self.adb("exec-out", "cat", "/sdcard/promo_ui.xml", capture=True, check=False).decode(errors="replace")
        match = re.search(rf'text="{re.escape(text)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
        if not match:
            return None
        x1, y1, x2, y2 = map(int, match.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2


def ffmpeg_exe() -> str:
    try:
        import imageio_ffmpeg  # type: ignore

        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        return "ffmpeg"


def open_showcase(device: Device, install: bool) -> None:
    device.display(None)
    if install:
        print("installing", APK.name)
        device.adb("install", "-r", str(APK))
    # The pictures have nothing but the garden in them; the system's note on full screen would cover it.
    device.shell("settings", "put", "secure", "immersive_mode_confirmations", "confirmed")
    device.shell("am", "force-stop", PACKAGE)
    device.broadcast("--ez", "load", "true")
    time.sleep(4)
    device.shell("am", "start", "-n", f"{PACKAGE}/.MainActivity")
    # Right after starting, the first taps are often lost: tap つづきから until the title has gone.
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


def set_look(device: Device, look: Look, view: View, settle: float = 4.0) -> None:
    device.broadcast(*look.extras(), *view.extras())
    time.sleep(settle)


def take_still(device: Device, still: Still, out: Path, scratch: Path) -> list[Path]:
    upright = out / still.name
    wide = out / still.name.replace(".png", "_16x9.png")
    device.display(None)
    time.sleep(2)
    set_look(device, still.look, still.upright)
    device.screencap(upright)
    device.display(WIDE_SIZE)
    time.sleep(3)
    set_look(device, still.look, still.wide)
    raw = scratch / "wide_raw.png"
    device.screencap(raw)
    Image.open(raw).convert("RGB").resize(WIDE_OUT, Image.LANCZOS).save(wide, optimize=True)
    Image.open(upright).convert("RGB").save(upright, optimize=True)
    print("took", upright.name, wide.name)
    return [upright, wide]


def take_clip(device: Device, clip: Clip, out: Path, scratch: Path) -> Path:
    device.display(WIDE_SIZE)
    time.sleep(3)
    set_look(device, clip.before if clip.set_after_start and clip.before else clip.look, clip.view)
    remote = "/sdcard/promo_clip.mp4"
    device.shell("rm", "-f", remote)
    recorder = subprocess.Popen(
        ["adb", "-s", device.serial, "shell", "screenrecord", "--size", "1600x900", "--bit-rate", "12000000",
         "--time-limit", str(clip.record_seconds), remote],
    )
    time.sleep(1.0)
    if clip.set_after_start:
        set_look(device, clip.look, clip.view, settle=0)
    recorder.wait()
    time.sleep(1)
    raw = scratch / f"raw_{clip.name}"
    device.adb("pull", remote, str(raw))
    target = out / clip.name
    subprocess.run(
        [ffmpeg_exe(), "-y", "-loglevel", "error", "-ss", str(clip.start), "-i", str(raw), "-t", str(clip.length),
         "-c:v", "libx264", "-profile:v", "high", "-pix_fmt", "yuv420p", "-r", "30", "-crf", "18",
         "-movflags", "+faststart", "-an", str(target)],
        check=True,
    )
    print("took", target.name)
    return target


def contact_sheet(stills: list[Path], target: Path) -> None:
    """All the stills, small, side by side: the upright ones above, the wide ones below."""
    upright = [p for p in stills if not p.stem.endswith("_16x9")]
    wide = [p for p in stills if p.stem.endswith("_16x9")]
    h_up, w_wide = 600, 520
    thumbs_up = [Image.open(p).convert("RGB") for p in upright]
    thumbs_up = [im.resize((round(im.width * h_up / im.height), h_up), Image.LANCZOS) for im in thumbs_up]
    thumbs_wide = [Image.open(p).convert("RGB").resize((w_wide, round(w_wide * 9 / 16)), Image.LANCZOS) for p in wide]
    gap = 12
    width = max(sum(t.width for t in thumbs_up), sum(t.width for t in thumbs_wide)) + gap * (max(len(thumbs_up), len(thumbs_wide)) + 1)
    height = h_up + (thumbs_wide[0].height if thumbs_wide else 0) + gap * 3
    sheet = Image.new("RGB", (width, height), (40, 40, 40))
    x = gap
    for t in thumbs_up:
        sheet.paste(t, (x, gap))
        x += t.width + gap
    x = gap
    for t in thumbs_wide:
        sheet.paste(t, (x, h_up + gap * 2))
        x += t.width + gap
    sheet.save(target, quality=85)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--out", default=str(ROOT / "docs/promo"))
    parser.add_argument("--scratch", default=str(ROOT / "app/build/promo"), help="where raw captures are kept")
    parser.add_argument("--no-install", action="store_true")
    parser.add_argument("--only", nargs="*", help="file names of the shots to take")
    parser.add_argument("--sheet", help="also write a contact sheet of the stills here")
    args = parser.parse_args()

    out, scratch = Path(args.out), Path(args.scratch)
    out.mkdir(parents=True, exist_ok=True)
    scratch.mkdir(parents=True, exist_ok=True)
    device = Device(args.serial)
    wanted = set(args.only) if args.only else None

    open_showcase(device, install=not args.no_install)
    try:
        for still in STILLS:
            if wanted is None or still.name in wanted:
                take_still(device, still, out, scratch)
        for clip in CLIPS:
            if wanted is None or clip.name in wanted:
                take_clip(device, clip, out, scratch)
    finally:
        device.display(None)
        device.broadcast("--ez", "clear", "true")
    if args.sheet:
        stills = sorted(p for p in out.glob("garden_*.png"))
        contact_sheet(stills, Path(args.sheet))
        print("contact sheet:", args.sheet)
    return 0


if __name__ == "__main__":
    sys.exit(main())
