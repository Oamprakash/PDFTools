"""Frame raw phone screenshots for Google Play: brand background, caption on top, rounded screenshot with shadow."""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
U = "/mnt/user-data/uploads/"
SHOTS = [  # (file, caption line 1, line 2)
    ("55b79f0a", "Compress PDF to the", "exact KB you need"),
    ("56d73b6b", "Every PDF tool in", "one simple app"),
    ("26598f37", "Pick 100 KB, 200 KB,", "1 MB or any size"),
    ("9633d841", "Merge PDFs in", "the order you want"),
    ("3e1df0be", "Split by pages or", "custom ranges"),
]
W, H = 1080, 1920
BIG = ImageFont.truetype("/usr/share/fonts/opentype/inter/InterDisplay-ExtraBold.otf", 74)

def background():
    bg = Image.new("RGB", (W, H)); d = ImageDraw.Draw(bg)
    top, bot = (0x35, 0x63, 0xF0), (0x24, 0x49, 0xC7)
    for y in range(H):
        t = y / H; d.line([(0, y), (W, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(top, bot)))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0)); g = ImageDraw.Draw(glow)
    return Image.alpha_composite(bg.convert("RGBA"), glow)

for i, (f, l1, l2) in enumerate(SHOTS, 1):
    img = background(); d = ImageDraw.Draw(img)
    for j, line in enumerate((l1, l2)):
        w = d.textlength(line, font=BIG); d.text(((W - w) / 2, 120 + j * 92), line, font=BIG, fill="white")
    shot = Image.open(f"{U}{f}-image.png").convert("RGB")
    shot = shot.crop((0, 46, 720, 1508))                          # drop status bar and the system nav bar, keep the app's buttons
    sh = H - 360 - 70; sw = int(shot.width * sh / shot.height); shot = shot.resize((sw, sh), Image.LANCZOS)
    x, y = (W - sw) // 2, 360
    mask = Image.new("L", (sw, sh), 0); ImageDraw.Draw(mask).rounded_rectangle((0, 0, sw - 1, sh - 1), 44, fill=255)
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((x, y + 18, x + sw, y + sh + 18), 44, fill=(10, 28, 92, 120))
    img = Image.alpha_composite(img, shadow.filter(ImageFilter.GaussianBlur(28)))
    border = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(border).rounded_rectangle((x - 10, y - 10, x + sw + 10, y + sh + 10), 52, fill=(255, 255, 255, 60))
    img = Image.alpha_composite(img, border)
    img.paste(shot, (x, y), mask)
    img.convert("RGB").save(f"pdfzy_screenshot_{i}.png")
print("done")
