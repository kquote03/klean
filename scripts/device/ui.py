#!/usr/bin/env python3
"""ui.py dump | tap "<text>" [index] | has "<text>" | taptag <resource-id or content-desc>. Waydroid via adb -s $DEV."""
import os, re, subprocess, sys, time
import xml.etree.ElementTree as ET
DEV = os.environ.get("DEV", "192.168.240.112:5555")
def adb(*a, **kw):
    return subprocess.run(["adb", "-s", DEV, *a], capture_output=True, text=True, **kw).stdout
def dump():
    for _ in range(3):
        adb("shell", "uiautomator dump /sdcard/ui.xml >/dev/null 2>&1")
        x = adb("shell", "cat /sdcard/ui.xml")
        i = x.find("<?xml")
        if i >= 0:
            try: return ET.fromstring(x[i:])
            except ET.ParseError: pass
        time.sleep(1)
    raise SystemExit("no dump")
def nodes(root):
    for n in root.iter("node"):
        yield n
def center(n):
    b = re.findall(r"\d+", n.get("bounds"))
    x1, y1, x2, y2 = map(int, b)
    return (x1 + x2) // 2, (y1 + y2) // 2
def match(n, t):
    return t == (n.get("text") or "") or t == (n.get("content-desc") or "")
cmd = sys.argv[1]
if cmd == "dump":
    for n in nodes(dump()):
        t = n.get("text") or ""; d = n.get("content-desc") or ""
        if t or d: print(f"{n.get('bounds')} text={t!r} desc={d!r} click={n.get('clickable')}")
elif cmd in ("tap", "has", "tapsub"):
    t = sys.argv[2]; idx = int(sys.argv[3]) if len(sys.argv) > 3 else -1
    ms = [n for n in nodes(dump()) if (match(n, t) if cmd != "tapsub" else t in ((n.get("text") or "") + (n.get("content-desc") or "")))]
    if cmd == "has":
        print("yes" if ms else "no"); sys.exit(0 if ms else 1)
    if not ms: print("not found:", t); sys.exit(1)
    x, y = center(ms[idx]); adb("shell", "input", "tap", str(x), str(y)); print("tapped", t, x, y)
