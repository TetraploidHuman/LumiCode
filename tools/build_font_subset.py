#!/usr/bin/env python3
"""Regenerate the bundled UI (sans) subsets used by LUMICODE.

界面字族是 Noto Sans CJK SC 的子集（标题 / 正文 / 中文说明）。
代码与标签用的等宽字族由 tools/build_mono_font.py 生成（JetBrains Mono + 中文字形合并）。

WebAssembly 没有系统字体回退，所以这些字形必须内置。

Requires fontTools (`pip install fonttools` / `nix-shell -p python3Packages.fonttools`)
and a copy of the variable Noto Sans CJK collections, e.g. from
https://github.com/notofonts/noto-cjk (Sans/Variable/OTF/NotoSansCJK-VF.otf.ttc).

Usage:
    python3 tools/build_font_subset.py \
        --sans /path/to/NotoSansCJK-VF.otf.ttc \
        --mono /path/to/NotoSansMonoCJK-VF.otf.ttc \
        --src composeApp/src \
        --out composeApp/src/commonMain/composeResources/font

The character set is derived from the Kotlin sources (every literal in the UI) plus
printable ASCII and common CJK punctuation.  Add more codepoints (or drop the subsetting
step entirely and ship the full fonts) if you need arbitrary CJK input.
"""

import argparse
import os
import sys

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

EXTRA_PUNCTUATION = "、。，·—“”‘’《》〈〉！？：；（）【】…※→←↑↓↗↙■□●○◆◇◎≡"
FACES = ("sans",)
WEIGHTS = ((400, "regular"), (700, "bold"))


def collect_characters(src_dir: str) -> list[int]:
    chars: set[str] = set()
    for root, _, files in os.walk(src_dir):
        for name in files:
            if name.endswith(".kt"):
                with open(os.path.join(root, name), encoding="utf-8") as handle:
                    chars |= set(handle.read())
    chars |= {chr(code) for code in range(0x20, 0x7F)}
    chars |= set(EXTRA_PUNCTUATION)
    chars -= {"\n", "\t", "\r"}
    return sorted(ord(char) for char in chars)


def find_sc_face(path: str) -> TTFont:
    """Return the Simplified Chinese face of a Noto Sans CJK collection."""
    for index in range(12):
        try:
            font = TTFont(path, fontNumber=index)
        except Exception:  # noqa: BLE001 - end of collection
            break
        family = ""
        for record in font["name"].names:
            if record.nameID == 1 and record.platformID == 3:
                family = str(record)
                break
        if "SC" in family:
            return font
        font.close()
    raise SystemExit(f"no Simplified Chinese face found in {path}")


def build(source: str, weight: int, out_path: str, codepoints: list[int]) -> None:
    font = find_sc_face(source)
    instantiateVariableFont(font, {"wght": weight}, inplace=True, updateFontNames=True)

    options = subset.Options()
    options.layout_features = ["*"]
    options.notdef_outline = True
    options.recalc_bounds = True
    options.drop_tables += ["DSIG", "vhea", "vmtx"]

    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=codepoints)
    subsetter.subset(font)

    font.flavor = None
    font.save(out_path)
    font.close()
    print(f"{out_path}  {os.path.getsize(out_path)} bytes")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sans", required=True, help="NotoSansCJK-VF.otf.ttc")
    parser.add_argument("--src", default="composeApp/src", help="directory scanned for characters")
    parser.add_argument(
        "--out",
        default="composeApp/src/commonMain/composeResources/font",
        help="output directory",
    )
    args = parser.parse_args()

    codepoints = collect_characters(args.src)
    print(f"{len(codepoints)} codepoints collected from {args.src}")

    font = find_sc_face(args.sans)
    missing = [chr(cp) for cp in codepoints if cp not in font.getBestCmap()]
    font.close()
    if missing:
        print(f"missing glyphs: {' '.join(missing)}", file=sys.stderr)
        raise SystemExit(1)

    os.makedirs(args.out, exist_ok=True)
    for weight, label in WEIGHTS:
        build(args.sans, weight, os.path.join(args.out, f"noto_sans_{label}.otf"), codepoints)


if __name__ == "__main__":
    main()
