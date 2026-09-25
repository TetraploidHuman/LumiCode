#!/usr/bin/env python3
"""Build the bundled monospace family: JetBrains Mono + CJK fallback glyphs.

The editor needs *one* mono family that has both JetBrains Mono's Latin (the IDEA
default coding face) and Chinese glyphs — WebAssembly's Skia canvas has no system
font fallback, so a pure-Latin family would render Chinese as tofu boxes.

Pipeline per weight:
  1. subset Noto Sans Mono CJK SC down to the characters actually used
  2. convert that subset from CFF (OTF) outlines to quadratic `glyf` outlines
  3. merge JetBrains Mono with it — the later font wins on cmap conflicts,
     so Latin comes from JetBrains Mono and CJK from Noto

Usage:
    python3 tools/build_mono_font.py \
        --jetbrains /path/to/JetBrainsMono-2.304/fonts/ttf \
        --noto /path/to/NotoSansMonoCJK-VF.otf.ttc \
        --src composeApp/src \
        --out composeApp/src/commonMain/composeResources/font
"""

from __future__ import annotations

import argparse
import os
import sys

from fontTools import subset
from fontTools.merge import Merger
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont, newTable
from fontTools.ttLib.tables._g_l_y_f import Glyph
from fontTools.varLib.instancer import instantiateVariableFont

EXTRA_PUNCTUATION = "、。，·—“”‘’《》〈〉！？：；（）【】…※→←↑↓↗↙■□●○◆◇◎≡×"
WEIGHTS = (("Regular", 400), ("Medium", 500), ("Bold", 700))
DROP_TABLES = ("CFF ", "CFF2", "VORG", "VARC", "DSIG", "vhea", "vmtx")
VARIATION_TABLES = ("fvar", "gvar", "avar", "cvar", "HVAR", "VVAR", "MVAR", "STAT")
# 这两张表里还藏着 ItemVariationStore（Merger 处理不了）；等宽字体不需要字距调整/字形类，
# 丢掉它们，只保留 GSUB（JetBrains Mono 的编程连字在里面）
MERGE_UNFRIENDLY = ("GDEF", "GPOS", "BASE")


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
    raise SystemExit(f"no Simplified Chinese face in {path}")


def otf_to_ttf(font: TTFont, max_err: float = 1.0) -> TTFont:
    """Convert CFF outlines to quadratic glyf outlines (the classic otf2ttf routine)."""
    glyph_order = font.getGlyphOrder()
    glyph_set = font.getGlyphSet()

    glyf = newTable("glyf")
    glyf.glyphOrder = glyph_order
    glyf.glyphs = {}
    for name in glyph_order:
        pen = TTGlyphPen(glyph_set)
        glyph_set[name].draw(Cu2QuPen(pen, max_err, reverse_direction=True))
        glyf[name] = pen.glyph()

    # maxp 的 recalc 会读每个字形的 xMin/yMin，所以先算命中的包围盒
    for glyph in glyf.glyphs.values():
        glyph.recalcBounds(glyf)

    font["glyf"] = glyf
    font["loca"] = newTable("loca")
    maxp = newTable("maxp")
    maxp.tableVersion = 0x00010000
    maxp.maxZones = 1
    maxp.maxTwilightPoints = 0
    maxp.maxStorage = 0
    maxp.maxFunctionDefs = 0
    maxp.maxInstructionDefs = 0
    maxp.maxStackElements = 0
    maxp.maxSizeOfInstructions = 0
    maxp.numGlyphs = len(glyph_order)
    font["maxp"] = maxp
    maxp.compile(font)
    font["head"].indexToLocFormat = 0
    post = font["post"]
    post.formatType = 2.0
    post.extraNames = []
    post.mapping = {}
    post.glyphOrder = glyph_order
    for tag in DROP_TABLES:
        if tag in font:
            del font[tag]
    font.sfntVersion = "\x00\x01\x00\x00"
    if "glyf" in font and hasattr(glyf, "glyphs"):
        for name, glyph in glyf.glyphs.items():
            if not isinstance(glyph, Glyph):  # pragma: no cover - defensive
                raise SystemExit(f"unexpected glyph type for {name}")
    return font


def strip_variations(path: str, out_path: str) -> str:
    """Drop variable-font tables so two static instances can be merged cleanly."""
    font = TTFont(path)
    for tag in VARIATION_TABLES + MERGE_UNFRIENDLY:
        if tag in font:
            del font[tag]
    font.save(out_path)
    font.close()
    return out_path


def noto_subset_ttf(noto_path: str, codepoints: list[int], weight: int, work_dir: str) -> str:
    font = find_sc_face(noto_path)
    if "fvar" in font:
        instantiateVariableFont(font, {"wght": weight}, inplace=True, updateFontNames=True)

    options = subset.Options()
    options.layout_features = []
    options.notdef_outline = True
    options.recalc_bounds = True
    # 注意：CFF 轮廓此时还不能丢，下一步的 otf_to_ttf 需要它
    options.drop_tables += ["DSIG", "vhea", "vmtx"]
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=codepoints)
    subsetter.subset(font)

    font = otf_to_ttf(font)
    out = os.path.join(work_dir, f"noto-mono-sc-{weight}.ttf")
    font.save(out)
    font.close()
    return out


def build_weight(
    jetbrains_dir: str,
    noto_path: str,
    style: str,
    weight: int,
    codepoints: list[int],
    out_dir: str,
    work_dir: str,
) -> None:
    jbm_path = os.path.join(jetbrains_dir, f"JetBrainsMono-{style}.ttf")
    if not os.path.isfile(jbm_path):
        raise SystemExit(f"missing JetBrains Mono face: {jbm_path}")

    cjk_path = noto_subset_ttf(noto_path, codepoints, weight, work_dir)

    # 两边都清掉可变字体残留（JetBrains Mono 的静态实例里仍带 HVAR/MVAR，会让 Merger 崩）
    clean_jbm = strip_variations(jbm_path, os.path.join(work_dir, f"jbm-clean-{style}.ttf"))
    clean_cjk = strip_variations(cjk_path, os.path.join(work_dir, f"cjk-clean-{style}.ttf"))

    # 实测 Merger 在 cmap 冲突时保留「先合并」的字体 -> 拉丁放 JetBrains Mono 在前，
    # CJK 只在 Noto 里有（不冲突），于是两边各取所需
    merged = Merger().merge([clean_jbm, clean_cjk])

    # 垂直度量沿用 JetBrains Mono，保证代码行高与字形基线一致
    jbm = TTFont(jbm_path)
    for tag in ("hhea", "OS/2"):
        src = jbm[tag]
        dst = merged[tag]
        if tag == "hhea":
            for attr in ("ascent", "descent", "lineGap"):
                setattr(dst, attr, getattr(src, attr))
        else:
            for attr in ("sTypoAscender", "sTypoDescender", "sTypoLineGap",
                         "usWinAscent", "usWinDescent"):
                setattr(dst, attr, getattr(src, attr))
    jbm.close()

    out = os.path.join(out_dir, f"jbmono_{style.lower()}.ttf")
    merged.save(out)
    merged.close()

    # 校验：拉丁取 JBM，中文取 Noto
    check = TTFont(out)
    cmap = check.getBestCmap()
    latin = cmap.get(0x41, "?")
    han = cmap.get(0x4E2D, "?")  # 中
    print(f"{os.path.basename(out):22s} {os.path.getsize(out):>8d} bytes  "
          f"A->{latin[:34]:34s} 中->{han[:34]}")
    check.close()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jetbrains", required=True, help="dir with JetBrainsMono-*.ttf")
    parser.add_argument("--noto", required=True, help="NotoSansMonoCJK-VF.otf.ttc")
    parser.add_argument("--src", default="composeApp/src")
    parser.add_argument("--out", default="composeApp/src/commonMain/composeResources/font")
    parser.add_argument("--work", default="/tmp/lumicode-fonts")
    args = parser.parse_args()

    codepoints = collect_characters(args.src)
    print(f"{len(codepoints)} codepoints from {args.src}")
    os.makedirs(args.out, exist_ok=True)
    os.makedirs(args.work, exist_ok=True)

    missing_check = find_sc_face(args.noto)
    cmap = missing_check.getBestCmap()
    missing = [chr(cp) for cp in codepoints if cp not in cmap]
    missing_check.close()
    if missing:
        print(f"missing CJK glyphs: {''.join(missing)}", file=sys.stderr)
        raise SystemExit(1)

    for style, weight in WEIGHTS:
        build_weight(args.jetbrains, args.noto, style, weight, codepoints, args.out, args.work)


if __name__ == "__main__":
    main()
