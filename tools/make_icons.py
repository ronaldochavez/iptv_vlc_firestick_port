"""Draws the launcher icon (mipmaps) and the 320x180 Android TV / Fire TV banner.

Usage (needs Pillow and the Windows Segoe UI fonts):
    python tools/make_icons.py app/src/main/res
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

RES = Path(sys.argv[1])
BRAND = (145, 70, 255)  # Twitch-style purple #9146FF
BRAND_LIGHT = (169, 112, 255)  # lighter purple for text on dark
DARK = (11, 14, 19)
WHITE = (255, 255, 255)
SS = 4  # supersampling for smooth edges


def mark(size: int) -> Image.Image:
    """Purple rounded square with a white TV outline and a play triangle."""
    s = size * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.22), fill=BRAND)
    # Screen
    w = int(s * 0.07)
    box = [int(s * 0.17), int(s * 0.30), int(s * 0.83), int(s * 0.78)]
    d.rounded_rectangle(box, radius=int(s * 0.07), outline=WHITE, width=w)
    # Antenna
    cx, top = s // 2, box[1]
    d.line([(cx, top), (int(s * 0.36), int(s * 0.16))], fill=WHITE, width=w)
    d.line([(cx, top), (int(s * 0.64), int(s * 0.16))], fill=WHITE, width=w)
    # Play triangle
    mid_y = (box[1] + box[3]) // 2
    tri = [(int(s * 0.44), mid_y - int(s * 0.11)), (int(s * 0.44), mid_y + int(s * 0.11)), (int(s * 0.62), mid_y)]
    d.polygon(tri, fill=WHITE)
    return img.resize((size, size), Image.LANCZOS)


for folder, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    out = RES / f"mipmap-{folder}"
    out.mkdir(parents=True, exist_ok=True)
    mark(px).save(out / "ic_launcher.png")

# Banner: 320x180 at xhdpi (what Fire TV and Android TV launchers expect).
W, H = 320, 180
banner = Image.new("RGBA", (W * SS, H * SS), DARK + (255,))
d = ImageDraw.Draw(banner)
for y in range(H * SS):  # subtle vertical gradient
    t = y / (H * SS)
    c = tuple(int(DARK[i] + (32 - DARK[i]) * t * 0.6) for i in range(3))
    d.line([(0, y), (W * SS, y)], fill=c + (255,))
d.rectangle([0, H * SS - 6 * SS, W * SS, H * SS], fill=BRAND + (255,))
icon = mark(84 * SS)
banner.alpha_composite(icon, (22 * SS, (H * SS - icon.height) // 2 - 4 * SS))
fonts = Path("C:/Windows/Fonts")
bold = ImageFont.truetype(str(fonts / "segoeuib.ttf"), 34 * SS)
regular = ImageFont.truetype(str(fonts / "segoeui.ttf"), 14 * SS)
d.text((122 * SS, 52 * SS), "TV", font=bold, fill=WHITE)
d.text((122 * SS, 88 * SS), "Abierta", font=bold, fill=BRAND_LIGHT)
d.text((124 * SS, 132 * SS), "Canales abiertos del mundo", font=regular, fill=(154, 164, 177))
banner = banner.resize((W, H), Image.LANCZOS).convert("RGB")
(RES / "drawable-xhdpi").mkdir(parents=True, exist_ok=True)
banner.save(RES / "drawable-xhdpi" / "banner.png")
print("ok")
