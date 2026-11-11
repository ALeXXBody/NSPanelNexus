#!/usr/bin/env python3
"""Generate the HA Marketplace brand images for NSPanel Nexus.

Per the Brands repo spec:
  icon.png 256x256 (icon@2x 512x512), landscape logo with shortest side
  128-256 px (logo@2x 256-512), light set + dark_ prefixed set,
  trimmed, transparent where possible.

Output: custom_components/nspanel_nexus/brand/
"""
from PIL import Image, ImageDraw, ImageFont
import os

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONTS = os.path.join(REPO, "branding", "fonts")
OUT = os.path.join(REPO, "custom_components", "nspanel_nexus", "brand")

ACCENT = (255, 140, 40, 255)          # brand orange
LIGHT_BG = (245, 247, 250, 0)         # transparent — light theme friendly
DARK_BG = (13, 16, 22, 0)
WHITE = (240, 244, 248, 255)
GRAY = (90, 100, 112, 255)
LIGHT_GRAY = (140, 150, 160, 255)


def font(path, size, weight="SemiBold"):
    f = ImageFont.truetype(path, size)
    try:
        f.set_variation_by_name(weight)
    except Exception:
        pass
    return f


def rounded_tile(size, bg_alpha=0, accent=ACCENT, word_color=WHITE, tagline=None):
    """The rounded square panel with the circuit N."""
    px = size * 4
    img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    m = int(px * 0.06)
    if bg_alpha:
        d.rounded_rectangle([m, m, px - m, px - m], radius=int(px * 0.15),
                            fill=(bg_alpha))
    tile = px * 0.62
    cx = cy = px / 2
    d.rounded_rectangle([cx - tile / 2, cy - tile / 2, cx + tile / 2, cy + tile / 2],
                        radius=int(tile * 0.18), outline=accent,
                        width=max(2, int(tile * 0.028)))
    f = font(os.path.join(FONTS, "Comfortaa.ttf"), int(tile * 0.62), "Bold")
    try:
        f.set_variation_by_axes([700])
    except Exception:
        pass
    bbox = d.textbbox((0, 0), "N", font=f, stroke_width=max(2, int(tile * 0.022)))
    w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    d.text((cx - w / 2 - bbox[0], cy - h / 2 - bbox[1]), "N", font=f,
           fill=accent, stroke_width=max(2, int(tile * 0.022)), stroke_fill=accent)
    return img.resize((size, size), Image.LANCZOS)


def wordmark(width, height, accent, text_color, bg=(0, 0, 0, 0)):
    """Landscape logo: tile on the left, wordmark to the right."""
    px = width * 4
    ph = height * 4
    img = Image.new("RGBA", (px, ph), bg)
    d = ImageDraw.Draw(img)
    tile = int(ph * 0.80)
    x0 = int(ph * 0.10)
    small = rounded_tile(tile).resize((tile, tile), Image.LANCZOS)
    img.alpha_composite(small, (x0, (ph - tile) // 2))
    f = font(os.path.join(FONTS, "Montserrat.ttf"), int(ph * 0.34), "SemiBold")
    t1, t2 = "NSPanel ", "Nexus"
    tx = x0 + tile + int(ph * 0.16)
    ty = ph * 0.30
    w1 = d.textlength(t1, font=f)
    d.text((tx, ty), t1, font=f, fill=text_color)
    d.text((tx + w1, ty), t2, font=f, fill=accent)
    f2 = font(os.path.join(FONTS, "Montserrat.ttf"), int(ph * 0.12), "Light")
    tag = "UNIFIED CONTROL"
    ty2 = ph * 0.62
    d.text((tx + int(ph * 0.02), ty2), tag, font=f2, fill=GRAY)
    return img.resize((width, height), Image.LANCZOS)


def main():
    os.makedirs(OUT, exist_ok=True)
    # icons: light set (default) + dark_ set
    for size, name in ((256, "icon.png"), (512, "icon@2x.png")):
        rounded_tile(size).save(os.path.join(OUT, name))
        rounded_tile(size).save(os.path.join(OUT, "dark_" + name))
    # logos: landscape 2:1 — 640x320 (normal), 1280x640 (@2x "best display")
    for w, h, name in ((640, 320, "logo.png"), (1280, 640, "logo@2x.png")):
        wordmark(w, h, ACCENT, WHITE).save(os.path.join(OUT, "dark_" + name))
        wordmark(w, h, ACCENT, (13, 16, 22, 255),
                 bg=(255, 255, 255, 255)).save(os.path.join(OUT, name))
    print("brand images written to", OUT)
    for f in sorted(os.listdir(OUT)):
        print(" ", f, os.path.getsize(os.path.join(OUT, f)), "bytes")


if __name__ == "__main__":
    main()
