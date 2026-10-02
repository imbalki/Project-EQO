#!/usr/bin/env python3
"""TASK-004 Phase 0: transitive closure of app-internal deps for in-scope packages."""
import json
import os
import re
import sys
from collections import defaultdict, deque

SRC = r"C:\Users\<user>\Claude\worktrees\_upstream\opendroid\app\src\main\java"
PREFIX = "com.opendroid.ai"
IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([\w.]+)", re.M)
DECL_RE = re.compile(
    r"^\s*(?:@\w+\s+)*(?:public\s+|private\s+|internal\s+|protected\s+|abstract\s+|open\s+|sealed\s+|data\s+|enum\s+|annotation\s+|value\s+|inner\s+|companion\s+|inline\s+|suspend\s+|operator\s+|const\s+|lateinit\s+)*(?:class|object|interface|enum\s+class|annotation\s+class|fun|val|var)\s+(\w+)",
    re.M,
)

files = {}          # rel path -> info
by_symbol = defaultdict(list)  # "pkg.Class" -> [rel]

for dirpath, _d, names in os.walk(SRC):
    for n in names:
        if not n.endswith(".kt"):
            continue
        p = os.path.join(dirpath, n)
        rel = os.path.relpath(p, SRC).replace("\\", "/")
        pkg = rel.rsplit("/", 1)[0].replace("/", ".")
        text = open(p, encoding="utf-8", errors="replace").read()
        imports = IMPORT_RE.findall(text)
        decls = set(DECL_RE.findall(text))
        decls.add(n[:-3])  # file facade name
        files[rel] = {"package": pkg, "imports": imports, "decls": sorted(decls), "lines": text.count("\n") + 1}
        for d in decls:
            by_symbol[f"{pkg}.{d}"].append(rel)

# also index by package wildcard fallback: pkg -> [rels]
by_pkg = defaultdict(list)
for rel, info in files.items():
    by_pkg[info["package"]].append(rel)

def resolve(imp):
    """app import -> set of candidate rel files"""
    if not imp.startswith(PREFIX):
        return []
    if imp in by_symbol:
        return by_symbol[imp]
    # maybe package-level or nested: strip last segments
    parts = imp.split(".")
    for k in range(len(parts) - 1, 3, -1):
        cand = ".".join(parts[:k])
        if cand in by_pkg:
            return by_pkg[cand]
    return []

INSCOPE_PKGS = [
    "com.opendroid.ai.core.agent",
    "com.opendroid.ai.core.llm",
    "com.opendroid.ai.core.security",
    "com.opendroid.ai.accessibility",
]

def in_scope(rel):
    pkg = files[rel]["package"]
    return any(pkg == p or pkg.startswith(p + ".") for p in INSCOPE_PKGS)

# BFS closure from in-scope files
closure = {}
frontier = deque([r for r in files if in_scope(r)])
seen = set(frontier)
missing = set()
while frontier:
    rel = frontier.popleft()
    for imp in files[rel]["imports"]:
        if not imp.startswith(PREFIX):
            continue
        for tgt in resolve(imp):
            if tgt not in seen:
                seen.add(tgt)
                frontier.append(tgt)
        if not resolve(imp):
            missing.add(imp)

closure = sorted(seen)
external = sorted(r for r in closure if not in_scope(r))
ext_by_pkg = defaultdict(list)
for r in external:
    ext_by_pkg[files[r]["package"]].append(r)

out = {
    "closure_count": len(closure),
    "in_scope_count": len([r for r in closure if in_scope(r)]),
    "external_by_package": {k: sorted(v) for k, v in sorted(ext_by_pkg.items())},
    "unresolved_imports": sorted(missing),
    "external_files": {r: {"package": files[r]["package"], "lines": files[r]["lines"]} for r in external},
}
json.dump(out, open(sys.argv[1] if len(sys.argv) > 1 else "closure.json", "w"), indent=1)

print("in-scope files:", out["in_scope_count"])
print("total closure:", out["closure_count"])
print("external files needed:", len(external))
for k, v in sorted(ext_by_pkg.items()):
    print(f"  {k}: {len(v)} files, {sum(files[r]['lines'] for r in v)} lines")
    for r in sorted(v):
        print("     -", r, files[r]["lines"])
print("unresolved imports:", len(missing))
for m in sorted(missing):
    print("  ?", m)
