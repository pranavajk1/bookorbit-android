#!/usr/bin/env python3
"""Builds an offline dictionary for the reader from a Wiktionary extract.

The input is one of kaikki.org's per-language Wiktextract files: every entry English Wiktionary has
for words of that language, one JSON object per line, with definitions written in English
(https://kaikki.org/dictionary/<Language>/kaikki.org-dictionary-<Language>.jsonl). The output is a
small SQLite file the app downloads once and looks words up in on the device, gzipped for transfer.

Usage:
    build_dictionary.py <input.jsonl> <lang code> <out.sqlite> [--version N]

Wiktionary text is CC BY-SA 4.0 (and GFDL); the file records that in its meta table and the app
credits it under every definition.

What's kept is what a reader selects: single words (no phrases, which a long-press can't pick),
no proper names, at most a few senses per word and short examples only.

Tables:
    entries(key, word, fold, pos, ipa, senses)
        One row per word and part of speech. `key` is the lowercased word; `word` its spelling when
        that differs (capitals); `fold` the key without accents when that differs. `senses` is a JSON
        list of {"g": gloss, "x": example}.
    forms(key, fold, pos, lemma, label)
        Words that are only a form of another ("walked": "simple past and past participle of walk"),
        and the forms in a lemma's inflection table that have no entry of their own (label null).
    labels(id, text)
        The glosses of forms, each stored once with the lemma as "{}": "simple past and past
        participle of {}" is shared by thousands of verbs.
    meta(key, value)
"""

import argparse
import gzip
import hashlib
import json
import os
import re
import shutil
import sqlite3
import sys
import unicodedata

MAX_SENSES = 6
MAX_EXAMPLES = 2
MAX_EXAMPLE_CHARS = 140
MAX_GLOSS_CHARS = 300

# Parts of speech that aren't words a reader looks up: names (places, surnames) are most of the bulk.
SKIP_POS = {
    "name", "character", "symbol", "punct", "suffix", "prefix", "infix", "interfix", "affix",
    "circumfix", "romanization", "combining_form", "proverb", "phrase", "prep_phrase",
}

# Inflection-table entries that aren't forms of the word (table headers, template names).
SKIP_FORM_TAGS = {"table-tags", "inflection-template", "class", "romanization", "canonical", "auxiliary"}

# Forms kept from one word's inflection table. Languages that build words from endings (Finnish,
# Hungarian, Turkish, Latin) list hundreds per word, mostly with possessive or clitic suffixes;
# the tables lead with the common forms, so the first ones cover what a reader meets.
MAX_TABLE_FORMS = 40
SKIP_TABLE_FORM_TAGS = {"possessive", "clitic"}


def fold(text):
    decomposed = unicodedata.normalize("NFKD", text)
    return "".join(c for c in decomposed if not unicodedata.combining(c))


def clean(text):
    return re.sub(r"\s+", " ", text).strip()


def pick_example(sense):
    """A short usage example: a plain example wins over a quotation, shorter wins over longer."""
    best = None
    for ex in sense.get("examples") or []:
        text = clean(ex.get("text") or "")
        if not text or len(text) > MAX_EXAMPLE_CHARS or "\n" in (ex.get("text") or ""):
            continue
        rank = (0 if ex.get("type") != "quotation" else 1, len(text))
        if best is None or rank < best[0]:
            best = (rank, text)
    return best[1] if best else None


def senses_of(entry):
    """The entry's senses, each {"g": gloss, "x": example?, "f": lemma when it's a form of one}."""
    out = []
    for sense in entry.get("senses") or []:
        tags = set(sense.get("tags") or [])
        if "no-gloss" in tags:
            continue
        glosses = sense.get("raw_glosses") or sense.get("glosses") or []
        if not glosses:
            continue
        gloss = clean(glosses[-1])[:MAX_GLOSS_CHARS]
        if not gloss:
            continue
        item = {"g": gloss}
        target = (sense.get("form_of") or sense.get("alt_of") or [{}])[0].get("word")
        if target:
            item["f"] = target
        elif sum(1 for s in out if "x" in s) < MAX_EXAMPLES:
            example = pick_example(sense)
            if example:
                item["x"] = example
        out.append(item)
        if len(out) >= MAX_SENSES:
            break
    return out


def ipa_of(entry):
    for sound in entry.get("sounds") or []:
        ipa = sound.get("ipa")
        if ipa:
            return ipa
    return None


def build(src, lang, out, version):
    if os.path.exists(out):
        os.remove(out)
    db = sqlite3.connect(out)
    db.executescript(
        """
        PRAGMA journal_mode = OFF;
        PRAGMA synchronous = OFF;
        CREATE TABLE entries(key TEXT NOT NULL, word TEXT, fold TEXT, pos TEXT NOT NULL, ipa TEXT, senses TEXT NOT NULL);
        CREATE TABLE forms(key TEXT NOT NULL, fold TEXT, pos TEXT, lemma TEXT NOT NULL, label INTEGER);
        CREATE TABLE labels(id INTEGER PRIMARY KEY, text TEXT NOT NULL);
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);
        """
    )
    entries = 0
    form_rows = set()
    table_forms = set()
    with open(src, encoding="utf-8") as f:
        for line in f:
            entry = json.loads(line)
            word = entry.get("word")
            pos = entry.get("pos")
            if not word or not pos or pos in SKIP_POS or " " in word:
                continue
            senses = senses_of(entry)
            if not senses:
                continue
            key = word.lower()
            folded = fold(key)
            folded = folded if folded != key else None
            pointers = [s for s in senses if "f" in s]
            for s in pointers:
                form_rows.add((key, folded, pos, s["f"], s["g"]))
            definitions = [{k: v for k, v in s.items() if k != "f"} for s in senses if "f" not in s]
            if definitions:
                db.execute(
                    "INSERT INTO entries VALUES (?, ?, ?, ?, ?, ?)",
                    (key, word if word != key else None, folded, pos, ipa_of(entry),
                     json.dumps(definitions, ensure_ascii=False, separators=(",", ":"))),
                )
                entries += 1
            if not pointers:
                # A lemma's own inflection table: only kept for forms with no entry (checked below).
                kept = 0
                for form in entry.get("forms") or []:
                    text = form.get("form")
                    tags = set(form.get("tags") or [])
                    if not text or tags & (SKIP_FORM_TAGS | SKIP_TABLE_FORM_TAGS) or " " in text or text.lower() == key:
                        continue
                    table_forms.add((text.lower(), pos, word))
                    kept += 1
                    if kept >= MAX_TABLE_FORMS:
                        break

    db.execute("CREATE INDEX entries_key ON entries(key)")
    db.execute("CREATE INDEX entries_fold ON entries(fold) WHERE fold IS NOT NULL")
    known = {row[0] for row in db.execute("SELECT DISTINCT key FROM entries")}
    known |= {row[0] for row in form_rows}
    for form_key, pos, lemma in table_forms:
        if form_key not in known:
            folded = fold(form_key)
            form_rows.add((form_key, folded if folded != form_key else None, pos, lemma, None))
    labels = {}

    def label_id(gloss, lemma):
        if gloss is None:
            return None
        text = gloss.replace(lemma, "{}", 1) if lemma in gloss else gloss
        return labels.setdefault(text, len(labels) + 1)

    rows = [(k, f, p, lemma, label_id(gloss, lemma)) for k, f, p, lemma, gloss in form_rows]
    db.executemany("INSERT INTO forms VALUES (?, ?, ?, ?, ?)", sorted(rows, key=lambda r: (r[0], r[2] or "", r[3])))
    db.executemany("INSERT INTO labels VALUES (?, ?)", [(i, t) for t, i in labels.items()])
    db.execute("CREATE INDEX forms_key ON forms(key)")
    db.execute("CREATE INDEX forms_fold ON forms(fold) WHERE fold IS NOT NULL")
    meta = {
        "lang": lang,
        "version": str(version),
        "source": "Wiktionary, via kaikki.org (Wiktextract)",
        "license": "CC BY-SA 4.0",
        "entries": str(entries),
    }
    db.executemany("INSERT INTO meta VALUES (?, ?)", meta.items())
    db.commit()
    db.execute("VACUUM")
    db.close()

    gz = out + ".gz"
    with open(out, "rb") as raw, gzip.open(gz, "wb", compresslevel=9) as packed:
        shutil.copyfileobj(raw, packed)
    digest = hashlib.sha256(open(gz, "rb").read()).hexdigest()
    return {
        "lang": lang,
        "version": version,
        "entries": entries,
        "forms": len(form_rows),
        "bytes": os.path.getsize(out),
        "gzipBytes": os.path.getsize(gz),
        "sha256": digest,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("src")
    parser.add_argument("lang")
    parser.add_argument("out")
    parser.add_argument("--version", type=int, default=1)
    args = parser.parse_args()
    info = build(args.src, args.lang, args.out, args.version)
    json.dump(info, sys.stdout, indent=2)
    print()


if __name__ == "__main__":
    main()
