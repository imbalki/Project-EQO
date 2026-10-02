/*
 * ADAPTER (TASK-004 Phase 2) - marker interface replacing the quarantined
 * upstream class com.opendroid.ai.core.memory.NotificationIntelligence
 * (206 lines, pulls Room-backed MemoryRepository; not in Phase One scope).
 * The moved code (AutoReplyEngine) carries it as a constructor parameter for
 * wiring parity but calls no members on it; the interface therefore declares
 * none. Grow it when an EQO implementation needs behavior from it.
 */
package ai.eqo.core.memory

interface NotificationStore
