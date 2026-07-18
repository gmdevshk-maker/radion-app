"""radion-icon.svg의 라디오 그래픽만 어댑티브 전경 PNG(5밀도)로 생성.
배경 그라데이션은 벡터 드로어블로 별도 처리하므로 여기선 그래픽만(투명 배경).
좌표는 SVG(512 뷰포트) 기준을 어댑티브 108단위 캔버스의 안전영역에 맞춰 스케일."""
from PIL import Image, ImageDraw

AMBER = (255, 180, 84, 255)      # #ffb454
AMBER_GRILL = (255, 180, 84, 166)  # opacity 0.65
RED = (232, 80, 58, 255)         # #e8503a
GREY = (86, 90, 100, 255)        # #565a64

# SVG 그래픽 bbox(스트로크 포함): x[122.5,389.5] y[60,398], center(256,229)
CX, CY = 256.0, 229.0
S_UNITS = 68.0 / 338.0  # 그래픽 높이 338 → 안전영역 내 68단위

SS = 4  # 슈퍼샘플링 배율
DENSITIES = {
    "mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432,
}
OUT = "app/src/main/res/mipmap-{}/ic_launcher_foreground.png"


def render(size):
    canvas = size * SS
    u2p = canvas / 108.0          # 단위→픽셀
    s = S_UNITS * u2p             # SVG px → 픽셀
    img = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def T(x, y):
        ux = 54.0 + (x - CX) * S_UNITS
        uy = 54.0 + (y - CY) * S_UNITS
        return ux * u2p, uy * u2p

    def SW(w):
        return max(1, round(w * s))

    def R(r):
        return r * s

    def circle(cx, cy, r, **kw):
        px, py = T(cx, cy)
        rr = R(r)
        d.ellipse([px - rr, py - rr, px + rr, py + rr], **kw)

    def rrect(x0, y0, x1, y1, rad, **kw):
        p0 = T(x0, y0)
        p1 = T(x1, y1)
        d.rounded_rectangle([p0[0], p0[1], p1[0], p1[1]], radius=R(rad), **kw)

    # 안테나 (라운드 캡: 양 끝에 원 추가)
    a0 = T(256, 154)
    a1 = T(330, 74)
    d.line([a0, a1], fill=AMBER, width=SW(11))
    cap = SW(11) / 2.0
    for (px, py) in (a0, a1):
        d.ellipse([px - cap, py - cap, px + cap, py + cap], fill=AMBER)
    # 안테나 팁
    circle(330, 74, 14, fill=RED)
    # 라디오 본체 (아웃라인)
    rrect(128, 154, 384, 344, 36, outline=AMBER, width=SW(11))
    # 다이얼
    circle(200, 248, 33, outline=AMBER, width=SW(11))
    circle(200, 248, 11, fill=AMBER)
    # 스피커 그릴
    rrect(272, 222, 338, 237, 7, fill=AMBER_GRILL)
    rrect(272, 256, 338, 271, 7, fill=AMBER_GRILL)
    # 받침대
    rrect(164, 344, 348, 394, 14, outline=GREY, width=SW(8))

    return img.resize((size, size), Image.LANCZOS)


for name, size in DENSITIES.items():
    render(size).save(OUT.format(name))
    print("생성:", OUT.format(name), f"({size}px)")
