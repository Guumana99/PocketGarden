#!/usr/bin/env python3
"""Measure how loud the sound files are, to set the gains in AudioManager.kt.

Prints, for every .mp3 / .ogg / .wav / .flac in a folder (default: app/src/main/res/raw):

  peak   highest sample, dBFS
  RMS    average level of the whole file, dBFS
  loud   RMS of the loudest 400 ms, dBFS -- the figure that matches how loud a short
         effect sounds, and the one the SoundEffect gains are based on
  gain   with --target: the volume (0..1) that brings "loud" down to that level;
         1.00 means the file is already at or below the target and cannot be raised

Usage:
  python tools/measure_audio.py
  python tools/measure_audio.py app/src/main/res/raw --target -22

Requires:  pip install miniaudio numpy
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import miniaudio
import numpy as np

EXTENSIONS = {".mp3", ".ogg", ".wav", ".flac"}
WINDOW_SECONDS = 0.4


def to_db(value: float) -> float:
    return 20 * np.log10(max(value, 1e-9))


def measure(path: Path) -> dict:
    decoded = miniaudio.decode_file(str(path), output_format=miniaudio.SampleFormat.FLOAT32)
    mono = np.array(decoded.samples, dtype=np.float32).reshape(-1, decoded.nchannels).mean(axis=1)
    window = min(len(mono), int(decoded.sample_rate * WINDOW_SECONDS))
    energy = np.cumsum(np.concatenate([[0.0], mono.astype(np.float64) ** 2]))
    loudest = np.sqrt((energy[window:] - energy[:-window]).max() / window)
    return {
        "seconds": len(mono) / decoded.sample_rate,
        "peak": to_db(float(np.abs(mono).max())),
        "rms": to_db(float(np.sqrt(np.mean(mono ** 2)))),
        "loud": to_db(float(loudest)),
    }


def gain_for(loud_db: float, target_db: float) -> float:
    """Linear volume that moves loud_db to target_db, capped at 1.0 (players cannot amplify)."""
    return min(1.0, 10 ** ((target_db - loud_db) / 20))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Measure the loudness of the sound files.")
    parser.add_argument("folder", type=Path, nargs="?", default=Path("app/src/main/res/raw"))
    parser.add_argument("--target", type=float, default=None,
                        help="target loudness in dBFS; adds a column with the gain that reaches it")
    args = parser.parse_args(argv)

    files = sorted(p for p in args.folder.iterdir() if p.suffix.lower() in EXTENSIONS)
    if not files:
        print(f"error: no sound files in {args.folder}", file=sys.stderr)
        return 1

    header = f"{'file':24} {'sec':>7} {'peak':>7} {'RMS':>7} {'loud':>7}"
    print(header + (f" {'gain':>6}" if args.target is not None else ""))
    for path in files:
        try:
            m = measure(path)
        except miniaudio.DecodeError as error:
            print(f"{path.name:24} could not be decoded: {error}", file=sys.stderr)
            continue
        line = f"{path.name:24} {m['seconds']:7.2f} {m['peak']:7.1f} {m['rms']:7.1f} {m['loud']:7.1f}"
        if args.target is not None:
            line += f" {gain_for(m['loud'], args.target):6.2f}"
        print(line)
    return 0


if __name__ == "__main__":
    sys.exit(main())
