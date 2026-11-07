"""
產生朵拉花店示範用商品圖（800x800 WebP）
用法：python tools/generate_images.py
輸出到 src/main/resources/static/images/products/
正式上線時請換成實拍照片，檔名規則不變即可。
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

SIZE = 800
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/static/images/products"

# 商品代碼 → (花瓣色, 花心色, 花瓣數, 背景色上, 背景色下, 細節圖張數)
# FLOWER-PEONY-PINK 刻意不產生，用來展示 placeholder 容錯
PRODUCTS = {
    "FLOWER-ROSE-RED": ((200, 30, 60), (130, 10, 40), 7, (255, 236, 240), (250, 214, 222), 2),
    "FLOWER-TULIP-PINK": ((245, 140, 175), (220, 90, 130), 4, (255, 243, 247), (248, 222, 232), 1),
    "FLOWER-SUNFLOWER": ((250, 196, 30), (110, 70, 30), 16, (255, 250, 230), (250, 235, 190), 1),
    "FLOWER-LILY-WHITE": ((252, 252, 250), (240, 200, 80), 6, (236, 244, 238), (214, 232, 220), 1),
    "FLOWER-CARNATION": ((236, 90, 120), (200, 60, 95), 12, (255, 240, 236), (250, 220, 214), 1),
    "FLOWER-HYDRANGEA-BLUE": ((120, 150, 230), (90, 110, 200), 4, (236, 242, 255), (214, 225, 250), 1),
    "FLOWER-ORCHID": ((235, 120, 200), (250, 230, 120), 5, (248, 240, 250), (232, 220, 240), 1),
    "FLOWER-BABYSBREATH": ((255, 255, 255), (245, 245, 235), 5, (240, 238, 248), (222, 218, 238), 1),
}


def gradient(top, bottom):
    """垂直漸層背景"""
    img = Image.new("RGB", (SIZE, SIZE), top)
    d = ImageDraw.Draw(img)
    for y in range(SIZE):
        t = y / SIZE
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        d.line([(0, y), (SIZE, y)], fill=c)
    return img


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def draw_flower(layer, cx, cy, r, petal, center, n, rot=0.0):
    """以多片橢圓花瓣圍成一朵花"""
    d = ImageDraw.Draw(layer)
    for ring, scale in ((0, 1.0), (1, 0.62)):
        for i in range(n):
            a = rot + (i + ring * 0.5) * 2 * math.pi / n
            pr = r * scale
            px = cx + math.cos(a) * pr * 0.55
            py = cy + math.sin(a) * pr * 0.55
            col = shade(petal, 0.92 if ring == 0 else 1.05)
            d.ellipse([px - pr * 0.5, py - pr * 0.5, px + pr * 0.5, py + pr * 0.5],
                      fill=col + (255,), outline=shade(petal, 0.8) + (255,))
    d.ellipse([cx - r * 0.22, cy - r * 0.22, cx + r * 0.22, cy + r * 0.22], fill=center + (255,))


def bouquet(code, spec, seed, zoom=1.0, offset=(0, 0)):
    petal, center, n, bg_top, bg_bottom, _ = spec
    rnd = random.Random(seed)
    img = gradient(bg_top, bg_bottom).convert("RGBA")
    layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    ox, oy = offset

    def p(x, y):
        return (SIZE / 2 + (x - SIZE / 2) * zoom + ox, SIZE / 2 + (y - SIZE / 2) * zoom + oy)

    # 花莖
    for i in range(7):
        x0 = 400 + rnd.randint(-120, 120)
        d.line([p(x0, 330), p(400 + rnd.randint(-20, 20), 690)], fill=(80, 130, 80, 255), width=int(8 * zoom))
    # 葉子
    for _ in range(6):
        x, y = 400 + rnd.randint(-170, 170), 300 + rnd.randint(-120, 120)
        w = 70 * zoom
        cx, cy = p(x, y)
        d.ellipse([cx - w, cy - w * 0.35, cx + w, cy + w * 0.35], fill=(95, 150, 100, 255))
    # 包裝紙（牛皮紙色三角形）
    d.polygon([p(230, 430), p(570, 430), p(400, 740)], fill=(196, 160, 120, 255), outline=(160, 120, 85, 255))
    d.polygon([p(300, 430), p(500, 430), p(400, 600)], fill=(214, 182, 140, 255))
    # 緞帶
    bx, by = p(400, 600)
    d.ellipse([bx - 30 * zoom, by - 18 * zoom, bx + 30 * zoom, by + 18 * zoom], fill=(194, 24, 91, 255))

    # 花朵
    count = 9 if code != "FLOWER-BABYSBREATH" else 40
    base_r = 75 if code != "FLOWER-BABYSBREATH" else 22
    for _ in range(count):
        x, y = 400 + rnd.randint(-150, 150), 300 + rnd.randint(-130, 110)
        cx, cy = p(x, y)
        r = base_r * zoom * rnd.uniform(0.8, 1.15)
        draw_flower(layer, cx, cy, r, petal, center, n, rnd.random() * math.pi)

    layer = layer.filter(ImageFilter.SMOOTH)
    img.alpha_composite(layer)
    return img.convert("RGB")


def placeholder():
    """預設圖：淡灰粉底 + 線條花朵"""
    img = gradient((246, 240, 242), (234, 224, 228))
    d = ImageDraw.Draw(img)
    c = (200, 180, 188)
    cx, cy, r = 400, 360, 120
    for i in range(6):
        a = i * math.pi / 3
        px, py = cx + math.cos(a) * r * 0.7, cy + math.sin(a) * r * 0.7
        d.ellipse([px - 70, py - 70, px + 70, py + 70], outline=c, width=6)
    d.ellipse([cx - 35, cy - 35, cx + 35, cy + 35], outline=c, width=6)
    d.line([(400, 480), (400, 640)], fill=c, width=6)
    d.ellipse([400, 540, 500, 580], outline=c, width=6)
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    placeholder().save(OUT / "placeholder_flower.webp", "WEBP", quality=85)
    for idx, (code, spec) in enumerate(PRODUCTS.items()):
        folder = OUT / code
        folder.mkdir(exist_ok=True)
        bouquet(code, spec, seed=idx).save(folder / "main.webp", "WEBP", quality=85)
        # 細節圖：放大花朵區域
        for k in range(1, spec[5] + 1):
            detail = bouquet(code, spec, seed=idx, zoom=1.8 + 0.3 * k, offset=(0, 180 + 40 * k))
            detail.save(folder / f"detail_{k}.webp", "WEBP", quality=85)
        print("已產生", code)
    # 網站圖示 favicon.ico：用紅玫瑰配色畫一朵花
    icon = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    draw_flower(icon, 128, 128, 200, (200, 30, 60), (130, 10, 40), 6)
    icon.save(OUT.parent.parent / "favicon.ico", sizes=[(16, 16), (32, 32), (48, 48), (64, 64)])
    print("輸出目錄：", OUT)


if __name__ == "__main__":
    main()
