#!/usr/bin/env python3
"""
Generates R.kt and the string table for the share-card preview, from the real strings.xml.

Generated rather than hand-written so the preview can never drift from the copy that actually
ships: a string renamed in the app breaks this build instead of silently rendering the old text.
"""
import re
import sys
import xml.etree.ElementTree as ET

strings_xml, out_dir = sys.argv[1], sys.argv[2]
root = ET.parse(strings_xml).getroot()

names = [e.get("name") for e in root.findall("string")]
values = {e.get("name"): "".join(e.itertext()) for e in root.findall("string")}

def kotlin_literal(s):
    s = s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
    return '"' + s.replace("\n", "\\n") + '"'

ids = {name: 0x7F010000 + i for i, name in enumerate(names)}

with open(f"{out_dir}/R.kt", "w", encoding="utf-8") as f:
    f.write("package com.pushuprpg.app\n\n")
    f.write("/** Generated from app/src/main/res/values/strings.xml. Do not edit. */\n")
    f.write("object R {\n    object string {\n")
    for name in names:
        f.write(f"        const val {name} = {ids[name]}\n")
    f.write("    }\n}\n")

with open(f"{out_dir}/StringTable.kt", "w", encoding="utf-8") as f:
    f.write("package preview\n\n")
    f.write("/** Generated from app/src/main/res/values/strings.xml. Do not edit. */\n")
    f.write("val STRING_TABLE: Map<Int, String> = mapOf(\n")
    for name in names:
        f.write(f"    {ids[name]} to {kotlin_literal(values[name])},\n")
    f.write(")\n")

print(f"{len(names)} strings")
