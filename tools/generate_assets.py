#!/usr/bin/env python3
"""Regenerate the committed browser-safe fonts and tiny synthesized UI sounds.

Development-only requirements: Python 3, Pillow, Noto Sans CJK JP and
DejaVu Sans Mono. The game itself needs none of these packages at runtime.
"""
import argparse
import math
from pathlib import Path
import re
import struct
import wave
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]


def characters():
    chars = set(chr(i) for i in range(32, 127))
    for start, stop in ((0x3000, 0x3100), (0xFF01, 0xFF61), (0x2190, 0x2194), (0x2160, 0x216C)):
        chars.update(chr(i) for i in range(start, stop))
    chars.update("×÷→←↑↓•·…—–✓✗★☆■□●○▶▷◆◇％ⅠⅡⅢⅣⅤⅥ")
    for folder in (ROOT / "core/src", ROOT / "docs", ROOT / "html/webapp"):
        for path in folder.rglob("*"):
            if path.is_file() and path.suffix in (".java", ".json", ".txt", ".md", ".html"):
                text = path.read_text(encoding="utf-8")
                chars.update(text)
                chars.update(chr(int(x, 16)) for x in re.findall(r"\\u([0-9a-fA-F]{4})", text))
    return sorted((c for c in chars if 32 <= ord(c) < 0xFFFF), key=ord)


def font_atlas(name, size, japanese_path, latin_path, chars):
    japanese = ImageFont.truetype(japanese_path, size, index=0)
    latin = ImageFont.truetype(latin_path, size) if name == "mono" else japanese
    ascent, descent = japanese.getmetrics()
    line_height = ascent + descent + 2
    width = height = 2048
    page = Image.new("RGBA", (width, height), (255, 255, 255, 0))
    draw = ImageDraw.Draw(page)
    x = y = row_height = 2
    glyphs = []
    for char in chars:
        font = latin if ord(char) < 127 else japanese
        left, top, right, bottom = font.getbbox(char, anchor="ls")
        w, h = max(1, right - left), max(1, bottom - top)
        if x + w + 2 > width:
            x = 2
            y += row_height + 2
            row_height = 0
        if y + h + 2 > height:
            raise RuntimeError("Font atlas full; increase atlas size or subset character set")
        draw.text((x - left, y - top), char, font=font, anchor="ls", fill="white")
        advance = round(font.getlength(char))
        glyphs.append(f"char id={ord(char)} x={x} y={y} width={w} height={h} xoffset={left} yoffset={ascent + top} xadvance={advance} page=0 chnl=15")
        x += w + 3
        row_height = max(row_height, h)
    output = ROOT / "assets/fonts"
    output.mkdir(parents=True, exist_ok=True)
    page.save(output / (name + ".png"), optimize=True)
    description = [
        f'info face="Terminal Quest {name}" size={size} bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1',
        f"common lineHeight={line_height} base={ascent} scaleW={width} scaleH={height} pages=1 packed=0",
        f'page id=0 file="{name}.png"', f"chars count={len(glyphs)}", *glyphs, "kernings count=0",
    ]
    (output / (name + ".fnt")).write_text("\n".join(description) + "\n", encoding="utf-8")
    print(f"{name}: {len(glyphs)} glyphs, {y + row_height}px used")


def sounds():
    target = ROOT / "assets/audio"
    target.mkdir(parents=True, exist_ok=True)
    for name, hz, duration in (("confirm", 740, 0.08), ("error", 185, 0.11)):
        count = int(22050 * duration)
        samples = [int(8500 * math.sin(2 * math.pi * hz * i / 22050) * math.sin(math.pi * i / count) ** 2) for i in range(count)]
        with wave.open(str(target / (name + ".wav")), "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(22050)
            out.writeframes(struct.pack("<" + "h" * count, *samples))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--japanese-font", default="/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc")
    parser.add_argument("--latin-font", default="/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf")
    args = parser.parse_args()
    glyphs = characters()
    font_atlas("ui", 22, args.japanese_font, args.latin_font, glyphs)
    font_atlas("mono", 20, args.japanese_font, args.latin_font, glyphs)
    sounds()
