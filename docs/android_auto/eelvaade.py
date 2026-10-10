"""Android Auto eelvaade: CarDrawing.kt geomeetria Pythonis (Pillow), näidisandmetega."""
import math
import random
import sys
from PIL import Image, ImageDraw, ImageFont

OUT = sys.argv[1]
W, H = 1600, 720
SS = 2  # supersampling

LIGHT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-ExtraLight.ttf"
REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


def font(size, light=True):
    try:
        return ImageFont.truetype(LIGHT if light else REG, max(1, int(size * SS)))
    except OSError:
        return ImageFont.truetype(REG, max(1, int(size * SS)))


def col(argb):
    return ((argb >> 16) & 255, (argb >> 8) & 255, argb & 255)


class Drawing:
    def __init__(self, dark):
        self.bg = col(0x0B0E11 if dark else 0xF4F6F7)
        self.surface = col(0x14181C if dark else 0xFFFFFF)
        self.tp = col(0xF2F4F5 if dark else 0x15191D)
        self.ts = col(0x9AA4AD if dark else 0x5A646D)
        self.off = col(0x2A3036 if dark else 0xDDE2E6)
        self.accent = col(0x2FD1C5)
        self.warning = col(0xFFB547)
        self.cold = col(0x3D6FD8)
        self.regen = col(0x7CD957)
        self.img = Image.new("RGB", (W * SS, H * SS), self.bg)
        self.d = ImageDraw.Draw(self.img)

    # --- primitiivid (koordinaadid loogilistes pikslites) ---
    def rrect(self, l, t, r, b, rad, fill=None, outline=None, width=1):
        self.d.rounded_rectangle([l * SS, t * SS, r * SS, b * SS], radius=rad * SS, fill=fill, outline=outline,
                                 width=int(width * SS))

    def text(self, s, x, y, size, color, align="l"):
        self.d.text((x * SS, y * SS), s, font=font(size), fill=color, anchor=align + "s")

    def measure(self, s, size):
        return self.d.textlength(s, font=font(size)) / SS

    def save(self, path):
        self.img.resize((W, H), Image.LANCZOS).save(path)

    # --- abifunktsioonid nagu Kotlinis ---
    def label(self, s, x, y, size, color=None):
        self.text(s, x, y, size, color or self.ts)

    def big(self, value, unit, x, base, size):
        self.text(value, x, base, size, self.tp)
        w = self.measure(value, size)
        self.text(unit, x + w, base, size * 0.35, self.ts)

    def pixel_gauge(self, l, t, r, b, frac):
        cols, rows = 10, 5
        cell = (r - l) / cols
        lit = int(max(0, min(1, frac)) * cols * rows)
        for row in range(rows):
            for c in range(cols):
                idx = row * cols + c
                x = l + c * cell
                y = t + (rows - 1 - row) * cell
                self.rrect(x + cell * .09, y + cell * .09, x + cell * .91, y + cell * .91, cell * .1,
                           fill=self.accent if idx < lit else self.off)

    def tile_grid(self, l, t, r, b, tiles, columns):
        rows = (len(tiles) + columns - 1) // columns
        gap = (b - t) * 0.03
        w = ((r - l) - gap * (columns - 1)) / columns
        h = ((b - t) - gap * (rows - 1)) / rows
        for i, (lab, val, unit, vcol) in enumerate(tiles):
            x = l + (i % columns) * (w + gap)
            y = t + (i // columns) * (h + gap)
            self.rrect(x, y, x + w, y + h, h * .12, fill=self.surface)
            small = min(h * .2, w * .09)
            ls = self.fit(lab.upper(), small, w * .88)
            self.text(lab.upper(), x + w * .06, y + h * .3, ls, self.ts)
            vs = self.fit(val, min(h * .42, w * .2), w * .7)
            self.text(val, x + w * .06, y + h * .82, vs, vcol or self.tp)
            vw = self.measure(val, vs)
            self.text(" " + unit, x + w * .06 + vw, y + h * .82, small, self.ts)

    def fit(self, s, size, max_w):
        w = self.measure(s, size)
        return size * max_w / w if w > max_w else size

    def scale(self, t):
        def blend(a, b, k):
            return tuple(int(a[i] + (b[i] - a[i]) * k) for i in range(3))
        t = max(0, min(1, t))
        return blend(self.cold, self.accent, t * 2) if t < .5 else blend(self.accent, self.warning, (t - .5) * 2)


def area():
    pad = H * 0.05
    return pad, pad, W - pad, H - pad


def overview(dr):
    l, t, r, b = area()
    h = b - t
    left_w = (r - l) * .38
    dr.label("LAETUS", l, t + h * .08, h * .045)
    dr.big("78", " %", l, t + h * .38, h * .30)
    dr.pixel_gauge(l, t + h * .48, l + left_w * .92, t + h * .48 + left_w * .92 / 2, .78)
    tiles = [
        ("Aku võimsus", "-12,4", "kW", dr.regen),
        ("Kiirus (mootori pööretest)", "54", "km/h", None),
        ("Aku temperatuur", "14…16", "°C", None),
        ("12 V aku", "14,8", "V", None),
        ("Päikese võimsus", "38", "W", None),
        ("Läbisõit", "23 456", "km", None),
    ]
    dr.tile_grid(l + left_w, t, r, b - h * .06, tiles, 2)


def battery(dr):
    l, t, r, b = area()
    h = b - t
    grid_w = (r - l) * .62
    random.seed(4)
    cells = [round(3.79 + random.random() * 0.03, 2) for _ in range(180)]
    cells[57] = 3.76
    cols = 18
    rows = 10
    cell = min(grid_w / cols, h * .9 / rows)
    mn, mx = min(cells), max(cells)
    for i, v in enumerate(cells):
        k = (v - mn) / (mx - mn) if mx > mn else .5
        x = l + (i % cols) * cell
        y = t + (i // cols) * cell
        dr.rrect(x + cell * .08, y + cell * .08, x + cell * .92, y + cell * .92, cell * .12, fill=dr.scale(k))
    dr.label(f"180 · {mn:.2f}…{mx:.2f} V".replace(".", ","), l, t + rows * cell + h * .07, h * .045)
    dr.tile_grid(l + grid_w + (r - l) * .03, t, r, b - h * .06, [
        ("Laetus (BMS)", "76,5", "%", None),
        ("Tervis (SOH)", "100,0", "%", None),
        ("Elementide erinevus", "60", "mV", None),
        ("Aku temperatuur", "14…16", "°C", None),
    ], 1)


def wheels(dr):
    l, t, r, b = area()
    h = b - t
    car_w, car_h = h * .42, h * .9
    cx = l + (r - l) * .32
    body = (cx - car_w / 2, t + (h - car_h) / 2, cx + car_w / 2, t + (h + car_h) / 2)
    dr.rrect(*body, car_w * .3, outline=dr.ts, width=h * .008)
    front_y = body[1] + car_h * .2
    rear_y = body[1] + car_h * .8
    ww, wh = h * .07, h * .15
    steering = 120.0  # positiivne = vasakule
    wheels_ = [  # (front, left, bar, temp)
        (True, True, 2.90, 18), (True, False, 2.88, 18),
        (False, True, 2.94, 17), (False, False, 2.10, 16),
    ]
    for front, left, bar, temp in wheels_:
        x = body[0] if left else body[2]
        y = front_y if front else rear_y
        color = dr.warning if bar < 2.2 else dr.accent
        # Canvas.rotate(deg) pöörab päripäeva; ekraanikoordinaatides (y alla) sama valem
        ang = math.radians(-(steering / 14)) if front else 0.0
        pts = []
        for px, py in [(-ww / 2, -wh / 2), (ww / 2, -wh / 2), (ww / 2, wh / 2), (-ww / 2, wh / 2)]:
            rx = px * math.cos(ang) - py * math.sin(ang)
            ry = px * math.sin(ang) + py * math.cos(ang)
            pts.append(((x + rx) * SS, (y + ry) * SS))
        dr.d.polygon(pts, fill=color)
        tx = x - ww * .8 if left else x + ww * .8
        al = "r" if left else "l"
        dr.text(f"{bar:.2f} bar".replace(".", ","), tx, y, h * .055, dr.tp, al)
        dr.text(f"{temp} °C", tx, y + h * .06, h * .045, dr.ts, al)
    right = l + (r - l) * .62
    dr.tile_grid(right, t, r, b - h * .06, [
        ("Roolinurk", "120° ←", "", None),
        ("Kiirus (mootori pööretest)", "8", "km/h", None),
        ("Ees rpm", "0", "", None),
        ("Taga rpm", "580", "", None),
    ], 1)


def charging(dr):
    l, t, r, b = area()
    h = b - t
    left_w = (r - l) * .45
    dr.label("LAEB", l, t + h * .08, h * .045, dr.accent)
    dr.big("10,8", " kW", l, t + h * .38, h * .28)
    dr.pixel_gauge(l, t + h * .48, l + left_w * .9, t + h * .48 + left_w * .9 / 2, .64)
    dr.tile_grid(l + left_w, t, r, b - h * .06, [
        ("Laetus", "64,0", "%", None),
        ("Aeg 80 %-ni", "1:07", "h:min", None),
        ("AC sisendvõimsus", "11,2", "kW", None),
        ("Aku temperatuur", "18…21", "°C", None),
    ], 2)


PAGES = {"ulevaade": overview, "aku": battery, "rattad": wheels, "laadimine": charging}

for name, fn in PAGES.items():
    for dark in (True, False):
        dr = Drawing(dark)
        fn(dr)
        dr.save(f"{OUT}/{name}_{'oo' if dark else 'paev'}.png")
print("ok")
