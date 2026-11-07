"""Takes a run of wide pictures of the showcase garden one after another, for a moment that cannot be
timed (where a visitor stands), and a sheet of them all to pick from. See docs/promo/README.md.

    python tools/promo_burst.py --garden birdcrops --view 2.6 5 4.5 0 --count 30 --gap 0.5 \
        --out app/build/promo/bird -- --es creatures bird --es bird 5,4

Each picture is kept at full size (the display's, 2400x1350 unless --display) as bNN.png; sheet.jpg
has them all, numbered. The chosen one is cut (around the bird for X-012) and scaled to 1600x900
with promo_crop.py-style PIL by hand, like the others (docs/promo/README.md).
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import promo_capture as pc  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--garden", help="the showcase garden's variant to load (none: the showcase garden)")
    parser.add_argument("--no-load", action="store_true", help="keep the garden that is open")
    parser.add_argument("--view", nargs=4, type=float, metavar=("SCALE", "X", "Y", "LIFT"), default=[0.3, 5, 5, 0])
    parser.add_argument("--count", type=int, default=24)
    parser.add_argument("--gap", type=float, default=1.0, help="seconds between pictures")
    parser.add_argument("--display", default="2400x1350",
                        help="the display while taking them; a larger one (4800x2700) leaves room to cut a closer picture out")
    parser.add_argument("--density", type=int, default=0,
                        help="the display's dpi while taking them (840 with 4800x2700: the same view, twice as fine; "
                             "the garden zooms no further than GardenCamera.MAX_SCALE)")
    parser.add_argument("--out", default=str(pc.ROOT / "app/build/promo/burst"))
    parser.add_argument("extras", nargs="*", help="more for the receiver, after --")
    args = parser.parse_args()

    device = pc.Device(args.serial)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    if not args.no_load:
        pc.open_showcase(device, install=False, load=("--es", "garden", args.garden) if args.garden else ())
    scale, x, y, lift = args.view
    # The pan is in pixels: as many more as the display is denser.
    fine = args.density / 420 if args.density else 1.0
    view = pc.View(scale, cell=(x, y), lift=lift, pixels=fine)
    width, height = (int(v) for v in args.display.split("x"))
    device.display((width, height))
    if args.density:
        device.shell("wm", "density", str(args.density))
    time.sleep(3)
    # The plain look first, so that the window has turned before anything is called.
    pc.set_look(device, pc.Look(), view, settle=7)
    device.broadcast(*pc.Look().extras(), *args.extras, *view.extras())
    thumbs = []
    for i in range(args.count):
        time.sleep(args.gap)
        path = out / f"b{i:02d}.png"
        device.screencap(path)
        thumbs.append(Image.open(path).convert("RGB").resize((480, round(480 * height / width))))
    cols = 6
    rows = (len(thumbs) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * 484, rows * 290), (30, 30, 30))
    draw = ImageDraw.Draw(sheet)
    for i, thumb in enumerate(thumbs):
        left, top = (i % cols) * 484, (i // cols) * 290
        sheet.paste(thumb, (left, top + 18))
        draw.text((left + 4, top + 2), str(i), fill=(255, 255, 0))
    sheet.save(out / "sheet.jpg", quality=80)
    device.display(None)
    if args.density:
        device.shell("wm", "density", "reset")
    print("sheet:", out / "sheet.jpg")
    return 0


if __name__ == "__main__":
    sys.exit(main())
