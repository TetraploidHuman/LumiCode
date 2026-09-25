# Third-party notices

## Bundled fonts

`composeApp/src/commonMain/composeResources/font/*.otf` are subsets of the
**Noto Sans CJK** / **Noto Sans Mono CJK** (Simplified Chinese) typefaces,
instanced at weights 400 and 700 and reduced to the glyphs used by this demo
(regenerate with `tools/build_font_subset.py`).

- Upstream: <https://github.com/notofonts/noto-cjk>
- Copyright: © 2014–2024 Adobe (<http://www.adobe.com/>) and Google Inc.
- License: **SIL Open Font License, Version 1.1** —
  full text: <https://scripts.sil.org/OFL>

The OFL permits bundling and subsetting provided the fonts are not sold on their own and
this notice is kept with the files. Reserved font names "Noto" and "Source" are not used
for any modified version's family name in this project.

## Runtime dependencies

| Component | License |
| --- | --- |
| Kotlin, Kotlin/Wasm | Apache-2.0 |
| Compose Multiplatform (JetBrains) | Apache-2.0 |
| Skiko / Skia | Apache-2.0 / BSD-3-Clause |
| AndroidX (activity-compose, Compose) | Apache-2.0 |
