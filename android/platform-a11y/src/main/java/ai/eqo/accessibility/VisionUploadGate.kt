/*
 * EQO (t_cfbe3719): fail-closed gate for the vision image-upload path.
 *
 * The vision fallback may send one screen image to the user's AI provider, so it
 * must never upload a protected (FLAG_SECURE) screen or one whose protection it
 * cannot verify. Android's public SDK exposes no trustworthy secure-window API,
 * so "unknown" is treated as protected: unavailable or throwing platform
 * metadata is NOT permission to upload. Only a window the platform positively
 * reports as non-secure may be captured and sent.
 *
 * Kept platform-free so the security decision is unit-tested without a device.
 */
package ai.eqo.accessibility

/** Trustworthy secure-window status of one visible window for the outbound image path. */
enum class WindowSecurity {
    /** The platform positively reports the window as secure (FLAG_SECURE). */
    SECURE,

    /** The platform positively reports the window as not secure. */
    NOT_SECURE,

    /** The platform could not be asked, or the probe threw: the status is unknown. */
    UNVERIFIABLE,
}

/**
 * Fail-closed decision for the one-image vision upload: a screen whose protection
 * cannot be verified is refused, never silently downgraded to "probably safe".
 */
object VisionUploadGate {
    const val PROTECTED_REASON = "This is a protected screen. Nothing will be sent."
    const val UNVERIFIABLE_REASON =
        "EQO cannot verify that this screen is not protected, so nothing will be sent."

    /**
     * The refusal to report for this set of visible windows, or null when every
     * visible window was positively verified non-secure. A secure window wins
     * over an unverifiable one; an empty window list cannot be verified either.
     */
    fun refusal(statuses: List<WindowSecurity>): String? =
        when {
            statuses.isEmpty() -> UNVERIFIABLE_REASON
            statuses.any { it == WindowSecurity.SECURE } -> PROTECTED_REASON
            statuses.any { it == WindowSecurity.UNVERIFIABLE } -> UNVERIFIABLE_REASON
            else -> null
        }

    /**
     * Classifies one window from a probe that returns its secure flag. A probe
     * that cannot answer (missing/blocked hidden API, OEM variation, or any
     * platform error) throws and is reported as [WindowSecurity.UNVERIFIABLE] —
     * never as non-secure.
     */
    fun classify(probe: () -> Boolean): WindowSecurity =
        try {
            if (probe()) WindowSecurity.SECURE else WindowSecurity.NOT_SECURE
        } catch (_: Exception) {
            WindowSecurity.UNVERIFIABLE
        }
}
