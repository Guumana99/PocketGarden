"""Edits a recorded promo script into a TikTok video (promo/README.md).

    python tools/promo_render.py T-001

In:  build/promo/<id>/frame_*.png and cues.json (tools/promo_shoot.py),
     promo/scenarios/<id>.yaml (name, hook_from_end), promo/captions/<id>.yaml, promo/outro.yaml.
Out: docs/promo/tiktok/<id>_<name>.mp4 (1080 x 1920, 30 fps, H.264 + AAC) and
     docs/promo/tiktok/<id>_sheet.jpg (one frame a second, with the time under each).

1. The last [hook_from_end] seconds go first too, if asked (0, none, unless the script says).
2. The frames become the video.
3. Captions are burnt in (Noto Sans JP Bold, white with a dark edge), at most 15 characters a line
   and 2 lines, kept out of TikTok's top edge, its bottom fifth and its right-hand buttons.
4. The sound effects go in where cues.json says (no music: that is added in TikTok).
5. Two seconds of ending: the name large over the last scene blurred, the lines of promo/outro.yaml.
"""
import argparse
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile

import yaml
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
FONT = ROOT / "tools/fonts/NotoSansJP.ttf"
RAW = ROOT / "app/src/main/res/raw"
W, H = 1080, 1920

# TikTok's own buttons and text cover these: captions keep clear of them.
SAFE_TOP = int(H * 0.12)
SAFE_BOTTOM = int(H * 0.80)
SAFE_LEFT = 70
SAFE_RIGHT = W - 190
MAX_CHARS = 15
MAX_LINES = 2

# The game's own gains (data/audio/AudioManager.kt, SoundEffect).
GAIN = {"se_tap": 0.80, "se_harvest": 0.80, "se_level_up": 0.90}

OUTRO_FADE = 0.4


def ffmpeg_exe():
    found = shutil.which("ffmpeg")
    if found:
        return found
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        sys.exit("ffmpeg not found: install it (winget install Gyan.FFmpeg) or pip install imageio-ffmpeg")


def font(size, weight="Bold"):
    f = ImageFont.truetype(str(FONT), size)
    try:
        f.set_variation_by_name(weight)
    except (OSError, ValueError):
        pass
    return f


def caption_png(text, path):
    """One caption on a clear 1080 x 1920 picture, centred in the safe area's lower part."""
    lines = text.split("\n")
    for line in lines:
        if len(line) > MAX_CHARS:
            print(f"  warning: caption line over {MAX_CHARS} characters: {line}")
    if len(lines) > MAX_LINES:
        print(f"  warning: caption over {MAX_LINES} lines: {text!r}")
    image = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    # 74 px, smaller if a line would not fit between the safe edges.
    size = 74
    while True:
        f = font(size)
        stroke = max(5, size // 8)
        widest = max(draw.textbbox((0, 0), line, font=f, stroke_width=stroke)[2] - draw.textbbox((0, 0), line, font=f, stroke_width=stroke)[0] for line in lines)
        if widest <= SAFE_RIGHT - SAFE_LEFT or size <= 40:
            break
        size -= 2
    gap = int(size * 0.28)
    boxes = [draw.textbbox((0, 0), line, font=f, stroke_width=stroke) for line in lines]
    heights = [b[3] - b[1] for b in boxes]
    total = sum(heights) + gap * (len(lines) - 1)
    # The block's bottom sits a little above the bottom fifth.
    y = SAFE_BOTTOM - 40 - total
    centre = (SAFE_LEFT + SAFE_RIGHT) // 2
    for line, box, h in zip(lines, boxes, heights):
        width = box[2] - box[0]
        x = max(SAFE_LEFT, min(centre - width // 2, SAFE_RIGHT - width))
        draw.text((x - box[0], y - box[1]), line, font=f, fill=(255, 255, 255, 255), stroke_width=stroke, stroke_fill=(52, 36, 20, 255))
        y += h + gap
    image.save(path)


def outro_png(last_frame, outro, path):
    """The ending: the last scene blurred and dimmed, the name large, the lines under it."""
    base = Image.open(last_frame).convert("RGB").filter(ImageFilter.GaussianBlur(outro.get("blur", 18)))
    base = ImageEnhance.Brightness(base).enhance(0.78)
    draw = ImageDraw.Draw(base)
    centre = (SAFE_LEFT + SAFE_RIGHT) // 2
    # The name as large as fits between the safe edges, up to 210 px.
    size = 210
    while True:
        title = font(size)
        stroke = max(6, size // 17)
        box = draw.textbbox((0, 0), outro["title"], font=title, stroke_width=stroke)
        if box[2] - box[0] <= SAFE_RIGHT - SAFE_LEFT - 20 or size <= 60:
            break
        size -= 4
    y = int(H * 0.36)
    draw.text((centre - (box[2] - box[0]) // 2 - box[0], y - box[1]), outro["title"], font=title, fill="white", stroke_width=stroke, stroke_fill=(52, 36, 20))
    y += box[3] - box[1] + 70
    small = font(62)
    for line in outro.get("lines", []):
        b = draw.textbbox((0, 0), line, font=small, stroke_width=7)
        draw.text((centre - (b[2] - b[0]) // 2 - b[0], y - b[1]), line, font=small, fill="white", stroke_width=7, stroke_fill=(52, 36, 20))
        y += b[3] - b[1] + 34
    base.save(path)


def make_sound(sound_cues, hook, total, path):
    """The sound effects of [sound_cues], [hook] seconds later than recorded, as one WAV [total] seconds long."""
    sounds = [c for c in sound_cues if (RAW / f"{c['name']}.mp3").exists()]
    missing = sorted({c["name"] for c in sound_cues} - {c["name"] for c in sounds})
    if missing:
        print("  warning: no such sound file:", ", ".join(missing))
    if not sounds:
        subprocess.run([ffmpeg_exe(), "-y", "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i",
                        f"anullsrc=r=44100:cl=stereo", "-t", f"{total:.3f}", str(path)], check=True)
        return
    by_file = {}
    for c in sounds:
        by_file.setdefault(c["name"], []).append(c["t"] + hook)
    inputs, filters, parts = [], [], []
    for n, (name, times) in enumerate(by_file.items()):
        inputs += ["-i", str(RAW / f"{name}.mp3")]
        labels = [f"s{n}_{k}" for k in range(len(times))]
        head = f"[{n}:a]aformat=sample_rates=44100:channel_layouts=stereo,volume={GAIN.get(name, 1.0)}"
        filters.append(head + (f",asplit={len(times)}" if len(times) > 1 else "") + "".join(f"[{l}x]" for l in labels))
        for l, t in zip(labels, times):
            ms = int(round(t * 1000))
            filters.append(f"[{l}x]adelay={ms}|{ms}[{l}]")
            parts.append(f"[{l}]")
    filters.append("".join(parts) + f"amix=inputs={len(parts)}:normalize=0,alimiter=limit=0.9,apad=pad_dur={total + 1:.3f},atrim=0:{total:.3f}[aout]")
    script = path.with_suffix(".txt")
    script.write_text(";\n".join(filters), encoding="utf-8")
    subprocess.run([ffmpeg_exe(), "-y", "-hide_banner", "-loglevel", "error", *inputs,
                    "-filter_complex_script", str(script), "-map", "[aout]", "-ar", "44100", str(path)], check=True)


def contact_sheet(frames, fps, hook, path, columns=6):
    """One frame a second of the recording (the hook left out), the second written under each."""
    picks = list(range(0, len(frames), fps))
    thumb_w, thumb_h = 180, 320
    rows = (len(picks) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * thumb_w, rows * (thumb_h + 30)), "white")
    label = font(22, "Regular")
    draw = ImageDraw.Draw(sheet)
    for n, index in enumerate(picks):
        im = Image.open(frames[index]).convert("RGB").resize((thumb_w, thumb_h), Image.LANCZOS)
        x, y = (n % columns) * thumb_w, (n // columns) * (thumb_h + 30)
        sheet.paste(im, (x, y))
        draw.text((x + 6, y + thumb_h + 2), (f"{index / fps:.0f}秒（動画 {index / fps + hook:.0f}秒）" if hook else f"{index / fps:.0f}秒"), font=label, fill="black")
    sheet.save(path, quality=88)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("id")
    ap.add_argument("--crf", type=int, default=21)
    args = ap.parse_args()

    recorded = ROOT / "build/promo" / args.id
    cues = json.loads((recorded / "cues.json").read_text(encoding="utf-8"))
    script = yaml.safe_load((ROOT / "promo/scenarios" / f"{args.id}.yaml").read_text(encoding="utf-8"))
    captions_file = ROOT / "promo/captions" / f"{args.id}.yaml"
    captions = (yaml.safe_load(captions_file.read_text(encoding="utf-8")) or {}).get("captions", []) if captions_file.exists() else []
    outro = yaml.safe_load((ROOT / "promo/outro.yaml").read_text(encoding="utf-8"))

    fps = cues["fps"]
    frames = sorted(recorded.glob("frame_*.png"))
    if len(frames) != cues["frames"]:
        sys.exit(f"{len(frames)} frames found, cues.json says {cues['frames']}")
    hook_frames = min(len(frames), round(float(script.get("hook_from_end", cues.get("hook_from_end", 0.0))) * fps))
    hook = hook_frames / fps
    body = len(frames) / fps
    outro_seconds = float(outro.get("seconds", 2.0))
    total = hook + body + outro_seconds - OUTRO_FADE

    out_dir = ROOT / "docs/promo/tiktok"
    out_dir.mkdir(parents=True, exist_ok=True)
    name = script.get("name") or "promo"
    target = out_dir / f"{args.id}_{name}.mp4"

    with tempfile.TemporaryDirectory() as tmp:
        tmp = pathlib.Path(tmp)
        outro_path = tmp / "outro.png"
        outro_png(frames[-1], outro, outro_path)

        caption_pictures = []
        for i, c in enumerate(captions):
            png = tmp / f"caption_{i}.png"
            caption_png(str(c["text"]), png)
            caption_pictures.append((float(c["from"]) + hook, float(c["to"]) + hook, Image.open(png).convert("RGBA")))
        outro_picture = Image.open(outro_path).convert("RGB")

        # The sound first, on its own: each file once, split into as many copies as it is heard,
        # each delayed to its cue.
        audio = tmp / "sound.wav"
        make_sound(cues["sounds"], hook, total, audio)

        # Then the picture, frame by frame: put together here and handed to ffmpeg as it goes
        # (ffmpeg's own filters stalled on reading the frames twice, for the hook).
        order = list(range(len(frames) - hook_frames, len(frames))) + list(range(len(frames)))
        count = round(total * fps)
        encoder = subprocess.Popen(
            [ffmpeg_exe(), "-y", "-hide_banner", "-loglevel", "error",
             "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-framerate", str(fps), "-i", "-",
             "-i", str(audio),
             "-map", "0:v", "-map", "1:a",
             "-c:v", "libx264", "-preset", "slow", "-crf", str(args.crf), "-maxrate", "5M", "-bufsize", "10M",
             "-pix_fmt", "yuv420p", "-r", str(fps), "-c:a", "aac", "-b:a", "128k", "-ar", "44100",
             "-movflags", "+faststart", "-t", f"{total:.3f}", str(target)],
            stdin=subprocess.PIPE,
        )
        fade_from = hook + body - OUTRO_FADE
        for n in range(count):
            t = n / fps
            if n < len(order):
                picture = Image.open(frames[order[n]]).convert("RGB")
                for start, end, caption in caption_pictures:
                    if start <= t < end:
                        picture.paste(caption, (0, 0), caption)
                if t >= fade_from:
                    picture = Image.blend(picture, outro_picture, min(1.0, (t - fade_from) / OUTRO_FADE))
            else:
                picture = outro_picture
            encoder.stdin.write(picture.tobytes())
        encoder.stdin.close()
        if encoder.wait() != 0:
            sys.exit("ffmpeg failed")

    sheet = out_dir / f"{args.id}_sheet.jpg"
    contact_sheet(frames, fps, hook, sheet)
    size = target.stat().st_size / 1e6
    print(f"{target} ({total:.2f}s, about {round(total * fps)} frames, {size:.1f} MB)")
    print(f"{sheet}")
    if size > 15:
        print("  warning: over 15 MB; try a larger --crf")


if __name__ == "__main__":
    main()
