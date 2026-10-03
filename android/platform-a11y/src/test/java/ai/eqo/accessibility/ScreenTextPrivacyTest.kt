/*
 * EQO (TASK-012, issue #17): security follow-up SF-2 — password fields and
 * secure windows are never read into screen text.
 */
package ai.eqo.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTextPrivacyTest {
    private fun tree(): FakeNode =
        FakeNode(text = "root")
            .child(FakeNode(text = "Visible balance: 42"))
            .child(
                FakeNode(text = "hunter2-should-never-appear", isPassword = true)
                    .child(FakeNode(text = "child-of-password-never-appear")),
            ).child(FakeNode(contentDescription = "Send"))

    @Test
    fun `screen text skips password nodes and their subtree`() {
        val text = NodeTreeSearch.screenText(tree())
        assertTrue(text.contains("Visible balance: 42"))
        assertTrue(text.contains("Send"))
        assertFalse("password text must not be read", text.contains("hunter2"))
        assertFalse("subtree of a password node must not be read", text.contains("child-of-password"))
    }

    @Test
    fun `observe refuses a secure window with a typed error and reads nothing`() {
        val automation =
            EqoAutomation(
                rootProvider = { tree() },
                serviceState = { EqoAutomation.ServiceState.AVAILABLE },
                takeover = TakeoverDetector(),
                isSecureWindow = { true },
            )
        val result = automation.observe()
        assertTrue(result is A11yResult.Failure)
        assertEquals(A11yError.SecureWindow, (result as A11yResult.Failure).error)
    }

    @Test
    fun `observe still reads a normal window`() {
        val automation =
            EqoAutomation(
                rootProvider = { tree() },
                serviceState = { EqoAutomation.ServiceState.AVAILABLE },
                takeover = TakeoverDetector(),
                isSecureWindow = { false },
            )
        val result = automation.observe()
        assertTrue(result is A11yResult.Success)
        assertFalse((result as A11yResult.Success).detail.contains("hunter2"))
    }
}
