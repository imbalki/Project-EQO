package ai.eqo.accessibility

/**
 * Explicit Phase-One runtime: no Application/Hilt or donor agent graph is required.
 * The service owns this holder; StudyLoopWiring obtains this same automation via
 * getInstance().automation. The shared takeover latch is also used by the study loop.
 * All Android/window dependencies are supplied, with no permissive defaults.
 */
class EqoServiceRuntime(
    rootProvider: () -> A11yNode?,
    serviceState: () -> EqoAutomation.ServiceState,
    isSecureWindow: () -> Boolean,
    val takeover: TakeoverDetector = sharedTakeover,
) {
    val automation = EqoAutomation(rootProvider, serviceState, takeover, isSecureWindow)

    companion object {
        val sharedTakeover: TakeoverDetector get() = TakeoverDetector.shared
    }
}
