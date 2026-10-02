#!/usr/bin/env python3
"""TASK-004 bisectability check: every moved file must equal the upstream
original EXCEPT the single leading provenance header line.
Usage: python verify-move.py <upstream-src-root> [<worktree-android-root>]
Exit 0 if and only if every moved file matches. Prints a per-file report.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MANIFEST = os.path.join(HERE, "move-manifest.json")
UPSTREAM_SRC = sys.argv[1] if len(sys.argv) > 1 else r"C:\Users\<user>\Claude\worktrees\_upstream\opendroid\app\src\main\java"
WORKTREE_ANDROID = sys.argv[2] if len(sys.argv) > 2 else r"C:\Users\<user>\Claude\worktrees\task-004\android"
HEADER_PREFIX = "// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/"

manifest = json.load(open(MANIFEST, encoding="utf-8"))
ok, bad = 0, []
total = 0
for mod, rels in sorted(manifest["files"].items()):
    for rel in sorted(rels):
        total += 1
        src = os.path.join(UPSTREAM_SRC, rel)
        dst = os.path.join(WORKTREE_ANDROID, mod, "src", "main", "java", rel)
        if not os.path.exists(dst):
            bad.append((rel, "MISSING"))
            continue
        orig = open(src, encoding="utf-8", errors="replace").read()
        moved = open(dst, encoding="utf-8", errors="replace").read()
        expected_header = HEADER_PREFIX + rel + "\n"
        if moved == expected_header + orig:
            ok += 1
        elif moved == orig:
            ok += 1
            print("WARN no header on", rel)
        else:
            # locate first differing line count
            mlines = moved.splitlines()
            olines = orig.splitlines()
            diff = "first-diff-line=%d" % next(
                (i + 1 for i in range(min(len(mlines), len(olines))) if mlines[i] != olines[i]),
                0,
            )
            bad.append((rel, diff))

print(f"moved files checked: {total}; byte-identical (besides header): {ok}; mismatches: {len(bad)}")
for rel, why in bad:
    print("  MISMATCH", rel, why)
sys.exit(0 if not bad else 1)