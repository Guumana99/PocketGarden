"""Records a promo script on the emulator, frame by frame (promo/README.md).

    python tools/promo_shoot.py T-001            # build-free: the promo APK must be installed
    python tools/promo_shoot.py T-001 --install  # assemblePromo's APK installed first
    python tools/promo_shoot.py T-004 --hook     # the opening's own take (see below)

Turns promo/scenarios/<id>.yaml into JSON, hands it (in the intent) to the promo build on the emulator, starts the
recorder and waits for it, then brings the frames and cues.json back to build/promo/<id>/.
--hook records the opening's own take into build/promo/<id>-hook/: the same script (same seed, so
the same moves) without the label board (no day_label or label_opacity), ending just after the
captions file's hook: (from + seconds). promo_render.py uses it for the high point when the hook
says label: false, so the hook's large caption has the top of the screen to itself.
A script with `parts:` (each a start / setup / camera / timeline of its own) is recorded part by part into
build/promo/<id>-<n>/ and joined into build/promo/<id>/ (--part N records only that one again).
Only ever talks to the emulator (emulator-5554 unless --serial says otherwise), never to a phone.
"""
import argparse
import base64
import json
import os
import pathlib
import shutil
import subprocess
import sys
import time

import yaml

ROOT = pathlib.Path(__file__).resolve().parent.parent
PACKAGE = "com.studioguu.miniaturegarden.promo"
ACTIVITY = f"{PACKAGE}/com.studioguu.miniaturegarden.promo.PromoActivity"
REMOTE = f"/sdcard/Android/data/{PACKAGE}/files/promo"
APK = ROOT / "app/build/outputs/apk/promo/app-promo.apk"


def adb(serial, *args, check=True, capture=False):
    cmd = ["adb", "-s", serial, *args]
    result = subprocess.run(cmd, check=check, capture_output=capture, text=True, encoding="utf-8", errors="replace")
    return result.stdout if capture else None


def record(serial, data, local, timeout):
    """Records the script [data] on the emulator and brings its frames and cues.json to [local]."""
    if local.exists():
        shutil.rmtree(local)
    local.mkdir(parents=True)
    (local / "scenario.json").write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")

    adb(serial, "shell", "am", "force-stop", PACKAGE)
    # The last recording goes first, its `done` above all: seen before the app has cleared it, it
    # would have the frames pulled while the new ones are still being made.
    adb(serial, "shell", "rm", "-rf", f"{REMOTE}/out/{data['id']}", f"{REMOTE}/error.txt", check=False)
    # The script goes in the intent: a file pushed into the app's folder would belong to the shell,
    # and the app could not always read it (nor write next to it). The app clears its own output.
    encoded = base64.b64encode(json.dumps(data, ensure_ascii=False).encode("utf-8")).decode("ascii")
    adb(serial, "shell", "am", "start", "-n", ACTIVITY, "--es", "scenario_b64", encoded)

    started = time.time()
    remote_out = f"{REMOTE}/out/{data['id']}"
    while True:
        time.sleep(5)
        listing = adb(serial, "shell", f"ls {remote_out} 2>/dev/null | grep -c frame_; ls {remote_out}/done {REMOTE}/error.txt 2>/dev/null", check=False, capture=True) or ""
        lines = listing.split()
        frames = lines[0] if lines else "0"
        if any(line.endswith("error.txt") for line in lines):
            sys.exit("recorder failed: " + (adb(serial, "shell", "cat", f"{REMOTE}/error.txt", capture=True) or ""))
        if any(line.endswith("/done") for line in lines):
            break
        print(f"  {int(time.time() - started)}s: {frames} frames", flush=True)
        if time.time() - started > timeout:
            sys.exit("timed out")
    print("pulling frames ...", flush=True)
    adb(serial, "pull", remote_out + "/.", str(local))
    print((local / "done").read_text().strip())
    # The emulator's storage is small (a 20 s recording is about 0.7 GB): once the frames are
    # here, they go from there.
    adb(serial, "shell", "rm", "-rf", remote_out, check=False)
    print(f"frames in {local}")


def join(video_id, takes, local):
    """The takes of a script with `parts:` one after another in [local], as one recording: the frames
    renumbered (hard links) and cues.json with each take's sounds moved to where it starts."""
    if local.exists():
        shutil.rmtree(local)
    local.mkdir(parents=True)
    count, fps, sounds, warnings, starts = 0, None, [], [], []
    for take in takes:
        cues = json.loads((take / "cues.json").read_text(encoding="utf-8"))
        fps = fps or cues["fps"]
        if cues["fps"] != fps:
            sys.exit("the parts must have the same fps")
        start = count / fps
        starts.append(start)
        sounds += [{**c, "t": c["t"] + start} for c in cues["sounds"]]
        warnings += cues.get("warnings", [])
        for frame in sorted(take.glob("frame_*.png")):
            os.link(frame, local / f"frame_{count:05d}.png")
            count += 1
    cues = {"id": video_id, "fps": fps, "frames": count, "duration": count / fps, "part_starts": starts,
            "sounds": sounds, "warnings": warnings}
    (local / "cues.json").write_text(json.dumps(cues, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"joined {len(takes)} parts: {count} frames ({count / fps:.2f}s), starting at {starts}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("id", help="script id, e.g. T-001 (promo/scenarios/<id>.yaml)")
    ap.add_argument("--serial", default="emulator-5554")
    ap.add_argument("--install", action="store_true", help="install the promo APK first")
    ap.add_argument("--hook", action="store_true", help="record the opening's take without the label board")
    ap.add_argument("--part", type=int, action="append", help="with parts: record only this one (from 1; may be repeated), then join")
    ap.add_argument("--timeout", type=int, default=3600, help="seconds to wait for the recording")
    args = ap.parse_args()
    if not args.serial.startswith("emulator-"):
        sys.exit("only an emulator is used for recording (--serial emulator-NNNN)")

    script = ROOT / "promo/scenarios" / f"{args.id}.yaml"
    data = yaml.safe_load(script.read_text(encoding="utf-8"))
    local = ROOT / "build/promo" / data["id"]
    if args.install:
        adb(args.serial, "install", "-r", str(APK))
    if "parts" in data:
        if args.hook:
            sys.exit("--hook is not for a script with parts")
        shared = {k: v for k, v in data.items() if k != "parts"}
        takes = []
        for n, part in enumerate(data["parts"], 1):
            take = local.with_name(f"{data['id']}-{n}")
            takes.append(take)
            if args.part and n not in args.part:
                continue
            print(f"part {n} of {len(data['parts'])}", flush=True)
            record(args.serial, {**shared, **part, "id": f"{data['id']}-{n}"}, take, args.timeout)
        join(data["id"], takes, local)
        return
    if args.hook:
        hook = yaml.safe_load((ROOT / "promo/captions" / f"{args.id}.yaml").read_text(encoding="utf-8"))["hook"]
        end = float(hook["from"]) + float(hook.get("seconds", 1.6)) + 0.2
        data["timeline"] = [cue for cue in data["timeline"]
                            if not {"day_label", "label_opacity", "end"} & set(cue) and float(cue.get("at", 0)) < end]
        data["timeline"].append({"at": end, "end": True})
        local = local.with_name(data["id"] + "-hook")
    record(args.serial, data, local, args.timeout)


if __name__ == "__main__":
    main()
