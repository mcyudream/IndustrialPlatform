# -*- coding: utf-8 -*-
"""Industrial Platform — community release cover banner.

Clean composition in the classic mod-cover style: dark spotlight background,
one hero cube (the real builder block texture on the sides, a glowing platform
grid on top), blocky 3D title. Regenerate:  python art/make_cover.py
"""
import math
from PIL import Image, ImageDraw, ImageFilter, ImageFont

VERSION = "2.4.4"
TITLE = "工业平台移植版"

W, H = 1920, 1080
FONT_HEI = "C:/Windows/Fonts/simhei.ttf"
FONT_YAHEI = "C:/Windows/Fonts/msyh.ttc"
TEX = "src/main/resources/assets/industrial_platform/textures/block/builder/platform_builder.png"

ACCENT = (94, 200, 255)

# ---------------------------------------------------------------- background
img = Image.new("RGB", (W, H), (23, 23, 26))
px = img.load()
for y in range(H):
    for x in range(0, W, 4):
        # radial spotlight centered above the cube
        dx, dy = (x - W / 2) / (W * 0.62), (y - H * 0.42) / (H * 0.62)
        t = max(0.0, 1.0 - math.sqrt(dx * dx + dy * dy))
        c = int(23 + 34 * t * t)
        for k in range(4):
            if x + k < W:
                px[x + k, y] = (c, c, c + 3)
img = img.convert("RGBA")

# soft light shaft behind the cube
shaft = Image.new("RGBA", (W, H), (0, 0, 0, 0))
sd = ImageDraw.Draw(shaft)
sd.ellipse([W / 2 - 560, H * 0.16, W / 2 + 560, H * 0.92], fill=(210, 218, 228, 26))
shaft = shaft.filter(ImageFilter.GaussianBlur(120))
img = Image.alpha_composite(img, shaft)

# --- ground shadow (under the cube) ----------------------------------------
shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
shd = ImageDraw.Draw(shadow)
shd.ellipse([W // 2 - 430, 900, W // 2 + 430, 1030], fill=(0, 0, 0, 150))
shadow = shadow.filter(ImageFilter.GaussianBlur(34))
img = Image.alpha_composite(img, shadow)


# ---------------------------------------------------------------- cube faces
def face_image(o, u, v, tex):
    """Map `tex` onto the parallelogram o, o+u, o+v (absolute canvas coords)
    by inverse-affine over the whole canvas. Returns RGBA canvas-size image."""
    S = tex.size[0]
    det = u[0] * v[1] - u[1] * v[0]
    # inverse of [[u0, v0], [u1, v1]] is 1/det * [[v1, -v0], [-u1, u0]]
    a, b = v[1] / det, -v[0] / det
    c_, d = -u[1] / det, u[0] / det
    # PIL AFFINE: input = (a*x + b*y + c, d*x + e*y + f), texel = param * S
    coeffs = (a * S, b * S, (a * -o[0] + b * -o[1]) * S,
              c_ * S, d * S, (c_ * -o[0] + d * -o[1]) * S)
    return tex.transform((W, H), Image.AFFINE, coeffs, resample=Image.BILINEAR)


def paste_face(base, face, shade=1.0, tint=None):
    if shade < 1.0 or tint is not None:
        dark = Image.new("RGBA", face.size, (0, 0, 0, int(255 * (1 - shade))))
        face = Image.composite(Image.alpha_composite(face, dark), face, face.split()[3])
        if tint is not None:
            layer = Image.new("RGBA", face.size, tint)
            face = Image.composite(Image.alpha_composite(face, layer), face, face.split()[3])
    return Image.alpha_composite(base, face)


# geometry: classic 2:1 item-render cube
HW, HH, V = 360, 180, 340
TC = (W // 2, 445)                     # top-face center
N = (TC[0], TC[1] - HH)                # back corner
E = (TC[0] + HW, TC[1])
S = (TC[0], TC[1] + HH)
Wc = (TC[0] - HW, TC[1])

# --- textures -----------------------------------------------------------
real = Image.open(TEX).convert("RGBA").resize((512, 512), Image.NEAREST)

# top: dark machine frame + glowing 3x3 platform grid (the builder's preview)
top = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
td = ImageDraw.Draw(top)
td.rectangle([0, 0, 511, 511], fill=(58, 58, 63))          # steel frame
td.rectangle([28, 28, 483, 483], fill=(24, 28, 36))        # recessed screen
cell = 124
gap = 28
x0 = y0 = 60
for i in range(3):
    for j in range(3):
        cx0 = x0 + i * (cell + gap)
        cy0 = y0 + j * (cell + gap)
        edge = (i in (0, 2)) and (j in (0, 2))
        col = (255, 203, 92) if edge else ACCENT          # corners = light blocks
        td.rectangle([cx0, cy0, cx0 + cell, cy0 + cell], fill=tuple(int(c * 0.55) for c in col))
        td.rectangle([cx0 + 16, cy0 + 16, cx0 + cell - 16, cy0 + cell - 16], fill=col)
# frame rivets
for rx, ry in ((40, 40), (472, 40), (40, 472), (472, 472)):
    td.ellipse([rx - 12, ry - 12, rx + 12, ry + 12], fill=(86, 86, 92))

top_mapped = face_image(N, (E[0] - N[0], E[1] - N[1]), (Wc[0] - N[0], Wc[1] - N[1]), top)
img = paste_face(img, top_mapped, shade=1.0)

left_mapped = face_image(Wc, (S[0] - Wc[0], S[1] - Wc[1]), (0, V), real)
img = paste_face(img, left_mapped, shade=0.62)

right_mapped = face_image(S, (E[0] - S[0], E[1] - S[1]), (0, V), real)
img = paste_face(img, right_mapped, shade=0.82)

d = ImageDraw.Draw(img)

# --- edge highlights ------------------------------------------------------
d.line([Wc, S], fill=(255, 255, 255, 60), width=2)         # front vertical edge
d.line([N, Wc], fill=(255, 255, 255, 42), width=2)
d.line([N, E], fill=(255, 255, 255, 30), width=2)
d.line([Wc, S], fill=(255, 255, 255, 40), width=2)
d.line([S, E], fill=(255, 255, 255, 26), width=2)

# --- top glow (screen light spilling up) -----------------------------------
glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
gd = ImageDraw.Draw(glow)
gd.ellipse([TC[0] - 360, TC[1] - 190, TC[0] + 360, TC[1] + 210], fill=(120, 200, 255, 46))
glow = glow.filter(ImageFilter.GaussianBlur(60))
img = Image.alpha_composite(img, glow)
d = ImageDraw.Draw(img)


# ---------------------------------------------------------------- title
def blocky_title(base, text, center_x, y, size):
    fnt = ImageFont.truetype(FONT_HEI, size)
    # measure
    tmp = ImageDraw.Draw(Image.new("RGB", (8, 8)))
    tw = tmp.textlength(text, font=fnt)
    x = int(center_x - tw / 2)

    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    # 3D extrusion
    for i in range(6, 0, -1):
        ld.text((x + i, y + i), text, font=fnt, fill=(10, 10, 12, 235))
    # dark outline
    ld.text((x, y), text, font=fnt, fill=(46, 46, 52, 255), stroke_width=6,
            stroke_fill=(18, 18, 22, 255))
    base = Image.alpha_composite(base, layer)

    # light gradient fill through the glyph mask
    mask = Image.new("L", base.size, 0)
    md = ImageDraw.Draw(mask)
    md.text((x, y), text, font=fnt, fill=255)
    grad = Image.new("RGBA", base.size, (0, 0, 0, 0))
    gpx = grad.load()
    for yy in range(y - 8, y + size + 12):
        t = max(0.0, min(1.0, (yy - y) / float(size)))
        c = int(246 - 84 * t)
        for xx in range(int(x) - 20, int(x + tw) + 20):
            if 0 <= xx < W and 0 <= yy < H:
                gpx[xx, yy] = (c, c, min(255, c + 4), 255)
    base.paste(grad, (0, 0), mask)
    return base


img = blocky_title(img, TITLE, W // 2, 56, 122)

# tracked English subtitle under the Chinese title
d = ImageDraw.Draw(img)  # blocky_title returned a new image — rebind the drawer
f_eng = ImageFont.truetype(FONT_HEI, 40)
tmp = ImageDraw.Draw(Image.new("RGB", (8, 8)))
TRACK = 18
sub = "INDUSTRIAL PLATFORM"
sw = sum(tmp.textlength(ch, font=f_eng) + TRACK for ch in sub) - TRACK
sx = int(W / 2 - sw / 2)
for ch in sub:
    d.text((sx + 2, 202), ch, font=f_eng, fill=(12, 12, 14))
    d.text((sx, 200), ch, font=f_eng, fill=(176, 186, 198))
    sx += tmp.textlength(ch, font=f_eng) + TRACK

# subtle footer
f_small = ImageFont.truetype(FONT_YAHEI, 26)
d = ImageDraw.Draw(img)
foot = "v%s  ·  Minecraft 1.12.2" % VERSION
d.text((W - d.textlength(foot, font=f_small) - 28, H - 52), foot, font=f_small, fill=(122, 122, 130))
credit = "移植自 Qi-Month/IndustrialPlatform"
d.text((28, H - 52), credit, font=f_small, fill=(122, 122, 130))

img.convert("RGB").save("art/cover.png", "PNG")
img.convert("RGB").save("art/cover.jpg", "JPEG", quality=92)
print("saved art/cover.png + art/cover.jpg", img.size)
