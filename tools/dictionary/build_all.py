#!/usr/bin/env python3
"""Builds every reader dictionary and the manifest the app downloads them by.

Usage:
    build_all.py <work dir> <release base url> [--version N] [--only en,fr]

For each language below this fetches kaikki.org's Wiktextract file into <work dir>/kaikki, builds
<work dir>/out/dict-<lang>-v<N>.sqlite.gz with build_dictionary.py, and finally writes
<work dir>/out/dictionaries.json listing every pack with its size and checksum. Upload the .gz
files and dictionaries.json to the GitHub release at <release base url> (the app reads
<release base url>/dictionaries.json, see Dictionaries.MANIFEST_URL).
"""

import argparse
import json
import os
import subprocess
import sys
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_dictionary  # noqa: E402

# App language code -> kaikki.org language name. The codes match DictionaryParsing.normalizeLang.
LANGUAGES = {
    "en": "English",
    "es": "Spanish",
    "fr": "French",
    "de": "German",
    "it": "Italian",
    "pt": "Portuguese",
    "nl": "Dutch",
    "sv": "Swedish",
    "da": "Danish",
    "no": "Norwegian Bokmål",
    "fi": "Finnish",
    "pl": "Polish",
    "cs": "Czech",
    "ro": "Romanian",
    "hu": "Hungarian",
    "el": "Greek",
    "ru": "Russian",
    "uk": "Ukrainian",
    "tr": "Turkish",
    "id": "Indonesian",
    "vi": "Vietnamese",
    "ja": "Japanese",
    "zh": "Chinese",
    "ko": "Korean",
    "ar": "Arabic",
    "he": "Hebrew",
    "hi": "Hindi",
    "th": "Thai",
    "la": "Latin",
}


def source_url(name):
    folder = urllib.parse.quote(name)
    file = urllib.parse.quote("kaikki.org-dictionary-" + name.replace(" ", "") + ".jsonl")
    return f"https://kaikki.org/dictionary/{folder}/{file}"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("work")
    parser.add_argument("base_url")
    parser.add_argument("--version", type=int, default=1)
    parser.add_argument("--only")
    parser.add_argument("--keep-sources", action="store_true")
    args = parser.parse_args()

    src_dir = os.path.join(args.work, "kaikki")
    out_dir = os.path.join(args.work, "out")
    os.makedirs(src_dir, exist_ok=True)
    os.makedirs(out_dir, exist_ok=True)
    manifest_path = os.path.join(out_dir, "dictionaries.json")
    packs = {}
    if os.path.exists(manifest_path):
        packs = {p["lang"]: p for p in json.load(open(manifest_path))["packs"]}

    wanted = args.only.split(",") if args.only else list(LANGUAGES)
    for lang in wanted:
        name = LANGUAGES[lang]
        src = os.path.join(src_dir, name.replace(" ", "") + ".jsonl")
        if not os.path.exists(src):
            print(f"[{lang}] downloading {name}", flush=True)
            subprocess.run(["curl", "-sSfL", "-o", src + ".part", source_url(name)], check=True)
            os.rename(src + ".part", src)
        stem = f"dict-{lang}-v{args.version}.sqlite"
        out = os.path.join(out_dir, stem)
        print(f"[{lang}] building", flush=True)
        info = build_dictionary.build(src, lang, out, args.version)
        os.remove(out)  # only the gzipped copy is published
        packs[lang] = {
            "lang": lang,
            "version": args.version,
            "url": f"{args.base_url.rstrip('/')}/{stem}.gz",
            "bytes": info["bytes"],
            "gzipBytes": info["gzipBytes"],
            "sha256": info["sha256"],
            "entries": info["entries"],
        }
        print(f"[{lang}] {info['entries']} entries, {info['gzipBytes'] / 1e6:.1f} MB download", flush=True)
        if not args.keep_sources:
            os.remove(src)
        ordered = [packs[code] for code in LANGUAGES if code in packs]
        with open(manifest_path, "w") as f:
            json.dump({"packs": ordered}, f, indent=2)


if __name__ == "__main__":
    main()
