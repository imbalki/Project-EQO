#!/usr/bin/env python3
"""TASK-004: compare EQO module lint findings against TASK-002's upstream
lint findings by (id, file) pair, to decide the brief's lint-baseline path.

The brief allows <module>/lint-baseline.xml entries ONLY for findings that
already exist in android/Phase-One/evidence/task-002-lint/lint-results-debug.xml
for the same file. This script computes that match count. Any finding that
does not match must be fixed or reported as a defect - never baselined.

Input: lint-findings-pre-fix.csv (id,file,line transcribed verbatim from the
pre-fix lint run; see the extraction map 'Lint (run 3)' section).
File matching compares the path suffix after 'java/' or 'res/' (or the leaf
name for build-script findings), so upstream's app/src/main/... paths match
the modules' <module>/src/main/... paths for the same moved file.

Usage: python lint-baseline-match.py <task-002-lint-results.xml> <findings.csv>
"""
import csv
import re
import sys
from pathlib import Path


def tail(path: str) -> str:
    p = path.replace("\\", "/")
    for marker in ("/java/", "/res/", "/"):
        if marker in p:
            return p.split(marker, 1)[1] if marker != "/" else p.rsplit("/", 1)[1]
    return p


def main() -> int:
    ref_xml = Path(sys.argv[1]).read_text(encoding="utf-8")
    ref = set()
    for b in re.split(r"(?=<issue\b)", ref_xml):
        if not b.startswith("<issue"):
            continue
        idm = re.search(r'id="([^"]+)"', b)
        locs = re.findall(r'file="([^"]+)"', b)
        if idm and locs:
            ref.add((idm.group(1), tail(locs[0])))
    rows = list(csv.DictReader(open(sys.argv[2], encoding="utf-8")))
    matched = [r for r in rows if (r["id"], tail(r["file"])) in ref]
    print(f"task-002 findings: {len(ref)} (id+file pairs)")
    print(f"EQO findings compared: {len(rows)}")
    print(f"MATCH COUNT (id+file): {len(matched)}")
    for r in matched:
        print("  match:", r["id"], r["file"])
    print(
        "baseline path applicable:",
        "yes" if matched and len(matched) == len(rows) else "no",
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
