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
4. The sound effects go in where cues.json says, thinned: one of a kind a second at most, and of
   tilling, planting and watering only the first two clear, the rest 6 dB quieter.
5. Music, if the script or the captions file names it (bgm: <file in tools/bgm>, bgm_start: <second
   of the song to start from>): cut from there, faded in over 0.2 s and out over the last 1.5 s.
   The music leads: the clear sound effects sit 5 dB under it, and it is not dipped for them.
   Nothing else is done to the song (its licence forbids arranging it: no change of speed or pitch).
6. The whole sound (effects and music together, one gain, so their balance stays) brought to
   -14 LUFS, a limiter keeping the true peak under -1 dB.
7. Two seconds of ending: the name large over the last scene blurred, the lines of promo/outro.yaml.
"""
import argparse
import json
import pathlib
import shutil
import subprocess
import sys
import tempfile

import numpy as np
import yaml
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
FONT = ROOT / "tools/fonts/NotoSansJP.ttf"
RAW = ROOT / "app/src/main/res/raw"
BGM_DIR = ROOT / "tools/bgm"
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

RATE = 44100
BGM_FADE_IN = 0.2
BGM_FADE_OUT = 1.5
SE_UNDER_BGM_DB = 5.0     # the music leads: the clear sound effects this far under it, no ducking
SAME_SOUND_GAP = 1.0      # one sound of a kind a second at most
REPEATS = {"se_tap", "se_plant", "se_water"}   # tilling, planting, watering:
CLEAR_REPEATS = 2         # the first this many clear,
REPEAT_QUIET_DB = 6.0     # the rest this much quieter
TARGET_LUFS = -14.0       # the whole sound, integrated loudness (TikTok's level)
PEAK_CEILING_DB = -1.0    # true peak of the finished mp4 at most this
LIMIT_DB = -2.0           # the limiter's ceiling: under the above, leaving room for AAC and inter-sample peaks


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


def run_ffmpeg(args, stdin=None):
    return subprocess.run([ffmpeg_exe(), "-y", "-hide_banner", "-loglevel", "error", *args],
                          input=stdin, stdout=subprocess.PIPE, check=True).stdout


def thin(sounds):
    """[sounds] with at most one of a kind a second, each with its extra gain in dB
    (the repeats of REPEATS after the first CLEAR_REPEATS: -REPEAT_QUIET_DB)."""
    kept, last, count = [], {}, {}
    for c in sorted(sounds, key=lambda c: c["t"]):
        name = c["name"]
        if name in last and c["t"] - last[name] < SAME_SOUND_GAP - 1e-6:
            continue
        last[name] = c["t"]
        count[name] = count.get(name, 0) + 1
        quiet = name in REPEATS and count[name] > CLEAR_REPEATS
        kept.append((c, -REPEAT_QUIET_DB if quiet else 0.0))
    return kept


def make_effects(sound_cues, hook, total):
    """The sound effects of [sound_cues] (thinned), [hook] seconds later than recorded, [total]
    seconds of float stereo samples at RATE (not limited yet); and the clear ones alone, to measure."""
    samples = round(total * RATE)
    sounds = [c for c in sound_cues if (RAW / f"{c['name']}.mp3").exists()]
    missing = sorted({c["name"] for c in sound_cues} - {c["name"] for c in sounds})
    if missing:
        print("  warning: no such sound file:", ", ".join(missing))
    kept = thin(sounds)
    print(f"  sounds: {len(kept)} of {len(sounds)} kept ({sum(1 for _, g in kept if g < 0)} of them quieter)")
    out = np.zeros((samples, 2), dtype=np.float32)
    clear = np.zeros((samples, 2), dtype=np.float32)
    clips = {}
    for c, extra_db in kept:
        name = c["name"]
        if name not in clips:
            raw = run_ffmpeg(["-i", str(RAW / f"{name}.mp3"), "-f", "f32le", "-ac", "2", "-ar", str(RATE), "-"])
            clips[name] = np.frombuffer(raw, dtype=np.float32).reshape(-1, 2) * GAIN.get(name, 1.0)
        clip = clips[name]
        at = int(round((c["t"] + hook) * RATE))
        if at >= samples:
            continue
        n = min(len(clip), samples - at)
        out[at:at + n] += clip[:n] * 10 ** (extra_db / 20)
        if extra_db == 0:
            clear[at:at + n] += clip[:n]
    return out, clear


def block_rms_db(x, block):
    """RMS of each [block]-sample block of [x] (stereo), in dB."""
    n = len(x) // block
    power = (x[:n * block].astype(np.float64) ** 2).mean(axis=1).reshape(n, block).mean(axis=1)
    return 10 * np.log10(np.maximum(power, 1e-12))


def power_mean_db(levels):
    return 10 * np.log10(np.mean(10 ** (levels / 10))) if len(levels) else -120.0


def add_music(effects, clear, total, bgm, bgm_start):
    """[effects] with the song [bgm] over it, from [bgm_start] s of the song (see the module notes);
    [clear] is the clear effects alone, whose loudness sets the song's."""
    path = BGM_DIR / bgm
    if not path.exists():
        sys.exit(f"no such song: {path}")
    samples = len(effects)
    raw = run_ffmpeg(["-ss", f"{bgm_start:.3f}", "-i", str(path), "-t", f"{total:.3f}",
                      "-f", "f32le", "-ac", "2", "-ar", str(RATE), "-"])
    song = np.frombuffer(raw, dtype=np.float32).reshape(-1, 2)
    if len(song) < samples:
        print(f"  warning: the song ends {(samples - len(song)) / RATE:.1f}s before the video")
        song = np.vstack([song, np.zeros((samples - len(song), 2), dtype=np.float32)])
    song = song[:samples].astype(np.float64)

    # Loudness by 50 ms blocks: the clear effects while they sound (within 30 dB of their loudest
    # block), the song over the whole cut. The song goes SE_UNDER_BGM_DB over the effects.
    block = int(RATE * 0.05)
    fx_levels = block_rms_db(clear, block)
    sounding = fx_levels[fx_levels > fx_levels.max() - 30] if fx_levels.max() > -100 else np.array([])
    song_level = power_mean_db(block_rms_db(song, block))
    if len(sounding):
        fx_level = power_mean_db(sounding)
        gain_db = fx_level + SE_UNDER_BGM_DB - song_level
    else:
        fx_level, gain_db = None, 0.0
    envelope = np.full(samples, 10 ** (gain_db / 20))

    # Fades: in at the start, out over the ending.
    t = np.arange(samples) / RATE
    envelope *= np.clip(t / BGM_FADE_IN, 0, 1)
    envelope *= np.clip((total - t) / BGM_FADE_OUT, 0, 1)

    print(f"  bgm: {bgm} from {bgm_start:.2f}s, "
          + (f"clear effects {fx_level:.1f} dB, " if fx_level is not None else "")
          + f"song {song_level:.1f} dB, gain {gain_db:+.1f} dB (effects {SE_UNDER_BGM_DB:.0f} dB under, no ducking)")
    return effects + (song * envelope[:, None]).astype(np.float32)


def loudness(path_or_samples):
    """(integrated LUFS, true peak dBTP) of a file or of float stereo samples at RATE."""
    if isinstance(path_or_samples, np.ndarray):
        source, stdin = ["-f", "f32le", "-ac", "2", "-ar", str(RATE), "-i", "-"], np.ascontiguousarray(path_or_samples, dtype=np.float32).tobytes()
    else:
        source, stdin = ["-i", str(path_or_samples)], None
    result = subprocess.run([ffmpeg_exe(), "-hide_banner", "-nostats", *source, "-vn",
                             "-af", "ebur128=peak=true:framelog=quiet", "-f", "null", "-"],
                            input=stdin, capture_output=True, check=True)
    text = result.stderr.decode("utf-8", "replace")
    summary = text[text.rindex("Summary:"):]
    lufs = float(summary.split("I:")[1].split("LUFS")[0])
    peak = float(summary.split("Peak:")[1].split("dBFS")[0])
    return lufs, peak


def limited(samples):
    raw = run_ffmpeg(["-f", "f32le", "-ac", "2", "-ar", str(RATE), "-i", "-",
                      "-af", f"alimiter=limit={10 ** (LIMIT_DB / 20):.4f}:level=disabled:attack=2:release=50",
                      "-f", "f32le", "-"], stdin=np.ascontiguousarray(samples, dtype=np.float32).tobytes())
    return np.frombuffer(raw, dtype=np.float32).reshape(-1, 2)


def make_sound(sound_cues, hook, total, path, bgm=None, bgm_start=0.0):
    """The sound effects (and the song, if any) as one WAV [total] seconds long, at TARGET_LUFS."""
    mix, clear = make_effects(sound_cues, hook, total)
    if bgm:
        mix = add_music(mix, clear, total, bgm, bgm_start)
    out = limited(mix)
    if np.abs(mix).max() > 1e-6:
        # One gain for everything; the limiter takes off a little loudness, so measure again and correct.
        gain_db = 0.0
        for _ in range(3):
            lufs, _ = loudness(out)
            if abs(lufs - TARGET_LUFS) < 0.2:
                break
            gain_db += TARGET_LUFS - lufs
            out = limited(mix * 10 ** (gain_db / 20))
        lufs, peak = loudness(out)
        print(f"  loudness: {lufs:.1f} LUFS, true peak {peak:.1f} dBTP (gain {gain_db:+.1f} dB)")
    run_ffmpeg(["-f", "f32le", "-ac", "2", "-ar", str(RATE), "-i", "-", "-c:a", "pcm_s16le", str(path)],
               stdin=np.ascontiguousarray(out, dtype=np.float32).tobytes())


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
    caption_doc = (yaml.safe_load(captions_file.read_text(encoding="utf-8")) or {}) if captions_file.exists() else {}
    captions = caption_doc.get("captions", [])
    # The song: the captions file wins over the script.
    bgm = caption_doc.get("bgm", script.get("bgm"))
    bgm_start = float(caption_doc.get("bgm_start", script.get("bgm_start", 0.0)) or 0.0)
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
        make_sound(cues["sounds"], hook, total, audio, bgm, bgm_start)

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
    lufs, peak = loudness(target)
    print(f"  mp4 sound: {lufs:.1f} LUFS, true peak {peak:.1f} dBTP")
    if peak > PEAK_CEILING_DB:
        print(f"  warning: true peak over {PEAK_CEILING_DB} dB; lower LIMIT_DB")
    if size > 15:
        print("  warning: over 15 MB; try a larger --crf")


if __name__ == "__main__":
    main()
