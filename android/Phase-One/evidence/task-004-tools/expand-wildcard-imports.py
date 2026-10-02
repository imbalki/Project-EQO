#!/usr/bin/env python3
"""TASK-004: expand wildcard imports flagged by ktlint (no-wildcard-imports).

For each `import <pkg>.*` in the EQO module sources, compute the set of
symbols the file actually uses and replace the wildcard with explicit
imports. Candidates come from:
  - top-level declarations of in-repo packages (parsed from source)
  - curated symbol lists for third-party packages (room, coroutines, flow,
    mlkit) filtered by name usage in the file body.

Usage: python expand-wildcard-imports.py [--apply] <android-dir>
Without --apply it prints the planned replacements (dry run).
"""
import re
import sys
from pathlib import Path

# Third-party candidates: name -> package (only names that exist in the pkg).
THIRD_PARTY = {
    "androidx.room": [
        "Dao", "Entity", "Insert", "Update", "Delete", "Upsert", "Query",
        "RawQuery", "Transaction", "OnConflictStrategy", "PrimaryKey",
        "ColumnInfo", "Embedded", "Relation", "Junction", "Index",
        "ForeignKey", "Ignore", "Database", "TypeConverter", "TypeConverters",
        "RewrittenQueries", "SkipQueryVerification", "MapColumn", "FullText",
    ],
    "kotlinx.coroutines": [
        "CoroutineScope", "CoroutineStart", "CoroutineName",
        "CoroutineExceptionHandler", "Dispatchers", "Job", "SupervisorJob",
        "Deferred", "CancellationException", "NonCancellable", "GlobalScope",
        "launch", "async", "await", "awaitAll", "withContext", "delay",
        "runBlocking", "yield", "cancel", "ensureActive", "isActive",
        "coroutineScope", "supervisorScope", "currentCoroutineContext",
        "withTimeout", "withTimeoutOrNull", "flowOn",
    ],
    "kotlinx.coroutines.flow": [
        "Flow", "MutableStateFlow", "StateFlow", "SharedFlow",
        "MutableSharedFlow", "flow", "update", "updateAndGet", "stateIn",
        "shareIn", "combine", "onCompletion", "onStart",
        "collect", "collectLatest", "first", "toList",
        "mapLatest", "flatMapLatest", "distinctUntilChanged",
        "debounce", "launchIn", "asStateFlow", "transformLatest",
        "SharingStarted", "callbackFlow", "channelFlow", "runningFold",
    ],
    "com.google.mlkit.genai.prompt": [
        "CachedContext", "Caches", "Candidate", "CountTokensResponse",
        "CreateCachedContextRequest", "GenerateContentRequest",
        "GenerateContentResponse", "Generation", "GenerationConfig",
        "GenerativeModel", "ImagePart", "ModelConfig", "ModelPreference",
        "ModelReleaseStage", "Part", "PromptPrefix", "TextPart",
        "generateContentRequest", "generationConfig", "modelConfig",
        "createCachedContextRequest",
    ],
}

DECL_RE = re.compile(
    r"^(?:public |internal |private |protected )?"
    r"(?:actual |expect |abstract |open |data |sealed |enum |annotation |inner |value |companion |inline |operator |infix |tailrec |suspend |external )*"
    r"(?:class|interface|object|typealias)\s+([A-Za-z_][A-Za-z0-9_]*)",
)
FUN_RE = re.compile(
    r"^(?:public |internal |private |protected )?"
    r"(?:actual |expect |abstract |open |override |inline |operator |infix |tailrec |suspend |external |infix )*"
    r"fun\s+(?:[A-Za-z_][A-Za-z0-9_]*(?:<[^>]*>)?\.)?([A-Za-z_][A-Za-z0-9_]*)\s*(?:<[^>]*>)?\s*\(",
)
TOPLEVEL_VAL_RE = re.compile(
    r"^(?:public |internal |private |protected )?(?:const )?(?:val|var)\s+"
    r"(?:[A-Za-z_][A-Za-z0-9_]*(?:<[^>]*>)?\.)?([A-Za-z_][A-Za-z0-9_]*)\s*[:=<]",
)


def package_decls(root: Path, pkg: str) -> set:
    names = set()
    pkg_path = pkg.replace(".", "/")
    for src in root.rglob("*.kt"):
        if "/build/" in src.as_posix() or "\\build\\" in src.as_posix():
            continue
        text = src.read_text(encoding="utf-8")
        if not re.search(rf"^package {re.escape(pkg)}\s*$", text, flags=re.M):
            continue
        for line in text.splitlines():
            if line.startswith((" ", "\t")):
                continue  # nested declaration: not importable at top level
            m = DECL_RE.match(line)
            if m:
                names.add(m.group(1))
            m = FUN_RE.match(line)
            if m:
                names.add(m.group(1))
            m = TOPLEVEL_VAL_RE.match(line)
            if m:
                names.add(m.group(1))
    return names


def used_names(body: str) -> set:
    return set(re.findall(r"[A-Za-z_][A-Za-z0-9_]*", body))


def main() -> int:
    args = [a for a in sys.argv[1:]]
    apply_changes = "--apply" in args
    args = [a for a in args if a != "--apply"]
    root = Path(args[0])
    decl_cache = {}
    total = 0
    for src in sorted(root.rglob("*.kt")):
        if "/build/" in src.as_posix():
            continue
        text = src.read_text(encoding="utf-8")
        wilds = re.findall(r"^import ([\w.]+)\.\*\s*$", text, flags=re.M)
        if not wilds:
            continue
        lines = text.splitlines(keepends=True)
        body = "".join(
            l for l in lines if not l.startswith("import ")
        )
        used = used_names(body)
        out_lines = []
        changed = False
        for line in lines:
            m = re.match(r"^import ([\w.]+)\.\*\s*$", line)
            if not m:
                out_lines.append(line)
                continue
            pkg = m.group(1)
            if pkg.startswith("com.opendroid."):
                cands = decl_cache.get(pkg)
                if cands is None:
                    cands = package_decls(root, pkg)
                    decl_cache[pkg] = cands
            else:
                cands = set(THIRD_PARTY.get(pkg, []))
            needed = sorted(n for n in cands if re.search(
                r"\b" + re.escape(n) + r"\b", body))
            print(f"{src.relative_to(root)}: import {pkg}.* -> {needed}")
            for n in needed:
                out_lines.append(f"import {pkg}.{n}\n")
            if not needed:
                print(f"  WARNING: no candidates matched for {pkg} in {src.name}")
            changed = True
            total += 1
        if apply_changes and changed:
            src.write_text("".join(out_lines), encoding="utf-8")
    print(f"wildcard imports processed: {total} (apply={apply_changes})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
