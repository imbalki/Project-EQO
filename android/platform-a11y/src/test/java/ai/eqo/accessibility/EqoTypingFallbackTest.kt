package ai.eqo.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqoTypingFallbackTest {
    private val takeover = TakeoverDetector()
    private var state = EqoAutomation.ServiceState.AVAILABLE

    private fun automation(f: A11yNode): EqoAutomation = EqoAutomation({ FakeNode().child(f) }, { state }, takeover)

    @Test
    fun focusedTargetUsesOnlyFocusedNativeInput() {
        val field = FakeNode(className = "android.widget.EditText", isFocused = true)
        assertTrue(automation(field).type("focused", "hello").isSuccess)
        assertEquals("hello", field.typedValue)
        val unfocused = FakeNode(className = "android.widget.EditText")
        assertTrue(automation(unfocused).type("focused", "hello") is A11yResult.Failure)
        assertNull(unfocused.typedValue)
    }

    @Test
    fun mismatchedHintTypesIntoTheOnlyInputOnScreen() {
        val field = FakeNode(className = "android.widget.EditText", hintText = "Search Keep")
        assertTrue(automation(field).type("Search your notes", "hello").isSuccess)
        assertEquals("hello", field.typedValue)
    }

    @Test
    fun focusedInputHoldsReportsWhetherTypingLanded() {
        val empty = FakeNode(className = "android.widget.EditText", isFocused = true)
        assertTrue(automation(empty).hasFocusedInput())
        assertEquals(false, automation(empty).focusedInputHolds("hello"))
        val filled = FakeNode(className = "android.widget.EditText", isFocused = true, text = "say Hello there")
        assertTrue(automation(filled).focusedInputHolds("hello"))
    }

    @Test
    fun passwordFieldsAreNeverTypedInto() {
        val secret = FakeNode(className = "android.widget.EditText", isPassword = true)
        assertTrue(automation(secret).type("Password", "hello") is A11yResult.Failure)
        assertTrue(automation(secret).type("anything", "hello") is A11yResult.Failure)
        assertNull(secret.typedValue)
    }

    @Test
    fun mismatchedHintWithTwoInputsDoesNotGuess() {
        val a = FakeNode(className = "android.widget.EditText", hintText = "To")
        val b = FakeNode(className = "android.widget.EditText", hintText = "Subject")
        val root = FakeNode().child(a).child(b)
        val auto = EqoAutomation({ root }, { state }, takeover)
        assertTrue(auto.type("Search", "hello") is A11yResult.Failure)
        assertNull(a.typedValue)
        assertNull(b.typedValue)
    }

    @Test
    fun focusesAndClicksBeforeSetTextWithoutPastingOnSuccess() {
        val field = FakeNode(isEditable = true, isClickable = true, hintText = "Message")
        assertTrue(automation(field).type("Message", "hello").isSuccess)
        assertEquals(listOf("focus", "click", "setText"), field.actions)
        assertEquals("hello", field.typedValue)
    }

    @Test
    fun rejectedSetTextFallsBackToPasteOnce() {
        val field = FakeNode(isEditable = true, hintText = "Message").apply { rejectSetText = true }
        assertTrue(automation(field).type("Message", "hello").isSuccess)
        assertEquals(listOf("focus", "setText", "paste"), field.actions)
        assertEquals("hello", field.typedValue)
    }

    @Test
    fun bothInputMethodsRejectedReturnActionRejected() {
        val field =
            FakeNode(isEditable = true, hintText = "Message").apply {
                rejectSetText = true
                rejectPaste = true
            }
        val result = automation(field).type("Message", "hello") as A11yResult.Failure
        assertEquals(A11yError.ActionRejected("Message"), result.error)
        assertNull(field.typedValue)
    }

    @Test
    fun nativeWhatsAppEditTextWorksWithoutEditableFlag() {
        val field = FakeNode(viewIdResourceName = "com.whatsapp:id/entry", className = "android.widget.EditText")
        assertTrue(automation(field).typeById("com.whatsapp:id/entry", "hello").isSuccess)
        assertEquals("hello", field.typedValue)
    }

    @Test
    fun gmailNativeRecipientSubjectAndBodyTargetsWork() {
        val recipient =
            FakeNode(
                viewIdResourceName = "com.google.android.gm:id/to",
                className = "android.widget.MultiAutoCompleteTextView",
            )
        val subject =
            FakeNode(
                viewIdResourceName = "com.google.android.gm:id/subject",
                className = "android.widget.EditText",
                hintText = "Subject",
            )
        val body =
            FakeNode(
                viewIdResourceName = "com.google.android.gm:id/body",
                className = "android.widget.EditText",
                contentDescription = "Compose email",
            )
        val root = FakeNode().child(recipient).child(subject).child(body)
        val automation = EqoAutomation({ root }, { state }, takeover)
        assertTrue(automation.typeById("com.google.android.gm:id/to", "owner@example.invalid").isSuccess)
        assertTrue(automation.type("Subject", "subject").isSuccess)
        assertTrue(automation.type("Compose email", "body").isSuccess)
        assertEquals("owner@example.invalid", recipient.typedValue)
        assertEquals("subject", subject.typedValue)
        assertEquals("body", body.typedValue)
    }

    @Test
    fun descriptionAndHintCanMatchEvenWithExistingText() {
        val field = FakeNode(text = "old draft", hintText = "Subject", isEditable = true)
        assertTrue(automation(field).type("Subject", "replacement").isSuccess)
        assertEquals("replacement", field.typedValue)
    }

    @Test
    fun takeoverDuringFocusPreventsClickAndTyping() {
        val field =
            FakeNode(isEditable = true, isClickable = true, hintText = "Message").apply {
                onAction = { if (it == "focus") takeover.onTouch(TakeoverDetector.TouchSource.USER) }
            }
        val result = automation(field).type("Message", "hello") as A11yResult.Failure
        assertEquals(A11yError.TakeoverDetected, result.error)
        assertEquals(listOf("focus"), field.actions)
        assertNull(field.typedValue)
    }

    @Test
    fun takeoverDuringSetTextRejectionPreventsPaste() {
        val field =
            FakeNode(isEditable = true, hintText = "Message").apply {
                rejectSetText = true
                onAction = { if (it == "setText") takeover.onTouch(TakeoverDetector.TouchSource.USER) }
            }
        val result = automation(field).type("Message", "hello") as A11yResult.Failure
        assertEquals(A11yError.TakeoverDetected, result.error)
        assertEquals(listOf("focus", "setText"), field.actions)
        assertNull(field.typedValue)
    }

    @Test
    fun takeoverDuringPastePreparationStopsTheFallback() {
        val field =
            FakeNode(isEditable = true, hintText = "Message").apply {
                rejectSetText = true
                onAction = { if (it == "paste") takeover.onTouch(TakeoverDetector.TouchSource.USER) }
            }
        val result = automation(field).type("Message", "hello") as A11yResult.Failure
        assertEquals(A11yError.TakeoverDetected, result.error)
        assertEquals(listOf("focus", "setText", "paste"), field.actions)
        assertNull(field.typedValue)
    }

    @Test
    fun serviceRevokedDuringFocusStopsImmediately() {
        val field =
            FakeNode(isEditable = true, hintText = "Message").apply {
                onAction = { state = EqoAutomation.ServiceState.ACCESSIBILITY_DISABLED }
            }
        val result = automation(field).type("Message", "hello") as A11yResult.Failure
        assertEquals(A11yError.AccessibilityDisabled, result.error)
        assertEquals(listOf("focus"), field.actions)
    }

    @Test
    fun missingTargetDoesNotFocusOrPaste() {
        val field = FakeNode(isEditable = true, hintText = "Message")
        val other = FakeNode(isEditable = true, hintText = "To")
        val root = FakeNode().child(field).child(other)
        val result = EqoAutomation({ root }, { state }, takeover).type("Subject", "hello") as A11yResult.Failure
        assertEquals(A11yError.NodeNotFound("Subject"), result.error)
        assertTrue(field.actions.isEmpty())
    }
}
