#!/usr/bin/env python3
"""TASK-004 Phase 0: compute intra-app import graph for OpenDroid sources."""
import json
import os
import re
import sys
from collections import defaultdict

SRC = sys.argv[1] if len(sys.argv) > 1 else r"C:\Users\<user>\Claude\worktrees\_upstream\opendroid\app\src\main\java"
TEST = sys.argv[2] if len(sys.argv) > 2 else r"C:\Users\<user>\Claude\worktrees\_upstream\opendroid\app\src\test\java"
PREFIX = "com.opendroid.ai"

IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([\w.]+)", re.M)


def package_of(path, root):
    rel = os.path.relpath(path, root).replace("\\", "/")
    pkg = rel.rsplit("/", 1)[0].replace("/", ".")
    return pkg


def scan(root):
    files = {}
    for dirpath, _dirs, names in os.walk(root):
        for n in names:
            if not n.endswith(".kt") and not n.endswith(".java"):
                continue
            p = os.path.join(dirpath, n)
            with open(p, encoding="utf-8", errors="replace") as f:
                text = f.read()
            pkg = package_of(p, root)
            imports = sorted(set(IMPORT_RE.findall(text)))
            app_imports = [i for i in imports if i.startswith(PREFIX + ".")]
            other_third = [
                i for i in imports
                if not i.startswith(PREFIX + ".") and not i.startswith("java") and not i.startswith("kotlin")
            ]
            rel = os.path.relpath(p, root).replace("\\", "/")
            files[rel] = {
                "package": pkg,
                "imports_app": app_imports,
                "imports_third_party": other_third,
            }
    return files


main = scan(SRC)
test = scan(TEST)

# Resolve app imports to file-level edges (best effort: class name at end)
def to_pkg(imp):
    # com.opendroid.ai.core.llm.LLMProvider -> com.opendroid.ai.core.llm
    parts = imp.split(".")
    return ".".join(parts[:-1])


edges = {}
for rel, info in main.items():
    targets = defaultdict(list)
    for imp in info["imports_app"]:
        targets[to_pkg(imp)].append(imp)
    edges[rel] = {k: v for k, v in sorted(targets.items())}

# package-level summary
pkg_files = defaultdict(list)
for rel in main:
    pkg_files[main[rel]["package"]].append(rel)

pkg_deps = defaultdict(set)
for rel, info in main.items():
    for imp in info["imports_app"]:
        dep_pkg = to_pkg(imp)
        own = info["package"]
        if dep_pkg != own and not dep_pkg.startswith(own + "."):
            pkg_deps[own].add(dep_pkg)

out = {
    "main_files": main,
    "test_files": test,
    "file_edges": edges,
    "package_deps": {k: sorted(v) for k, v in sorted(pkg_deps.items())},
}
with open(sys.argv[3] if len(sys.argv) > 3 else "import-graph.json", "w", encoding="utf-8") as f:
    json.dump(out, f, indent=1, sort_keys=True)

print("main files:", len(main))
print("test files:", len(test))
print("package deps:")
for k, v in sorted(pkg_deps.items()):
    print(" ", k, "->", sorted(v))
