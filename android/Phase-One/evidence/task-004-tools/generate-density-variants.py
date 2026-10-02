#!/usr/bin/env python3
"""TASK-004: generate density variants for platform-a11y res bitmap.

Lint (IconLocation + IconMissingDensityFolder) requires bitmaps to live in
density folders with hdpi/xhdpi/xxhdpi variants. Upstream shipped a single
512x341 PNG in the densityless res/drawable folder, which Android treats as
mdpi (Bitmap.decodeResourceStream: DENSITY_DEFAULT -> DENSITY_DEFAULT 160).

This script derives the hdpi/xhdpi/xxhdpi variants from the mdpi source with
the same scale factors the framework applies at load time (1.5x / 2x / 3x),
so the rendered size on every density is unchanged from upstream.

Usage: python generate-density-variants.py <res-dir-with-drawable-mdpi>
Requires Pillow (run via `uv run --with pillow python ...`).
"""
import sys
from pathlib import Path

from PIL import Image

SCALES = {"drawable-hdpi": 1.5, "drawable-xhdpi": 2.0, "drawable-xxhdpi": 3.0}


def main() -> int:
    res = Path(sys.argv[1])
    src_dir = res / "drawable-mdpi"
    for src in sorted(src_dir.glob("*.png")):
        img = Image.open(src)
        for folder, scale in SCALES.items():
            out_dir = res / folder
            out_dir.mkdir(exist_ok=True)
            size = (round(img.width * scale), round(img.height * scale))
            out = img.resize(size, Image.LANCZOS)
            out.save(out_dir / src.name)
            print(f"{src.name}: {folder} {size[0]}x{size[1]} (x{scale})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
