"""The two sheets for looking over a set of promotional pictures at a glance (docs/promo/2026-11):
contact_sheet.jpg has every picture small with its name, clips_sheet.jpg the first, middle and
last frame of every clip. The plain pictures in raw/ are left out.

    python tools/promo_sheets.py [docs/promo/2026-11]
"""

from __future__ import annotations

import re
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from promo_capture import ROOT, ffmpeg_exe  # noqa: E402


def contact_sheet(folder: Path) -> Path:
    stills = sorted(folder.glob("X-*.png"))
    w, h, cols = 480, 270, 4
    rows = (len(stills) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * (w + 10) + 10, rows * (h + 34) + 10), (36, 36, 36))
    draw = ImageDraw.Draw(sheet)
    for i, path in enumerate(stills):
        x, y = 10 + (i % cols) * (w + 10), 10 + (i // cols) * (h + 34)
        sheet.paste(Image.open(path).convert("RGB").resize((w, h), Image.LANCZOS), (x, y + 22))
        draw.text((x, y + 4), path.name, fill=(240, 240, 240))
    target = folder / "contact_sheet.jpg"
    sheet.save(target, quality=85)
    return target


def clips_sheet(folder: Path) -> Path:
    ffmpeg = ffmpeg_exe()
    row_height = 300
    scratch = Path(tempfile.mkdtemp())
    rows = []
    for clip in sorted(folder.glob("X-*.mp4")):
        info = subprocess.run([ffmpeg, "-i", str(clip)], capture_output=True, text=True, errors="replace").stderr
        m = re.search(r"Duration: (\d+):(\d+):([\d.]+)", info)
        length = int(m[1]) * 3600 + int(m[2]) * 60 + float(m[3])
        frames = []
        for j, at in enumerate([0.05, length / 2, length - 0.1]):
            still = scratch / f"{clip.stem}_{j}.png"
            subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-ss", str(at), "-i", str(clip), "-frames:v", "1", str(still)], check=True)
            image = Image.open(still).convert("RGB")
            frames.append(image.resize((round(image.width * row_height / image.height), row_height), Image.LANCZOS))
        rows.append((clip.name, length, frames))
    width = max(sum(f.width for f in frames) + 40 for _, _, frames in rows)
    sheet = Image.new("RGB", (width, len(rows) * (row_height + 34) + 10), (36, 36, 36))
    draw = ImageDraw.Draw(sheet)
    for i, (name, length, frames) in enumerate(rows):
        y = 10 + i * (row_height + 34)
        draw.text((10, y + 4), f"{name}  ({length:.1f} s: first / middle / last)", fill=(240, 240, 240))
        x = 10
        for frame in frames:
            sheet.paste(frame, (x, y + 22))
            x += frame.width + 10
    target = folder / "clips_sheet.jpg"
    sheet.save(target, quality=85)
    return target


def main() -> int:
    folder = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/promo/2026-11"
    print(contact_sheet(folder))
    print(clips_sheet(folder))
    return 0


if __name__ == "__main__":
    sys.exit(main())
