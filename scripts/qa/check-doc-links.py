#!/usr/bin/env python3
"""Fail on broken relative links/anchors in docs/**/*.md."""
import os
import re
import sys
import urllib.parse

root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
files = []
for directory, _, names in os.walk(os.path.join(root, "docs")):
    for name in names:
        if name.endswith(".md"):
            files.append(os.path.join(directory, name))


def anchors(path):
    values = set()
    text = open(path, encoding="utf-8").read()
    for match in re.finditer(r'<a id="([^"]+)"></a>', text):
        values.add(match.group(1))
    for match in re.finditer(r"^#{1,6}\s+(.+)$", text, re.M):
        title = match.group(1).strip().lower()
        title = re.sub(r"[^\w一-鿿 \-]", "", title)
        values.add(title.replace(" ", "-"))
    return values


errors = []
links = 0
for path in files:
    text = open(path, encoding="utf-8").read()
    for match in re.finditer(r"(?<!!)\[[^\]]*\]\(([^)]+)\)", text):
        target = match.group(1)
        if target.startswith(("http://", "https://", "mailto:")):
            continue
        links += 1
        relative, _, fragment = target.partition("#")
        if relative:
            resolved = os.path.normpath(os.path.join(
                os.path.dirname(path), urllib.parse.unquote(relative)))
        else:
            resolved = path
        if not os.path.exists(resolved):
            errors.append(f"{os.path.relpath(path, root)}: missing {target}")
            continue
        if fragment and fragment not in anchors(resolved):
            errors.append(f"{os.path.relpath(path, root)}: anchor missing {target}")

print(f"files {len(files)} links {links} errors {len(errors)}")
for error in errors:
    print("ERR", error)
sys.exit(1 if errors else 0)
