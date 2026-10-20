"""Generates the default SwiftClient capes (64x32 Minecraft cape layout) into ../capes.

Layout: outer face (seen from behind the player) at x1..10 y1..16, inner face x12..21 y1..16,
edges around them, elytra wings at x22..45 y0..21.
Run: python make_capes.py
"""
import json
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "capes")
LOGO = os.path.join(HERE, "..", "..", "launcher", "src", "renderer", "img", "logo.png")

NAVY, NAVY2, DEEP = (19, 24, 48), (30, 39, 80), (8, 10, 22)
ORANGE, ORANGE2, ODEEP = (252, 109, 0), (255, 154, 61), (179, 78, 0)
GOLD, GOLD2 = (255, 200, 61), (255, 226, 140)
WHITE = (236, 238, 246)


def base(color, edge):
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    for x in range(0, 22):
        for y in range(0, 17):
            px[x, y] = edge + (255,)
    for x in range(1, 11):
        for y in range(1, 17):
            px[x, y] = color + (255,)
    for x in range(12, 22):
        for y in range(1, 17):
            px[x, y] = color + (255,)
    # elytra
    for x in range(22, 46):
        for y in range(0, 22):
            px[x, y] = color + (255,)
    return img


def face(img, fn):
    """Paints the outer face via fn(x, y) -> rgb|None, with x 0..9, y 0..15.
    Also mirrored onto the inner face and the elytra wing."""
    px = img.load()
    for x in range(10):
        for y in range(16):
            c = fn(x, y)
            if c:
                px[1 + x, 1 + y] = c + (255,)
                px[21 - x, 1 + y] = c + (255,)
                px[34 + x, 2 + y] = c + (255,)


def border(img, color):
    face(img, lambda x, y: color if x in (0, 9) or y == 15 else None)


def swift():
    img = base(NAVY, ORANGE)
    border(img, ORANGE)
    logo = Image.open(LOGO).convert("RGBA").resize((8, 8), Image.NEAREST)
    lp = logo.load()
    face(img, lambda x, y: lp[x - 1, y - 3][:3] if 1 <= x <= 8 and 3 <= y <= 10 and lp[x - 1, y - 3][3] > 128 else None)
    face(img, lambda x, y: GOLD if y == 13 and 2 <= x <= 7 else None)
    return img


def ember():
    img = base(ORANGE, ODEEP)
    face(img, lambda x, y: ORANGE2 if y < 5 else (ORANGE if y < 11 else ODEEP))
    # gold chevron
    face(img, lambda x, y: GOLD if y == 6 + abs(x - 4.5) // 1 - 2 and 1 <= x <= 8 else None)
    face(img, lambda x, y: GOLD2 if y == 9 + abs(x - 4.5) // 1 - 2 and 1 <= x <= 8 else None)
    return img


def midnight():
    img = base(DEEP, NAVY2)
    stars = {(2, 2), (7, 3), (4, 6), (8, 8), (1, 10), (5, 12), (3, 14), (8, 13)}
    face(img, lambda x, y: (GOLD if (x + y) % 2 else WHITE) if (x, y) in stars else None)
    face(img, lambda x, y: NAVY2 if y == 15 else None)
    return img


def staff():
    img = base(GOLD, ODEEP)
    face(img, lambda x, y: GOLD2 if (x + y) % 4 == 0 else None)
    border(img, ODEEP)
    s = ["####", "#...", "####", "...#", "####"]
    face(img, lambda x, y: NAVY if 3 <= x <= 6 and 5 <= y <= 9 and s[y - 5][x - 3] == "#" else None)
    return img


def founder():
    img = base(NAVY, GOLD)
    border(img, GOLD)
    crown = ["#..##..#", "########", "#.#..#.#", "########"]
    face(img, lambda x, y: GOLD if 1 <= x <= 8 and 3 <= y <= 6 and crown[y - 3][x - 1] == "#" else None)
    face(img, lambda x, y: ORANGE if 9 <= y <= 13 and 3 <= x <= 6 else None)
    return img


CAPES = [
    ("swift", "SwiftClient", "The classic SwiftClient cape", True, swift),
    ("ember", "Ember", "Hot off the forge", True, ember),
    ("midnight", "Midnight", "A quiet night on SwiftSMP", True, midnight),
    ("staff", "Staff", "SwiftSMP staff team", False, staff),
    ("founder", "Founder", "Here from day one", False, founder),
]

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    catalog = []
    for cid, name, desc, public, fn in CAPES:
        fn().save(os.path.join(OUT, f"{cid}.png"))
        catalog.append({"id": cid, "name": name, "description": desc, "public": public})
    with open(os.path.join(OUT, "catalog.json"), "w") as f:
        json.dump(catalog, f, indent=2)
    print("wrote", len(catalog), "capes to", os.path.normpath(OUT))
