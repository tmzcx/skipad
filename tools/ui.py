"""设备小工具：抓节点树 / 截图 / 过滤日志。

用法（在 skipad 目录下）：
    python tools/ui.py tree            # 打印当前界面节点（含 text/id/bounds）
    python tools/ui.py tap 跳过        # 按文字找节点并点击中心
    python tools/ui.py shot out.png    # 截图
    python tools/ui.py log 60          # 最近 60 秒的 SkipEngine/SkipService 日志
"""
import os
import re
import subprocess
import sys
import time

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ADB = os.path.join(ROOT, ".android-sdk", "platform-tools", "adb.exe")
TMP = os.path.join(ROOT, ".tmp")
os.makedirs(TMP, exist_ok=True)
os.environ["TMP"] = TMP
os.environ["TEMP"] = TMP


def adb(*args, capture=True):
    cmd = [ADB] + list(args)
    r = subprocess.run(
        cmd,
        capture_output=capture,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    return r.stdout or ""


def dump_tree():
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        out = adb("exec-out", "cat", "/sdcard/ui.xml")
        if "<hierarchy" in out:
            return out
        time.sleep(0.5)
    return ""


def parse_nodes(xml):
    nodes = []
    for m in re.finditer(r"<node\b[^>]*>", xml):
        tag = m.group(0)
        attrs = dict(re.findall(r'(\w[\w-]*)="([^"]*)"', tag))
        b = attrs.get("bounds", "")
        mb = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", b)
        if not mb:
            continue
        rect = tuple(int(x) for x in mb.groups())
        nodes.append({
            "text": attrs.get("text", ""),
            "desc": attrs.get("content-desc", ""),
            "id": attrs.get("resource-id", ""),
            "cls": attrs.get("class", "").split(".")[-1],
            "clickable": attrs.get("clickable") == "true",
            "rect": rect,
        })
    return nodes


def cmd_tree():
    xml = dump_tree()
    if not xml:
        print("抓取失败")
        return 1
    nodes = parse_nodes(xml)
    print(f"{len(nodes)} nodes")
    for n in nodes:
        label = n["text"] or n["desc"]
        if not label and not n["id"]:
            continue
        l, t, r, b = n["rect"]
        print(
            f"[{l:4d},{t:4d}][{r:4d},{b:4d}] {n['cls']:14s}"
            f" click={int(n['clickable'])}"
            f" id={n['id'] or '-'}"
            f" text={label!r}"
        )
    return 0


def cmd_tap(word):
    nodes = parse_nodes(dump_tree())
    for n in nodes:
        if word in (n["text"] or n["desc"]):
            l, t, r, b = n["rect"]
            x, y = (l + r) // 2, (t + b) // 2
            adb("shell", "input", "tap", str(x), str(y))
            print(f"tap {word!r} at {x},{y}")
            return 0
    print(f"没找到 {word!r}")
    return 1


def cmd_shot(path):
    out = adb("exec-out", "screencap", "-p")
    data = out.encode("latin-1", errors="ignore")
    with open(path, "wb") as f:
        f.write(data)
    print("saved", path, len(data), "bytes")
    return 0


def cmd_log(seconds):
    secs = int(seconds) if seconds else 60
    out = adb("logcat", "-d", "-t", "2000", "-s", "SkipEngine:V", "SkipService:V", "AndroidRuntime:E")
    lines = out.strip().splitlines()[-80:]
    print("\n".join(lines))
    return 0


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    cmd = sys.argv[1]
    if cmd == "tree":
        return cmd_tree()
    if cmd == "tap":
        return cmd_tap(sys.argv[2])
    if cmd == "shot":
        return cmd_shot(sys.argv[2] if len(sys.argv) > 2 else os.path.join(TMP, "shot.png"))
    if cmd == "log":
        return cmd_log(sys.argv[2] if len(sys.argv) > 2 else "60")
    print(__doc__)
    return 1


if __name__ == "__main__":
    sys.exit(main())
