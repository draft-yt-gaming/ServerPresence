"""Server Presence logo: a Minecraft grass block on a blurple tile, with an "online" status dot. 32x32 pixel art."""
import random
import sys
from PIL import Image, ImageDraw

N = 32
img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
px = img.load()

# rounded tile, vertical gradient
top, bottom = (0x72, 0x7E, 0xF7), (0x3F, 0x49, 0xC0)
radius = 6
def inside(x, y, r=radius, x0=0, y0=0, x1=N - 1, y1=N - 1):
    cx = min(max(x, x0 + r), x1 - r)
    cy = min(max(y, y0 + r), y1 - r)
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r + 2
for y in range(N):
    t = y / (N - 1)
    color = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
    for x in range(N):
        if inside(x, y):
            px[x, y] = color + (255,)
# light top edge / dark bottom edge (only on the flat parts and the start of the curves)
for x in range(N):
    for y in range(N):
        if px[x, y][3] and not inside(x, y - 1) and y <= 2:
            px[x, y] = (0x9A, 0xA3, 0xFF, 255)
        elif px[x, y][3] and not inside(x, y + 1) and y >= N - 3:
            px[x, y] = (0x2F, 0x37, 0x99, 255)

# grass block, 16x16 texture with a 1px outline
rng = random.Random(7)
bx, by, size = 6, 5, 18
grass = [(0x5D, 0xA1, 0x30), (0x6C, 0xB8, 0x3A), (0x4E, 0x8A, 0x27), (0x79, 0xC6, 0x45)]
dirt = [(0x86, 0x5A, 0x3A), (0x96, 0x6C, 0x4A), (0x6F, 0x4A, 0x2E), (0x7A, 0x52, 0x35)]
outline = (0x1A, 0x1C, 0x4A, 255)
for y in range(size):
    for x in range(size):
        X, Y = bx + x, by + y
        if x in (0, size - 1) or y in (0, size - 1):
            px[X, Y] = outline
            continue
        ty = y - 1
        # grass on top, with drips into the dirt
        drip = {1: 6, 2: 5, 4: 7, 6: 5, 9: 6, 11: 7, 13: 5, 14: 6}.get(x, 4)
        palette = grass if ty < drip else dirt
        color = palette[rng.randrange(len(palette))]
        if ty == 0:
            color = (0x8E, 0xD6, 0x55)  # lit top row
        if x == size - 2 or ty == size - 3:
            color = tuple(int(c * 0.78) for c in color)  # shaded right/bottom
        px[X, Y] = color + (255,)

# "online" status dot in the bottom right corner, cut out of the tile like Discord avatars
cx, cy = 24, 24
gap = [(x, y) for x in range(N) for y in range(N) if (x - cx) ** 2 + (y - cy) ** 2 <= 6.5 ** 2]
for x, y in gap:
    # the gap takes the colour of the tile behind it
    t = y / (N - 1)
    px[x, y] = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,)
for x in range(N):
    for y in range(N):
        d = (x - cx) ** 2 + (y - cy) ** 2
        if d <= 4.5 ** 2:
            px[x, y] = (0x23, 0xA5, 0x5A, 255)
        if (x - cx, y - cy) in ((-2, -3), (-3, -2), (-2, -2)):
            px[x, y] = (0x5F, 0xD9, 0x8E, 255)

out = sys.argv[1]
img.save(f"{out}/logo-32.png")
for s in (128, 400, 512):
    img.resize((s, s), Image.NEAREST).save(f"{out}/logo-{s}.png")
print("ok")
