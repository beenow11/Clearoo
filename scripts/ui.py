#!/usr/bin/env python3
"""Tiny adb + uiautomator driver for the smoke test.

  ui.py tap TEXT       tap the first element whose text/description contains TEXT
  ui.py tapx TEXT      tap the element whose text is exactly TEXT (e.g. a dialog button)
  ui.py expect TEXT    wait until TEXT is on screen
  ui.py shot NAME      save a screenshot and print the visible text
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

OUT = "smoke"


def adb(*args, check=True):
    return subprocess.run(["adb", *args], check=check, capture_output=True).stdout


def nodes():
    for _ in range(3):
        adb("shell", "rm", "-f", "/sdcard/ui.xml", check=False)
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
        raw = adb("shell", "cat", "/sdcard/ui.xml", check=False)
        try:
            return list(ET.fromstring(raw).iter("node"))
        except ET.ParseError:
            time.sleep(1)
    return []


def norm(s):
    # Emoji variation selectors differ between sources; ignore them when matching.
    return s.replace("\ufe0f", "")


def label(n):
    return norm((n.get("text") or "") + " " + (n.get("content-desc") or ""))


def visible_text(ns):
    return [label(n).strip() for n in ns if label(n).strip()]


def dismiss_system_anr(ns):
    """The slow emulator's own apps (launcher, System UI) sometimes freeze and show an
    "isn't responding" dialog over everything. Wait it out; Clearoo's own ANRs still fail."""
    texts = visible_text(ns)
    if any("isn't responding" in t and "Clearoo" not in t for t in texts):
        for n in ns:
            if label(n).strip() == "Wait":
                x, y = center(n)
                adb("shell", "input", "tap", str(x), str(y))
                print(f"(dismissed system dialog: {texts[0]})")
                time.sleep(2)
                return True
    return False


def find(text, timeout=None, exact=False):
    timeout = timeout or int(os.environ.get("UI_TIMEOUT", "20"))
    end = time.time() + timeout
    while time.time() < end:
        ns = nodes()
        if dismiss_system_anr(ns):
            end += 5
            continue
        for n in ns:
            hit = label(n).strip().lower() == norm(text).lower() if exact else norm(text) in label(n)
            if hit:
                return n, ns
        time.sleep(1)
    return None, nodes()


def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def fail(msg, ns):
    print(f"FAIL: {msg}\nOn screen: {visible_text(ns)}")
    shot("failure")
    sys.exit(1)


def shot(name):
    with open(f"{OUT}/{name}.png", "wb") as f:
        f.write(adb("exec-out", "screencap", "-p"))
    print(f"[{name}] {visible_text(nodes())}")


def cards():
    """Where each media card is on screen (the last one listed is the top card)."""
    for n in nodes():
        d = n.get("content-desc") or ""
        if d.lower().endswith((".jpg", ".png", ".mp4")):
            print(f"  card {d} bounds={n.get('bounds')}")


if __name__ == "__main__":
    cmd, arg = sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else ""
    if cmd == "shot":
        shot(arg)
    elif cmd == "cards":
        cards()
    else:
        n, ns = find(arg, exact=(cmd == "tapx"))
        if n is None:
            fail(f"'{arg}' not found", ns)
        if cmd in ("tap", "tapx"):
            x, y = center(n)
            adb("shell", "input", "tap", str(x), str(y))
            time.sleep(1.2)
        print(f"ok: {cmd} '{arg}'")
