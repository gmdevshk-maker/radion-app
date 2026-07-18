"""Pretendard 폰트를 앱에 필요한 글자만 남기고 서브셋한다.

원본(design/font-src/)은 전체 한글 11,172자를 담아 1.5MB/weight라 APK가 불필요하게 커진다.
KS X 1001 상용 한글 2,350자 + 앱이 실제로 쓰는 글자만 남기면 1/10 이하로 줄어든다.

채널을 추가해 새 한글이 들어오면 다시 실행할 것:
    python design/subset_fonts.py
"""

import glob
import os

from fontTools import subset

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIR = os.path.join(ROOT, "design", "font-src")
FONT_DIR = os.path.join(ROOT, "app", "src", "main", "res", "font")

# 라틴/숫자/기호 + 한글 자모 + 문장부호 (한글 완성형은 --text로 따로 지정)
UNICODES = (
    "U+0020-007E,U+00A0-00FF,U+2000-206F,U+20A0-20BF,U+2100-2139,"
    "U+2190-21FF,U+25A0-25FF,U+3000-303F,U+3130-318F"
)


def ksx1001_hangul() -> set:
    """KS X 1001 상용 한글 2,350자. iso2022_kr 코덱이 정확히 이 집합만 허용한다."""
    result = set()
    for cp in range(0xAC00, 0xD7A4):
        ch = chr(cp)
        try:
            ch.encode("iso2022_kr")
            result.add(ch)
        except UnicodeEncodeError:
            pass
    return result


def chars_used_by_app() -> set:
    """상용 한글 밖의 글자를 쓰더라도 깨지지 않도록 앱 리소스/소스의 글자를 모두 수집."""
    result = set()
    targets = [os.path.join(ROOT, "app", "src", "main", "assets", "channels.json")]
    targets += glob.glob(os.path.join(ROOT, "app", "src", "main", "res", "values", "*.xml"))
    for base, _, files in os.walk(os.path.join(ROOT, "app", "src", "main", "java")):
        targets += [os.path.join(base, f) for f in files if f.endswith(".kt")]
    for path in targets:
        with open(path, encoding="utf-8") as f:
            result |= set(f.read())
    return {c for c in result if 0xAC00 <= ord(c) <= 0xD7A3}


def main() -> None:
    text = "".join(sorted(ksx1001_hangul() | chars_used_by_app()))
    total_before = total_after = 0
    for src in sorted(glob.glob(os.path.join(SRC_DIR, "pretendard_*.otf"))):
        out = os.path.join(FONT_DIR, os.path.basename(src))
        before = os.path.getsize(src)
        subset.main([src, "--unicodes=" + UNICODES, "--text=" + text,
                     "--layout-features=*", "--output-file=" + out])
        after = os.path.getsize(out)
        total_before += before
        total_after += after
        print("  %-26s %6.2f MB -> %5.0f KB" % (os.path.basename(src), before / 1048576, after / 1024))
    print("\n  합계 %.2f MB -> %.2f MB" % (total_before / 1048576, total_after / 1048576))


if __name__ == "__main__":
    main()
