/*
 * EQO (TASK-008, issue #13): the five activation checks, in run order.
 *
 * Scope says "each check (pair, connect, helper start, authorize, binder health) is a
 * separate, individually failing step" — this enum IS that list, and the declaration
 * order is the run order. Each check has its own failure surface and its own recovery
 * guidance; passing one proves nothing about the others (see the task Notes: pairing,
 * helper authorization, accessibility and CDP consent are separate auth planes).
 */
package ai.eqo.adb.pairing

enum class ActivationCheck {
    /** SPAKE2 + AES-GCM pairing against the port from the "Pair device with pairing code" dialog. */
    PAIR,

    /** mTLS adb session against the connection port from the "Wireless debugging" screen. */
    CONNECT,

    /** The EQO privileged helper process starts. */
    HELPER_START,

    /** The owner authorizes EQO against the running helper. */
    AUTHORIZE,

    /** The helper binder is alive and answers. */
    BINDER_HEALTH,
}
