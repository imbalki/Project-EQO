#!/usr/bin/env python3
"""TASK-004 Phase 1: copy in-scope OpenDroid files VERBATIM into EQO modules,
prepending the provenance header line. No other content change.
Usage: python copy-move.py <upstream-src-root> <worktree-android-root> [--dry-run]
Writes a manifest json next to itself for verify-move.py.
"""
import hashlib
import json
import os
import shutil
import sys

UPSTREAM_SRC = sys.argv[1] if len(sys.argv) > 1 else r"C:\Users\<user>\Claude\worktrees\_upstream\opendroid\app\src\main\java"
WORKTREE_ANDROID = sys.argv[2] if len(sys.argv) > 2 else r"C:\Users\<user>\Claude\worktrees\task-004\android"
DRY = "--dry-run" in sys.argv

ORIGIN = "yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51"

# module -> upstream package prefix
IN_SCOPE_DIRS = {
    "core-security": ["core/security"],
    "core-llm": ["core/llm"],
    "core-agent": ["core/agent"],
    "platform-a11y": ["accessibility"],
}
# core/agent files that physically land in :core-llm (split package, cycle break)
AGENT_TO_LLM = {
    "core/agent/ActionRisk.kt",
    "core/agent/ActionSchema.kt",
    "core/agent/DeviceStateProvider.kt",
    "core/agent/IntentClassifier.kt",
    "core/agent/ChatErrorUiState.kt",
}
MOVE_INTO = {
    "core-llm": [
        "core/util/DeviceCapabilities.kt", "core/util/DurationParser.kt",
        "core/util/NetworkErrorFormatter.kt", "core/util/UrlUtils.kt",
        "data/models/AutoMode.kt", "data/models/AutoReplyConfig.kt", "data/models/ChatMessage.kt",
        "data/models/HabitEvent.kt", "data/models/HabitRoutine.kt", "data/models/LLMConfig.kt",
        "data/models/Macro.kt", "data/models/Memory.kt", "data/models/Plan.kt",
        "data/models/PlanStatus.kt", "data/models/PlanStep.kt", "data/models/StepResult.kt",
        "data/models/UserProfile.kt",
        "data/repository/SettingsRepository.kt",
        "data/db/dao/ModelDao.kt", "data/db/dao/NotificationDao.kt", "data/db/dao/UnknownActionDao.kt",
        "data/db/entities/ModelEntity.kt", "data/db/entities/NotificationEntity.kt",
        "data/db/entities/UnknownActionEntity.kt",
        "actions/ActionDispatcher.kt", "actions/base/Action.kt", "actions/base/ActionResult.kt",
    ],
    "core-agent": [
        "core/memory/WorkingMemory.kt", "core/memory/ExecutionHistoryPrivacy.kt",
        "core/crash/CrashLogRedactor.kt",
    ],
    "core-security": [
        "core/settings/AppSettingsStore.kt",
        "social/domain/model/SocialModels.kt", "social/domain/model/SocialPlatform.kt",
    ],
}

def module_for(rel):
    """rel = 'com/opendroid/ai/<subpath>.kt'"""
    sub = rel[len("com/opendroid/ai/"):]
    pkg_dir = sub.rsplit("/", 1)[0]
    for mod, prefixes in IN_SCOPE_DIRS.items():
        for p in prefixes:
            if pkg_dir == p or pkg_dir.startswith(p + "/"):
                if sub in AGENT_TO_LLM:
                    return "core-llm"
                return mod
    for mod, files in MOVE_INTO.items():
        if sub in files:
            return mod
    return None

plan = {}
missing = []
for dirpath, _d, names in os.walk(UPSTREAM_SRC):
    for n in names:
        if not n.endswith(".kt"):
            continue
        p = os.path.join(dirpath, n)
        rel = os.path.relpath(p, UPSTREAM_SRC).replace("\\", "/")
        mod = module_for(rel)
        if mod:
            plan.setdefault(mod, []).append(rel)

tot = 0
for mod in sorted(plan):
    print(f"== {mod}: {len(plan[mod])} files")
    for rel in sorted(plan[mod]):
        tot += 1
        src = os.path.join(UPSTREAM_SRC, rel)
        dst = os.path.join(WORKTREE_ANDROID, mod, "src", "main", "java", rel)
        text = open(src, encoding="utf-8", errors="replace").read()
        header = f"// Origin: {ORIGIN}, path: app/src/main/java/{rel}\n"
        if DRY:
            print("  would move", rel, "->", mod)
            continue
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        if os.path.exists(dst):
            existing = open(dst, encoding="utf-8", errors="replace").read()
            if existing != header + text and existing != text:
                print("  WARN dst differs (exists with edit?):", rel)
                continue
        with open(dst, "w", encoding="utf-8", newline="\n") as f:
            f.write(header + text)
        print("  moved", rel)

if not DRY:
    manifest = {"origin": ORIGIN, "files": {mod: sorted(plan[mod]) for mod in plan}}
    mp = os.path.join(os.path.dirname(os.path.abspath(__file__)), "move-manifest.json")
    json.dump(manifest, open(mp, "w"), indent=1)
    print(f"total moved: {tot}; manifest: {mp}")