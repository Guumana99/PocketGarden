"""Edits a recorded promo script into a TikTok video (promo/README.md).

    python tools/promo_render.py T-001

In:  build/promo/<id>/frame_*.png and cues.json (tools/promo_shoot.py),
     promo/scenarios/<id>.yaml (name, hook_from_end), promo/captions/<id>.yaml, promo/outro.yaml.
Out: docs/promo/tiktok/<id>_<name>.mp4 (1080 x 1920, 30 fps, H.264 + AAC) and
     docs/promo/tiktok/<id>_sheet.jpg (one frame a second, with the time under each),
     both then copied to Google Drive, PocketGarden_Docs/promo/tiktok: the mp4 to 完成/ (only the latest
     mp4 of each video: an older one of the same id goes to the bin), the sheet to 確認用/
     (rclone's "gdrive" remote, as tools/sync_docs_to_drive.ps1; --no-sync to skip).

1. The opening. The captions file's hook: (from, seconds, text, rewind, rewind_label) puts the
   recording's high point first: [seconds] of it from [from] (its sounds too), a large caption over
   it, then the recording from [from] back to 0 played backwards fast in [rewind] seconds (frames
   skipped; no sound of its own, the music goes on) with a small board "<<  [rewind_label]" in the
   middle, then the recording from the start. Captions and sounds move later by that much, and the
   ending's blurred background is the high point's first frame (so the loop joins up).
   With label: false, the high point comes from the take without the label board
   (build/promo/<id>-hook/, tools/promo_shoot.py --hook), and the caption may sit higher
   (text_top: its top as a part of the height, HOOK_TOP otherwise).
   Without hook:, the last [hook_from_end] seconds go first instead, if asked (0, none, unless the script says).
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
7. The ending (1.5 s unless promo/outro.yaml says otherwise): the name large over the last scene blurred,
   the lines under it (see outro_png); the captions file's own outro: (title, lines, ...) in place of
   promo/outro.yaml's. Its words live in promo/outro.yaml alone, for all the videos.
"""
import argparse
import io
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
# Instagram Reels covers the bottom 35% and a button column on the right: one mp4 for TikTok, Shorts and Reels.
# Body captions and the ending sit above 65% and, below 40% of the height, keep 230 px clear of the right edge.
SAFE_RIGHT_LOW = W - 230
CAPTION_CENTRE_Y = int(H * 0.62)   # the body caption's vertical centre (y = 1190)
CAPTION_BOTTOM = int(H * 0.672)    # its lower edge at most (y = 1290, 630 px from the bottom)
OUTRO_BOTTOM = int(H * 0.65)       # the ending's lines stay above the bottom 35%
MAX_CHARS = 15
MAX_LINES = 2

# The game's own gains (data/audio/AudioManager.kt, SoundEffect).
GAIN = {"se_tap": 0.80, "se_harvest": 0.80, "se_level_up": 0.90}

OUTRO_FADE = 0.4

HOOK_TOP = int(H * 0.31)   # the hook's large caption: its top this far down (about a third, under the full-size board)
HOOK_SIZE = 104            # its letters (smaller if a line would not fit)

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
    """One caption on a clear 1080 x 1920 picture, centred at 62% of the height, clear of the right buttons."""
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
        if widest <= SAFE_RIGHT_LOW - SAFE_LEFT or size <= 40:
            break
        size -= 2
    gap = int(size * 0.28)
    boxes = [draw.textbbox((0, 0), line, font=f, stroke_width=stroke) for line in lines]
    heights = [b[3] - b[1] for b in boxes]
    total = sum(heights) + gap * (len(lines) - 1)
    # The block's vertical centre is at 62% of the height; its bottom stays above y = 1290.
    y = min(CAPTION_CENTRE_Y - total // 2, CAPTION_BOTTOM - total)
    if size < 56:
        print(f"  warning: caption shrunk to {size}px to keep clear of the right buttons: {text!r}")
    centre = (SAFE_LEFT + SAFE_RIGHT_LOW) // 2
    for line, box, h in zip(lines, boxes, heights):
        width = box[2] - box[0]
        x = max(SAFE_LEFT, min(centre - width // 2, SAFE_RIGHT_LOW - width))
        draw.text((x - box[0], y - box[1]), line, font=f, fill=(255, 255, 255, 255), stroke_width=stroke, stroke_fill=(52, 36, 20, 255))
        y += h + gap
    image.save(path)


def hook_caption_png(text, path, top=HOOK_TOP):
    """The hook's caption: larger than the others, at most 2 lines, its top at [top]."""
    lines = text.split("\n")
    if len(lines) > MAX_LINES:
        print(f"  warning: hook caption over {MAX_LINES} lines: {text!r}")
    image = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    size = HOOK_SIZE
    while True:
        f = font(size, "Black")
        stroke = max(6, size // 7)
        widest = max(draw.textbbox((0, 0), line, font=f, stroke_width=stroke)[2] - draw.textbbox((0, 0), line, font=f, stroke_width=stroke)[0] for line in lines)
        if widest <= SAFE_RIGHT - SAFE_LEFT or size <= 60:
            break
        size -= 2
    gap = int(size * 0.24)
    y = top
    centre = (SAFE_LEFT + SAFE_RIGHT) // 2
    for line in lines:
        box = draw.textbbox((0, 0), line, font=f, stroke_width=stroke)
        width = box[2] - box[0]
        x = max(SAFE_LEFT, min(centre - width // 2, SAFE_RIGHT - width))
        draw.text((x - box[0], y - box[1]), line, font=f, fill=(255, 255, 255, 255), stroke_width=stroke, stroke_fill=(52, 36, 20, 255))
        y += box[3] - box[1] + gap
    image.save(path)


def rewind_png(label, path):
    """The small board in the middle while the rewind plays: a drawn rewind mark, then [label]."""
    label = label.lstrip("⏪◀≪ ").strip()   # the mark is drawn, not typed (the font has no U+23EA)
    image = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    f = font(56)
    box = draw.textbbox((0, 0), label, font=f)
    text_w, text_h = box[2] - box[0], box[3] - box[1]
    mark_h = 40
    mark_w = int(mark_h * 1.3)
    pad_x, pad_y, gap = 34, 22, 20
    board_w = pad_x * 2 + mark_w + gap + text_w
    board_h = pad_y * 2 + max(text_h, mark_h)
    left = (W - board_w) // 2
    top = (H - board_h) // 2
    draw.rounded_rectangle((left, top, left + board_w, top + board_h), radius=board_h // 2, fill=(40, 30, 20, 175))
    cy = top + board_h // 2
    x = left + pad_x
    half = mark_w // 2
    for dx in (0, half):   # two triangles pointing left
        draw.polygon([(x + dx, cy), (x + dx + half, cy - mark_h // 2), (x + dx + half, cy + mark_h // 2)], fill=(255, 255, 255, 255))
    tx = x + mark_w + gap
    draw.text((tx - box[0], cy - text_h // 2 - box[1]), label, font=f, fill=(255, 255, 255, 255))
    image.save(path)


OUTRO_LINE_SIZE = 62   # a line of the ending given as plain text


def outro_png(last_frame, outro, path):
    """The ending: the last scene blurred and dimmed, the name large, the lines under it.

    Each line is plain text (OUTRO_LINE_SIZE px, white with a dark edge) or
    {text, size, pill: true}: [size] px at most (smaller if it would not fit the safe width),
    and with pill: true on a dark rounded board, to stand out (the pre-registration line)."""
    base = Image.open(last_frame).convert("RGB").filter(ImageFilter.GaussianBlur(outro.get("blur", 18)))
    base = ImageEnhance.Brightness(base).enhance(0.78)
    draw = ImageDraw.Draw(base, "RGBA")
    centre = (SAFE_LEFT + SAFE_RIGHT_LOW) // 2
    room = SAFE_RIGHT_LOW - SAFE_LEFT
    # The name as large as fits between the safe edges, up to 210 px.
    size = 210
    while True:
        title = font(size)
        stroke = max(6, size // 17)
        box = draw.textbbox((0, 0), outro["title"], font=title, stroke_width=stroke)
        if box[2] - box[0] <= room - 20 or size <= 60:
            break
        size -= 4
    y = int(H * 0.27)   # high enough that the last line stays above the bottom 35%
    draw.text((centre - (box[2] - box[0]) // 2 - box[0], y - box[1]), outro["title"], font=title, fill="white", stroke_width=stroke, stroke_fill=(52, 36, 20))
    y += box[3] - box[1] + 70
    lines = list(outro.get("lines", []))
    if outro.get("above"):   # one more line just above the pill (the first pill line), per video
        at = next((i for i, l in enumerate(lines) if isinstance(l, dict) and l.get("pill")), len(lines))
        lines.insert(at, {"text": str(outro["above"]), "size": 52})
    for line in lines:
        spec = line if isinstance(line, dict) else {"text": str(line)}
        text = str(spec["text"])
        pill = bool(spec.get("pill"))
        size = int(spec.get("size", OUTRO_LINE_SIZE))
        pad_x, pad_y = (30, 18) if pill else (0, 0)
        while True:
            f = font(size)
            stroke = 0 if pill else 7
            b = draw.textbbox((0, 0), text, font=f, stroke_width=stroke)
            if (b[2] - b[0]) + 2 * pad_x <= room or size <= 30:
                break
            size -= 1
        if (b[2] - b[0]) + 2 * pad_x > room:
            print(f"  warning: ending line wider than the safe area: {text}")
        w, h = b[2] - b[0], b[3] - b[1]
        x = centre - w // 2
        if pill:
            y += 6
            draw.rounded_rectangle((x - pad_x, y - pad_y, x + w + pad_x, y + h + pad_y), radius=(h + 2 * pad_y) // 2,
                                   fill=(52, 36, 20, 225), outline=(255, 236, 190, 255), width=4)
            draw.text((x - b[0], y - b[1]), text, font=f, fill=(255, 236, 190))
            y += h + pad_y + 40
        else:
            draw.text((x - b[0], y - b[1]), text, font=f, fill="white", stroke_width=stroke, stroke_fill=(52, 36, 20))
            y += h + 34
        if y > OUTRO_BOTTOM:
            print(f"  warning: ending runs into the bottom 35%: {text}")
    base.save(path)


DRIVE_FOLDER = "gdrive:PocketGarden_Docs/promo/tiktok"
DRIVE_FINISHED = "完成"     # the mp4s to post, the latest of each only
DRIVE_REVIEW = "確認用"     # the contact sheets and still frames (docs/promo/tiktok/frames/*), all in one folder


def rclone_exe():
    found = shutil.which("rclone")
    if found:
        return found
    packages = pathlib.Path.home() / "AppData/Local/Microsoft/WinGet/Packages"
    return next((str(p) for p in packages.glob("Rclone.Rclone*/*/rclone.exe")), None)


def drive_folder_of(path):
    """Where a file of docs/promo/tiktok/ goes on Drive: mp4s to 完成/, pictures to 確認用/."""
    return f"{DRIVE_FOLDER}/{DRIVE_FINISHED if path.suffix == '.mp4' else DRIVE_REVIEW}"


def sync_to_drive(files, video_id=None):
    """Copies [files] to Drive (changed ones only, each to drive_folder_of) and checks them there.
    Other mp4s of [video_id] in 完成/ (an older name) go to Drive's bin."""
    rclone = rclone_exe()
    if not rclone:
        print("  warning: rclone not found; not copied to Drive (winget install --id Rclone.Rclone -e)")
        return

    def run(*args):
        result = subprocess.run([rclone, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
        if result.returncode != 0:
            print(f"  warning: rclone {args[0]} failed: " + result.stderr.strip())
        return result.returncode == 0

    by_folder = {}
    for f in files:
        by_folder.setdefault((f.parent, drive_folder_of(f)), []).append(f)
    for (local, remote), group in by_folder.items():
        names = [arg for f in group for arg in ("--include", f.name)]
        if not (run("copy", str(local), remote, *names, "--checksum") and run("check", str(local), remote, *names, "--one-way")):
            print(f"  not on Drive: {', '.join(f.name for f in group)}")
            return
        print(f"  on Drive: {remote}/ ({', '.join(f.name for f in group)})")
    if video_id:
        keep = [f.name for f in files if f.suffix == ".mp4"]
        filters = [arg for name in keep for arg in ("--filter", f"- {name}")] + ["--filter", f"+ {video_id}_*.mp4", "--filter", "- *"]
        run("delete", f"{DRIVE_FOLDER}/{DRIVE_FINISHED}", *filters)   # Drive keeps them in the bin


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


# --- Letter tags under flowers (the captions file's tags:) -----------------------------------------
# The picture has no marks for "A to D", so a tag is drawn under each flower. The flowers are found by
# their bloom colour in each frame (the camera may be moving), so the tag follows the flower.
BLOOM_MASKS = {
    "red": lambda h, s, v: ((h < 12) | (h > 345)) & (s > 0.6) & (v > 0.55),
    "yellow": lambda h, s, v: (h > 32) & (h < 50) & (s > 0.7) & (v > 0.75),
    "blue": lambda h, s, v: (h > 210) & (h < 250) & (s > 0.6) & (v > 0.4),
    "pink": lambda h, s, v: (h > 320) & (h < 350) & (s > 0.15) & (s < 0.55) & (v > 0.75),
}


def draw_tags(picture, spec, t):
    """Draws the tags of [spec] (items: [{text, colour}], from, to, emphasize: {text, from}) on [picture] at body time [t]."""
    if not spec or not (float(spec.get("from", 0.0)) <= t < float(spec.get("to", 1e9))):
        return picture
    small = picture.resize((W // 2, H // 2))
    hsv = np.array(small.convert("HSV")).astype(float)
    h, s, v = hsv[..., 0] * 360 / 255, hsv[..., 1] / 255, hsv[..., 2] / 255
    draw = ImageDraw.Draw(picture, "RGBA")
    emph = spec.get("emphasize") or {}
    for item in spec["items"]:
        mask = BLOOM_MASKS[item["colour"]](h, s, v)
        area = int(mask.sum())
        if area < 800:   # the flower is out of the picture (or only a scrap of it)
            continue
        ys, xs = np.nonzero(mask)
        cx, cy = float(xs.mean()) * 2, float(ys.mean()) * 2
        unit = (area ** 0.5) * 2   # about the bloom's size in the picture
        size = int(max(34, min(78, unit * 0.62)))
        f = font(size)
        text = str(item["text"])
        b = draw.textbbox((0, 0), text, font=f)
        w, hh = b[2] - b[0], b[3] - b[1]
        pad_x, pad_y = int(size * 0.5), int(size * 0.28)
        yy = cy + unit * 1.55
        xx = cx
        chosen = bool(emph) and emph.get("text") == text and t >= float(emph.get("from", 0.0))
        fill = (255, 214, 92, 245) if chosen else (253, 240, 205, 235)
        box = (xx - w / 2 - pad_x, yy - hh / 2 - pad_y, xx + w / 2 + pad_x, yy + hh / 2 + pad_y)
        draw.rounded_rectangle(box, radius=(hh + 2 * pad_y) // 2, fill=fill, outline=(120, 82, 44, 255), width=4)
        draw.text((xx - w / 2 - b[0], yy - hh / 2 - b[1]), text, font=f, fill=(74, 50, 30))
    return picture


def contact_sheet(frames, fps, hook, path, columns=6, captions=(), tags=None):
    """One frame a second of the recording (the hook left out), the second written under each."""
    picks = list(range(0, len(frames), fps))
    thumb_w, thumb_h = 180, 320
    rows = (len(picks) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * thumb_w, rows * (thumb_h + 30)), "white")
    label = font(22, "Regular")
    draw = ImageDraw.Draw(sheet)
    for n, index in enumerate(picks):
        full = Image.open(frames[index]).convert("RGB")
        full = draw_tags(full, tags, index / fps)
        for start, end, caption in captions:   # the captions as they are in the video (body time)
            if start <= index / fps < end:
                full.paste(caption, (0, 0), caption)
        im = full.resize((thumb_w, thumb_h), Image.LANCZOS)
        x, y = (n % columns) * thumb_w, (n // columns) * (thumb_h + 30)
        sheet.paste(im, (x, y))
        draw.text((x + 6, y + thumb_h + 2), (f"{index / fps:.0f}秒（動画 {index / fps + hook:.1f}秒）" if hook else f"{index / fps:.0f}秒"), font=label, fill="black")
    sheet.save(path, quality=88)


def strip(video, times, path, height=770):
    """Frames of [video] at [times] (seconds) side by side, each second written under it."""
    pictures = []
    for t in times:
        raw = run_ffmpeg(["-ss", f"{t:.3f}", "-i", str(video), "-frames:v", "1", "-f", "image2pipe", "-vcodec", "png", "-"])
        im = Image.open(io.BytesIO(raw)).convert("RGB")
        pictures.append(im.resize((round(im.width * height / im.height), height), Image.LANCZOS))
    gap, label_h = 12, 60
    sheet = Image.new("RGB", (sum(p.width for p in pictures) + gap * (len(pictures) - 1), height + label_h), "white")
    draw = ImageDraw.Draw(sheet)
    label = font(30, "Regular")
    x = 0
    for t, p in zip(times, pictures):
        sheet.paste(p, (x, 0))
        text = f"{t:.1f} 秒"
        b = draw.textbbox((0, 0), text, font=label)
        draw.text((x + (p.width - (b[2] - b[0])) // 2, height + 12), text, font=label, fill="black")
        x += p.width + gap
    sheet.save(path, quality=90)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("id")
    ap.add_argument("--crf", type=int, default=21)
    ap.add_argument("--no-sync", action="store_true", help="do not copy the results to Google Drive")
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
    # A video's own ending (the captions file's outro:) over the shared one, key by key.
    outro = {**outro, **(caption_doc.get("outro") or {})}

    fps = cues["fps"]
    frames = sorted(recorded.glob("frame_*.png"))
    if len(frames) != cues["frames"]:
        sys.exit(f"{len(frames)} frames found, cues.json says {cues['frames']}")
    # The opening: (frame indices, in order) played before the recording from its start.
    hook_spec = caption_doc.get("hook")
    hook_sounds = []
    paths = list(frames)   # the picture of each frame of the video, by index (the hook's take after the recording's)
    if hook_spec:
        start = min(len(frames) - 1, round(float(hook_spec["from"]) * fps))
        shown = max(1, round(float(hook_spec.get("seconds", 1.6)) * fps))
        rewind_n = round(float(hook_spec.get("rewind", 0.8)) * fps)
        shown_frames = [min(len(frames) - 1, start + i) for i in range(shown)]
        hook_cues = cues
        if hook_spec.get("label", True) is False:
            take = recorded.with_name(args.id + "-hook")
            take_frames = sorted(take.glob("frame_*.png"))
            if len(take_frames) < start + shown:
                sys.exit(f"the hook's take ({take}) has {len(take_frames)} frames, {start + shown} needed: python tools/promo_shoot.py {args.id} --hook")
            hook_cues = json.loads((take / "cues.json").read_text(encoding="utf-8"))
            paths += take_frames[start:start + shown]
            shown_frames = list(range(len(frames), len(frames) + shown))
        # From the high point back to the first frame, evenly skipped (the first frame itself last).
        rewind_frames = [round(start * (1 - (i + 1) / rewind_n)) for i in range(rewind_n)] if rewind_n else []
        opening = shown_frames + rewind_frames
        # The high point's own sounds, at their place in it.
        hook_sounds = [{**c, "t": c["t"] - start / fps} for c in hook_cues["sounds"] if start / fps <= c["t"] < (start + shown) / fps]
        rewind_from, rewind_to = len(shown_frames) / fps, len(opening) / fps
        outro_frame = paths[shown_frames[0]]
    else:
        n = min(len(frames), round(float(script.get("hook_from_end", cues.get("hook_from_end", 0.0))) * fps))
        opening = list(range(len(frames) - n, len(frames)))
        outro_frame = frames[-1]
    hook_frames = len(opening)
    hook = hook_frames / fps
    body = len(frames) / fps
    outro_seconds = float(outro.get("seconds", 1.5))
    total = hook + body + outro_seconds - OUTRO_FADE

    out_dir = ROOT / "docs/promo/tiktok"
    out_dir.mkdir(parents=True, exist_ok=True)
    name = script.get("name") or "promo"
    target = out_dir / f"{args.id}_{name}.mp4"

    with tempfile.TemporaryDirectory() as tmp:
        tmp = pathlib.Path(tmp)
        outro_path = tmp / "outro.png"
        outro_png(outro_frame, outro, outro_path)

        caption_pictures = []
        for i, c in enumerate(captions):
            png = tmp / f"caption_{i}.png"
            caption_png(str(c["text"]), png)
            caption_pictures.append((float(c["from"]) + hook, float(c["to"]) + hook, Image.open(png).convert("RGBA")))
        if hook_spec:
            hook_png = tmp / "hook.png"
            hook_caption_png(str(hook_spec.get("text", "")), hook_png,
                             int(H * float(hook_spec["text_top"])) if "text_top" in hook_spec else HOOK_TOP)
            if hook_spec.get("text"):
                caption_pictures.append((0.0, rewind_from, Image.open(hook_png).convert("RGBA")))
            if hook_spec.get("rewind_label") and rewind_to > rewind_from:
                rewind_path = tmp / "rewind.png"
                rewind_png(str(hook_spec["rewind_label"]), rewind_path)
                caption_pictures.append((rewind_from, rewind_to, Image.open(rewind_path).convert("RGBA")))
        outro_picture = Image.open(outro_path).convert("RGB")

        # The sound first, on its own: each file once, split into as many copies as it is heard,
        # each delayed to its cue.
        audio = tmp / "sound.wav"
        make_sound(hook_sounds + [{**c, "t": c["t"] + hook} for c in cues["sounds"]], 0.0, total, audio, bgm, bgm_start)

        # Then the picture, frame by frame: put together here and handed to ffmpeg as it goes
        # (ffmpeg's own filters stalled on reading the frames twice, for the hook).
        order = opening + list(range(len(frames)))
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
                picture = Image.open(paths[order[n]]).convert("RGB")
                if order[n] < len(frames) and n >= len(opening):   # the recording itself (not the hook's take)
                    picture = draw_tags(picture, caption_doc.get("tags"), (n - len(opening)) / fps)
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
    contact_sheet(frames, fps, hook, sheet, captions=[(a - hook, b - hook, c) for a, b, c in caption_pictures[:len(captions)]], tags=caption_doc.get("tags"))
    # The opening (the high point, the rewind, the start) and the ending, to look over.
    stills = out_dir / "frames"
    stills.mkdir(exist_ok=True)
    opening = stills / f"{args.id}_opening.jpg"
    strip(target, [0.0, 1.0, 2.0, 3.0], opening)   # the first three seconds (the decisive ones)
    ending = stills / f"{args.id}_ending.jpg"
    end = hook + body - OUTRO_FADE
    strip(target, [end - 0.5, end + 0.2, total - 0.6], ending)
    answer_stills = caption_doc.get("answer_stills")
    if answer_stills:
        answer = stills / f"{args.id}_answer.jpg"
        strip(target, [float(a) + hook for a in answer_stills], answer)
    size = target.stat().st_size / 1e6
    print(f"{target} ({total:.2f}s, about {round(total * fps)} frames, {size:.1f} MB)")
    print(f"{sheet}")
    lufs, peak = loudness(target)
    print(f"  mp4 sound: {lufs:.1f} LUFS, true peak {peak:.1f} dBTP")
    if peak > PEAK_CEILING_DB:
        print(f"  warning: true peak over {PEAK_CEILING_DB} dB; lower LIMIT_DB")
    if size > 15:
        print("  warning: over 15 MB; try a larger --crf")
    if not args.no_sync:
        sync_to_drive([target, sheet, opening, ending] + ([answer] if answer_stills else []), args.id)


if __name__ == "__main__":
    main()
