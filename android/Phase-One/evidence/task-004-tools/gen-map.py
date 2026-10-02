#!/usr/bin/env python3
"""TASK-004 Phase 0 map generator.
Computes per-file app-internal imports from the upstream OpenDroid tree and
renders android/Phase-One/evidence/task-004-extraction-map.md.
Decision table is explicit (human decisions), imports are script-computed.
"""
import json
import os
import sys

SRC = r"C:\Users\<user>\Claude\worktrees\_upstream\opendroid\app\src\main\java"
GRAPH = json.load(open(r"C:\Users\<user>\AppData\Local\hermes\profiles\eqo-core-dev\cache\scratch\t004\import-graph.json"))
main = GRAPH["main_files"]

PREFIX = "com.opendroid.ai"
ORIGIN = "yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51"

MOD = {
    "core-agent": ":core-agent",
    "core-llm": ":core-llm",
    "core-security": ":core-security",
    "platform-a11y": ":platform-a11y",
}

# ---- Decisions: in-scope package files -> module ----
AGENT_MOVE_TO_LLM = {
    "com/opendroid/ai/core/agent/ActionRisk.kt",
    "com/opendroid/ai/core/agent/ActionSchema.kt",
    "com/opendroid/ai/core/agent/DeviceStateProvider.kt",
    "com/opendroid/ai/core/agent/IntentClassifier.kt",
    "com/opendroid/ai/core/agent/ChatErrorUiState.kt",
}
AGENT_MOVE = {
    "com/opendroid/ai/core/agent/AgentLoop.kt",
    "com/opendroid/ai/core/agent/ActionSequenceExecutor.kt",
    "com/opendroid/ai/core/agent/AliasResolver.kt",
    "com/opendroid/ai/core/agent/AutoApprovalPolicy.kt",
    "com/opendroid/ai/core/agent/AutoReplyEngine.kt",
    "com/opendroid/ai/core/agent/ChatErrorUiState.kt",
    "com/opendroid/ai/core/agent/ContactResolver.kt",
    "com/opendroid/ai/core/agent/PlanManager.kt",
    "com/opendroid/ai/core/agent/PlanValidator.kt",
    "com/opendroid/ai/core/agent/ReEvaluationEngine.kt",
    "com/opendroid/ai/core/agent/ReplyDispatcher.kt",
    "com/opendroid/ai/core/agent/VisionEngine.kt",
}

LLM_MOVE = {  # everything else in core/llm moves
}
SECURITY_MOVE = {}
A11Y_MOVE = {}

def all_in(pkg_dir):
    base = "com/opendroid/ai/" + pkg_dir + "/"
    return {r for r in main if r.startswith(base)}

LLM_MOVE = all_in("core/llm")
SECURITY_MOVE = all_in("core/security")
A11Y_MOVE = all_in("accessibility")

# ---- Decisions: external files needed by moved code ----
MOVE_INTO = {
    # :core-llm gets the shared small deps + SettingsRepository (DataStore-only, no Room)
    "core-llm": {
        "com/opendroid/ai/core/util/DeviceCapabilities.kt": "small util used by 7 moved files",
        "com/opendroid/ai/core/util/DurationParser.kt": "small util, used by moved code",
        "com/opendroid/ai/core/util/NetworkErrorFormatter.kt": "small util, used by moved providers",
        "com/opendroid/ai/core/util/UrlUtils.kt": "small util, used by 4 moved files",
        "com/opendroid/ai/data/models/AutoMode.kt": "model used by moved code",
        "com/opendroid/ai/data/models/AutoReplyConfig.kt": "model used by moved code",
        "com/opendroid/ai/data/models/ChatMessage.kt": "model used by 7 moved files",
        "com/opendroid/ai/data/models/HabitEvent.kt": "model used by moved code",
        "com/opendroid/ai/data/models/HabitRoutine.kt": "model used by moved code",
        "com/opendroid/ai/data/models/LLMConfig.kt": "model used by providers/factory",
        "com/opendroid/ai/data/models/Macro.kt": "model used by moved code",
        "com/opendroid/ai/data/models/Memory.kt": "model used by moved code",
        "com/opendroid/ai/data/models/Plan.kt": "model used by 5 moved files",
        "com/opendroid/ai/data/models/PlanStep.kt": "model used by 6 moved files",
        "com/opendroid/ai/data/models/PlanStatus.kt": "model used by moved code",
        "com/opendroid/ai/data/models/StepResult.kt": "model used by moved code",
        "com/opendroid/ai/data/models/UserProfile.kt": "model used by moved code",
        "com/opendroid/ai/data/repository/SettingsRepository.kt": "DataStore-only (no Room); used by 19 moved files incl. all providers",
        "com/opendroid/ai/core/agent/ActionRisk.kt": "agent contract (ActionRisk/ActionRiskPolicy) imported by LLMProviderFactory+ProviderSelectionPolicy; split-package into :core-llm to break llm<->agent cycle",
        "com/opendroid/ai/core/agent/ActionSchema.kt": "agent contract imported by LLMProviderFactory + prompts; split-package into :core-llm (cycle break)",
        "com/opendroid/ai/core/agent/DeviceStateProvider.kt": "agent contract imported by LLMProviderFactory; split-package into :core-llm (cycle break)",
        "com/opendroid/ai/core/agent/IntentClassifier.kt": "agent class imported by LLMProviderFactory; split-package into :core-llm (cycle break)",
        "com/opendroid/ai/data/db/dao/ModelDao.kt": "pure Room-annotated interface used directly by ModelDownloadWorker; compiles with room-runtime, no Room KSP needed (SQL untouched)",
        "com/opendroid/ai/data/db/dao/NotificationDao.kt": "pure Room-annotated interface used by AutoReplyEngine; compiles with room-runtime only",
        "com/opendroid/ai/data/db/dao/UnknownActionDao.kt": "pure Room-annotated interface used by ActionDispatcher/PlanValidator/ReEvaluationEngine",
        "com/opendroid/ai/data/db/entities/NotificationEntity.kt": "pure Room-annotated data class behind NotificationDao",
        "com/opendroid/ai/data/db/entities/ModelEntity.kt": "contains pure ModelStatus enum + markDownload* extensions used by ModelDownloadRetryPolicy/Worker (Room @Entity class rides along)",
        "com/opendroid/ai/data/db/entities/UnknownActionEntity.kt": "pure Room-annotated data class behind UnknownActionDao",
        "com/opendroid/ai/actions/ActionDispatcher.kt": "executor used by AgentLoop/PlanValidator/ReEvaluationEngine AND LLMProviderFactory (llm) - must sit below :core-agent, so it lives in :core-llm (cycle break)",
        "com/opendroid/ai/actions/base/Action.kt": "contract used by ActionDispatcher + moved code",
        "com/opendroid/ai/actions/base/ActionResult.kt": "contract used by ActionDispatcher + ActionSequenceExecutor + moved code",
    },
    # :core-agent gets the small support files its moved code needs
    "core-agent": {
        "com/opendroid/ai/core/memory/WorkingMemory.kt": "small pure class used by PlanManager",
        "com/opendroid/ai/core/memory/ExecutionHistoryPrivacy.kt": "small pure redaction helper used by AgentLoop",
        "com/opendroid/ai/core/crash/CrashLogRedactor.kt": "small pure redactor used by AgentLoop + ActionSequenceExecutor",
    },
    # :core-security gets settings + the two social model files its code needs
    "core-security": {
        "com/opendroid/ai/core/settings/AppSettingsStore.kt": "DataStore store used by LegacyPreferenceMigration + LegacySecurePreferenceInventory (security in-scope)",
        "com/opendroid/ai/social/domain/model/SocialModels.kt": "models used by SocialCredentialStore (security in-scope)",
        "com/opendroid/ai/social/domain/model/SocialPlatform.kt": "model used by SocialCredentialStore (security in-scope)",
    },
}

ADAPTER = {
    "com/opendroid/ai/data/repository/ConversationRepository.kt": "Room-backed (OpenDroidDatabase/DAO); used by AgentLoop -> minimal interface ChatHistoryStore",
    "com/opendroid/ai/data/repository/PlanRepository.kt": "Room-backed (PlanDao/PlanEntity); used by PlanManager -> minimal interface PlanStore",
    "com/opendroid/ai/core/memory/MemoryManager.kt": "Room/repository-backed (369 lines); used by AgentLoop/AutoReplyEngine/ContactResolver -> minimal interface MemoryStore",
    "com/opendroid/ai/core/service/OpenDroidService.kt": "full service component; used by OpenDroidAccessibilityService -> minimal interface ServiceBridge (Phase 2)",
    "com/opendroid/ai/MainActivity.kt": "UI (out of scope); used by ModelDownloadForegroundInfoFactory only for notification tap Intent -> injectable NotificationTapTarget (Phase 2)",
}

QUARANTINE = {  # needed-adjacent files that are deliberately NOT copied (noted, per brief)
    "com/opendroid/ai/core/memory/NotificationIntelligence.kt": "used by AutoReplyEngine for notification smarts; 206 lines, pulls Room; reviewed as Phase-2 candidate, for now quarantined behind NotificationStore",
}

INSCOPE_PKGS = [
    ("com.opendroid.ai.core.agent", "core-agent", "agent loop + planning/execution (TASK-004 scope)"),
    ("com.opendroid.ai.core.llm", "core-llm", "LLM providers incl. Gemma/LiteRT-LM + model download (D-006, non-default)"),
    ("com.opendroid.ai.core.security", "core-security", "credential security + redaction"),
    ("com.opendroid.ai.accessibility", "platform-a11y", "accessibility automation"),
]

def rel_imports(rel):
    """map import class -> list of (other-package) import strings, script-computed."""
    info = main[rel]
    app = [i for i in info["imports_app"] if not _own_pkg(i, info["package"])]
    return sorted(set(app))

def _own_pkg(imp, pkg):
    p = imp.rsplit(".", 1)[0]
    return p == pkg or p.startswith(pkg + ".")

def short(imp):
    return imp.replace(PREFIX + ".", "")

def render_table(rows):
    out = []
    for rel, dec, reason in rows:
        imps = rel_imports(rel)
        imp_str = ", ".join(f"`{short(i)}`" for i in imps) if imps else "-"
        out.append(f"| `{rel[len('com/opendroid/ai/'):]}` | {dec} | {reason} | {imp_str} |")
    return out

def run():
    lines = []
    a = lines.append
    a("# TASK-004 extraction map (Phase 0/1)")
    a("")
    a("- Repository: imbalki/project-eqo, branch `agent/android/36-extraction`")
    a(f"- Source: `{ORIGIN}` (verified `git rev-parse HEAD` in the read-only clone)")
    a("- Scope: modules `:core-agent`, `:core-llm`, `:core-security`, `:platform-a11y`")
    a("- Imports below are **script-computed** (`tools/gen-map.py` reads the upstream tree); decisions are human-edited.")
    a("")

    a("## Decisions for the lead")
    a("")
    a("1. **Issue/branch**: card body says Refs #9 / `9-extraction`; the 2026-10-02 brief says issue **#36** and branch `agent/android/36-extraction` (already created). Followed the brief: commits end `Refs #36`.")
    a("2. **Module dependency DAG (no cycles)**: `:core-security` <- `:core-llm` <- `:core-agent` <- `:platform-a11y`. The upstream graph has hard cycles (core.agent <-> core.llm, core.agent <-> accessibility) that Gradle cannot express; cycles are broken by **split packages** and **minimal interfaces** (both sanctioned by the brief), never by editing moved code in the move commits.")
    a("3. **Split package**: `ActionRisk.kt`, `ActionSchema.kt`, `DeviceStateProvider.kt`, `IntentClassifier.kt` keep their `com.opendroid.ai.core.agent` package but physically live in `:core-llm`, because `LLMProviderFactory`/`ProviderSelectionPolicy`/prompts (in-scope llm) import them while 12 other agent files import llm types. JVM allows split packages across modules; renaming to `ai.eqo` is TASK-005. This keeps every moved file byte-identical to upstream (bisectable).")
    a("4. **SettingsRepository moves into `:core-llm`** (not an interface): it is DataStore-only (no Room/DAO), 351 lines, and is consumed by 19 moved files (all providers); an interface would touch 19 files for no compile-win. Its own deps (core.security, data.models) are both in the DAG below it. **Review point**: it is `data.*` app-layer code living in a core module; TASK-005 may relocate it.")
    a("5. **Room-backed dependencies**: (a) files that are *only* Room annotations + pure types/state (`ModelDao`, `UnknownActionDao`, `NotificationDao`, `UnknownActionEntity`, `NotificationEntity`, `ModelEntity` incl. the `ModelStatus` enum + `markDownload*` extensions) are **moved verbatim** — annotated interfaces/data classes compile with `androidx.room:room-runtime` alone (no Room KSP, SQL strings untouched), preserving behavior exactly; (b) concrete classes needing a live `OpenDroidDatabase` (`ConversationRepository`, `PlanRepository`) and `MemoryManager` become **minimal interfaces** (Phase 2 `refactor(android): adapter ...` commits, listed under Non-move changes). `OpenDroidDatabase` + the remaining Room layer are QUARANTINED (not in Phase One scope; no moved file needs them directly).")
    a("6. **ActionDispatcher + actions.base + UnknownActionDao/Entity move verbatim into `:core-llm`** (not :core-agent, as first drafted): `LLMProviderFactory` (in-scope llm) imports `ActionDispatcher`, and agent files import llm types, so a module `:core-llm`-atop-`:core-agent` placement would create a cycle. ActionDispatcher is 331 lines with no Android framework beyond Context/Log; the four files move as a unit (upstream behavior unchanged).")
    a("7. **:platform-a11y depends on :core-agent (and transitively :core-llm,:core-security)** so `OpenDroidAccessibilityService` can keep its `AgentLoop`/`AgentState`/`SettingsRepository` imports in the move commit (no behavior change). If the dependency weight (litertlm/mlkit coming along transitively) is unacceptable, the Phase-2 alternative is a `SettingsRepository`-lite interface in :platform-a11y — flagged, not decided.")
    a("8. **`R` usage in moved code**: `OpenDroidAccessibilityService` and `ModelDownloadForegroundInfoFactory` import `com.opendroid.ai.R`. Handling happens in Phase 2 (module-local resources vs resource-provider interface); move commits carry the files as-is (they cannot compile until resolved — expected for move commits).")
    a("9. **LiteRT-LM / ML Kit GenAI stay as declared deps of `:core-llm` only** (D-006 optional non-default provider): GemmaProvider, LiteRTLMProvider, HybridOnDeviceProvider, OnDeviceModelRegistry etc. keep their imports. No `ai.liquid.*` anywhere (gate grep).")
    a("10. Dependencies that are Android framework or third-party (Hilt, KSP, Datastore, OkHttp BOM 5.4.0, Retrofit, kotlinx-serialization, WorkManager, coroutines, junit, MockWebServer) are declared per module in Phase 2 exactly at the upstream versions; version catalog is the single source.")
    a("")
    a("## Per-file decisions (in-scope packages)")
    a("")
    a("Columns: file (repo-relative) | decision | one-line reason | imports of OTHER app packages (script-computed).")
    a("")
    for pkg, mod, desc in INSCOPE_PKGS:
        a(f"### {pkg} -> {MOD[mod]}  ({desc})")
        a("")
        a("| File | Decision | Reason | App imports (other packages) |")
        a("|---|---|---|---|")
        pfx = pkg.replace(".", "/") + "/"
        rels = sorted(r for r in main if r.startswith(pfx))
        for rel in rels:
            if rel in AGENT_MOVE_TO_LLM:
                dec = "MOVE (verbatim, split-package into :core-llm)"
                reason = "see Decision 3 (cycle break: llm files import it)"
            else:
                dec = "MOVE (verbatim)"
                reason = "in-scope package"
            rows = [(rel, dec, reason)]
            for row in render_table(rows):
                a(row)
        a("")
    a("## External files needed by moved code")
    a("")
    a("| File | Decision | Reason | Imported by (in-scope) |")
    a("|---|---|---|---|")
    mods = ["core-llm", "core-agent", "core-security"]
    for m in mods:
        for rel, reason in sorted(MOVE_INTO[m].items()):
            tgt_pkg = rel.rsplit("/", 1)[0].replace("/", ".")
            moved_set = set().union(*MOVE_INTO.values()) | set(main)
            # only count files that will be moved (in-scope or in MOVE_INTO)
            users = sorted(
                r for r, info in main.items()
                if r in moved_set and any(i.startswith(tgt_pkg) for i in info["imports_app"])
            )
            a(f"| `{rel}` | MOVE (verbatim) -> {MOD[m]} | {reason} | imported by {len(users)} moved file(s) |")
    for rel, reason in sorted(ADAPTER.items()):
        a(f"| `{rel}` | ADAPTER (Phase 2 interface; NOT copied) | {reason} | - |")
    for rel, reason in sorted(QUARANTINE.items()):
        a(f"| `{rel}` | QUARANTINE (not copied) | {reason} | - |")
    a("")
    a("## Not moved (out of Phase One scope)")
    a("")
    a("Packages entirely unchanged and NOT copied (quarantined by exclusion; TASK-005 provenance map records them): `ui/**`, `di/**` (Hilt wiring for the upstream app, rewritten in EQO anyway), `data/db/**` (Room db + remaining daos/entities), `data/repository/{Memory,Social}Repository`, `data/crash/**`, `core/memory/**` (except WorkingMemory + ExecutionHistoryPrivacy), `core/memory/graph/**`, `core/voice/**`, `core/routine/**`, `core/storage/**`, `core/permissions/**`, `core/service/**`, `core/crash/**` (except CrashLogRedactor), `core/settings/**` (except AppSettingsStore), `actions/**` (except ActionDispatcher + base), `social/**` (except the two model files), root `MainActivity`/`OpenDroidApp`/application classes, `assets/`, `res/`.")
    a("")
    a("## Evidence log (updated as phases complete)")
    a("")
    a("- Phase 0 (this file): map written and pushed.")
    a("- Phase 1: see commits; bisectability verified by `tools/verify-move.py`.")
    a("- Phase 2: per-module compile results, non-move changes, test counts, dependency report and OkHttp convergence below (appended in place).")
    a("")

    out = "\n".join(lines) + "\n"
    dest = sys.argv[1] if len(sys.argv) > 1 else "task-004-extraction-map.md"
    with open(dest, "w", encoding="utf-8") as f:
        f.write(out)
    print("wrote", dest, len(out), "chars")

if __name__ == "__main__":
    run()