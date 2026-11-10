"""Records a promo script on the emulator, frame by frame (promo/README.md).

    python tools/promo_shoot.py T-001            # build-free: the promo APK must be installed
    python tools/promo_shoot.py T-001 --install  # assemblePromo's APK installed first

Turns promo/scenarios/<id>.yaml into JSON, hands it (in the intent) to the promo build on the emulator, starts the
recorder and waits for it, then brings the frames and cues.json back to build/promo/<id>/.
Only ever talks to the emulator (emulator-5554 unless --serial says otherwise), never to a phone.
"""
import argparse
import base64
import json
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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("id", help="script id, e.g. T-001 (promo/scenarios/<id>.yaml)")
    ap.add_argument("--serial", default="emulator-5554")
    ap.add_argument("--install", action="store_true", help="install the promo APK first")
    ap.add_argument("--timeout", type=int, default=3600, help="seconds to wait for the recording")
    args = ap.parse_args()
    if not args.serial.startswith("emulator-"):
        sys.exit("only an emulator is used for recording (--serial emulator-NNNN)")

    script = ROOT / "promo/scenarios" / f"{args.id}.yaml"
    data = yaml.safe_load(script.read_text(encoding="utf-8"))
    local = ROOT / "build/promo" / data["id"]
    if local.exists():
        shutil.rmtree(local)
    local.mkdir(parents=True)
    (local / "scenario.json").write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")

    if args.install:
        adb(args.serial, "install", "-r", str(APK))
    adb(args.serial, "shell", "am", "force-stop", PACKAGE)
    # The script goes in the intent: a file pushed into the app's folder would belong to the shell,
    # and the app could not always read it (nor write next to it). The app clears its own output.
    encoded = base64.b64encode(json.dumps(data, ensure_ascii=False).encode("utf-8")).decode("ascii")
    adb(args.serial, "shell", "am", "start", "-n", ACTIVITY, "--es", "scenario_b64", encoded)

    started = time.time()
    remote_out = f"{REMOTE}/out/{data['id']}"
    while True:
        time.sleep(5)
        listing = adb(args.serial, "shell", f"ls {remote_out} 2>/dev/null | grep -c frame_; ls {remote_out}/done {REMOTE}/error.txt 2>/dev/null", check=False, capture=True) or ""
        lines = listing.split()
        frames = lines[0] if lines else "0"
        if any(line.endswith("error.txt") for line in lines):
            sys.exit("recorder failed: " + (adb(args.serial, "shell", "cat", f"{REMOTE}/error.txt", capture=True) or ""))
        if any(line.endswith("/done") for line in lines):
            break
        print(f"  {int(time.time() - started)}s: {frames} frames", flush=True)
        if time.time() - started > args.timeout:
            sys.exit("timed out")
    print("pulling frames ...", flush=True)
    adb(args.serial, "pull", remote_out + "/.", str(local))
    print((local / "done").read_text().strip())
    print(f"frames in {local}")


if __name__ == "__main__":
    main()
