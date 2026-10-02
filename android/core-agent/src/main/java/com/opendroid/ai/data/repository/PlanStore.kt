/*
 * ADAPTER (TASK-004 Phase 2) - minimal interface replacing the quarantined
 * upstream class com.opendroid.ai.data.repository.PlanRepository
 * (Room/PlanDao-backed, not in Phase One scope). Members are exactly those the
 * moved code calls. Implementation lands with the EQO app layer.
 */
package com.opendroid.ai.data.repository

import com.opendroid.ai.data.models.Plan

interface PlanStore {
    suspend fun getPlanById(planId: String): Plan?

    suspend fun savePlan(plan: Plan)
}
