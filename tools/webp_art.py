"""Make the app's pictures from their PNG masters: lossless WebP, every pixel the same (#fb-1111).

The pictures are made and kept as PNG under art/png/, laid out as under app/src/main/
(art/png/res/drawable-nodpi/icon_mint.png, art/png/assets/atlas/crops_0.png, ...). The tools that
make them (prepare_*.py, asset_packer.py) write there. This script then writes each one into the
app as lossless WebP beside where the PNG would be (app/src/main/res/drawable-nodpi/icon_mint.webp),
with the colour of its fully clear pixels kept as it is (cwebp's -exact), so that nothing bleeds in
at the edges when the picture is scaled. Every WebP is decoded again and checked against its PNG,
RGBA pixel by pixel; one that differs stops the run. The atlases' manifests are copied over with
their pages' file names ending in .webp. Pictures in the app with no master any more are removed.

  python tools/webp_art.py           # write what is missing or out of date, check every picture
  python tools/webp_art.py --check   # write nothing: check that the app's pictures match their masters

The PNGs stay in git as the working copies (art/ is not part of the app, so not of the APK either).
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
MASTERS = ROOT / "art" / "png"
APP = ROOT / "app" / "src" / "main"
ATLAS = Path("assets") / "atlas"


def app_path(master: Path) -> Path:
    """Where the WebP of [master] goes in the app."""
    return APP / master.relative_to(MASTERS).with_suffix(".webp")


def rgba(path: Path) -> np.ndarray:
    with Image.open(path) as image:
        return np.asarray(image.convert("RGBA"))


def encode(master: Path, out: Path) -> None:
    """Writes [master] to [out] as lossless WebP, clear pixels' colours kept (cwebp -lossless -exact -z 9)."""
    with Image.open(master) as image:
        picture = image.convert("RGBA") if image.mode not in ("RGB", "RGBA") else image.copy()
    out.parent.mkdir(parents=True, exist_ok=True)
    picture.save(out, "WEBP", lossless=True, exact=True, quality=100, method=6)


def same(master: Path, webp: Path) -> bool:
    a, b = rgba(master), rgba(webp)
    return a.shape == b.shape and bool(np.array_equal(a, b))


def manifest_for_app(text: str) -> str:
    manifest = json.loads(text)
    for page in manifest.get("pages", []):
        page["file"] = str(Path(page["file"]).with_suffix(".webp"))
    return json.dumps(manifest, indent=2) + "\n"


def run(check_only: bool) -> int:
    masters = sorted(MASTERS.rglob("*.png"))
    problems: list[str] = []
    written = 0
    png_bytes = webp_bytes = 0
    for master in masters:
        out = app_path(master)
        stale = not out.exists() or out.stat().st_mtime < master.stat().st_mtime
        if stale and not check_only:
            encode(master, out)
            written += 1
        if not out.exists():
            problems.append(f"missing {out.relative_to(ROOT)}")
            continue
        if not same(master, out):
            problems.append(f"pixels differ: {out.relative_to(ROOT)}")
        png_bytes += master.stat().st_size
        webp_bytes += out.stat().st_size
    for manifest in sorted((MASTERS / ATLAS).glob("*_manifest.json")):
        target = APP / ATLAS / manifest.name
        want = manifest_for_app(manifest.read_text(encoding="utf-8"))
        if not check_only and (not target.exists() or target.read_text(encoding="utf-8") != want):
            target.write_text(want, encoding="utf-8", newline="\n")
        if not target.exists() or target.read_text(encoding="utf-8") != want:
            problems.append(f"manifest out of date: {target.relative_to(ROOT)}")
    wanted = {app_path(m) for m in masters}
    for picture in sorted(list(APP.rglob("*.webp")) + list(APP.rglob("*.png"))):
        if picture.parts[-2] == "raw" or picture in wanted:
            continue
        if picture.suffix == ".png" or not check_only:
            if check_only:
                problems.append(f"PNG left in the app: {picture.relative_to(ROOT)}")
            else:
                picture.unlink()
                print(f"removed {picture.relative_to(ROOT)}")
        else:
            problems.append(f"no master for {picture.relative_to(ROOT)}")
    print(f"{len(masters)} pictures, {written} written; PNG {png_bytes / 1e6:.1f} MB -> WebP {webp_bytes / 1e6:.1f} MB")
    for problem in problems:
        print(problem)
    return 1 if problems else 0


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="write nothing, only check")
    sys.exit(run(parser.parse_args().check))


if __name__ == "__main__":
    main()
