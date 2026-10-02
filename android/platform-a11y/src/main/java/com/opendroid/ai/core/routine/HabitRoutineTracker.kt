/*
 * ADAPTER (TASK-004 Phase 2) - minimal interface replacing the quarantined
 * upstream class com.opendroid.ai.core.routine.HabitRoutineEngine (584 lines,
 * pulls Room habit/macro DAOs and the memory graph; not in Phase One scope).
 * Members are exactly those the moved code (OpenDroidAccessibilityService) calls.
 */
package com.opendroid.ai.core.routine

interface HabitRoutineTracker {
    fun recordAppOpen(
        packageName: String,
        appName: String? = null,
        metadata: Map<String, String> = emptyMap(),
    )
}
