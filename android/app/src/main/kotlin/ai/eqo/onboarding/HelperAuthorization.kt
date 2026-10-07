package ai.eqo.onboarding

import ai.eqo.adb.pairing.StepSignal
import ai.eqo.adb.pairing.StepSignalException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal interface HelperPermissionPort {
    fun isGranted(): Boolean

    fun request(result: (Boolean) -> Unit)

    fun close(authorized: Boolean = false)
}

/** Bounded worker-thread wait. A missing answer, denial or interruption never authorizes. */
internal class HelperAuthorization(
    private val port: HelperPermissionPort,
    private val timeoutMs: Long = 55_000L,
) {
    fun await() {
        if (port.isGranted()) {
            port.close(authorized = true)
            return
        }
        val answer = AtomicReference<PermissionAnswer?>(null)
        val done = CountDownLatch(1)
        var authorized = false
        try {
            port.request { granted ->
                val decision = if (granted) PermissionAnswer.GRANTED else PermissionAnswer.DENIED
                if (answer.compareAndSet(null, decision)) done.countDown()
            }
            if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) fail("helper authorization timed out")
            if (answer.get() != PermissionAnswer.GRANTED || !port.isGranted()) fail("helper authorization was denied")
            authorized = true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            fail("helper authorization was interrupted")
        } finally {
            port.close(authorized)
        }
    }

    private fun fail(detail: String): Nothing = throw StepSignalException(StepSignal.HELPER_NOT_AUTHORIZED, detail)

    private enum class PermissionAnswer { GRANTED, DENIED }
}
