"""Generates the Microsoft Store (MSIX) tile images in build/appx from the SwiftClient logo.

Run from launcher/: python build/make_store_assets.py
electron-builder picks these up by name for the appx target. No scale-qualified names
(e.g. *.targetsize-44_altform-unplated.png): electron-builder's bundled makepri.exe crashes on them.
"""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "appx")
LOGO = os.path.join(HERE, "..", "src", "renderer", "img", "logo.png")
FONT = os.path.join(HERE, "..", "src", "renderer", "fonts", "PixelifySans.ttf")

NAVY = (13, 16, 32, 255)
ORANGE = (252, 109, 0)
WHITE = (236, 238, 246)


def tile(w, h, logo_frac=0.62, transparent=False, wordmark=False):
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0) if transparent else NAVY)
    if not transparent:
        glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        ImageDraw.Draw(glow).ellipse([w * 0.15, -h * 0.4, w * 0.85, h * 0.6], fill=ORANGE + (70,))
        img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(max(w, h) // 8)))
    size = int(min(w, h) * logo_frac)
    logo = Image.open(LOGO).convert("RGBA").resize((size, size), Image.NEAREST)
    if wordmark:
        f = ImageFont.truetype(FONT, int(h * 0.22))
        try:
            f.set_variation_by_axes([700])
        except Exception:
            pass
        d = ImageDraw.Draw(img)
        tw = d.textlength("SwiftClient", font=f)
        total = size + int(h * 0.08) + tw
        x = int((w - total) / 2)
        img.alpha_composite(logo, (x, (h - size) // 2))
        tx = x + size + int(h * 0.08)
        ty = (h - f.size) // 2 - int(f.size * 0.1)
        d.text((tx, ty), "Swift", font=f, fill=WHITE)
        d.text((tx + d.textlength("Swift", font=f), ty), "Client", font=f, fill=ORANGE)
    else:
        img.alpha_composite(logo, ((w - size) // 2, (h - size) // 2))
    return img


ASSETS = {
    "StoreLogo.png": (50, 50, dict(logo_frac=0.9)),
    "Square44x44Logo.png": (44, 44, dict(logo_frac=0.9, transparent=True)),
    "Square150x150Logo.png": (150, 150, dict()),
    "SmallTile.png": (71, 71, dict(logo_frac=0.7)),
    "LargeTile.png": (310, 310, dict()),
    "Wide310x150Logo.png": (310, 150, dict(logo_frac=0.55, wordmark=True)),
    "SplashScreen.png": (620, 300, dict(logo_frac=0.5, wordmark=True)),
}

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, (w, h, kw) in ASSETS.items():
        tile(w, h, **kw).save(os.path.join(OUT, name))
    print("wrote", len(ASSETS), "tiles to", OUT)
