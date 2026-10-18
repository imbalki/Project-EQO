// Origin: EQO recovery regressions; synthetic destinations assembled in memory only.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.actions.impl.RecipientPreparationException
import ai.eqo.core.agent.Contact
import ai.eqo.core.agent.ContactResolution
import ai.eqo.core.agent.ContactResolver
import ai.eqo.core.agent.TaskIntent
import ai.eqo.core.agent.isLiteralEmail
import ai.eqo.core.agent.isLiteralPhone
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@OptIn(ExperimentalCoroutinesApi::class)
class RecipientRecoveryTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val address = "synthetic" + '@' + "example.invalid"
    private val phone = "+" + "1".repeat(11)
    private val memory get() = RememberedRecipients(context)
    private val contacts =
        object : ContactResolver(context) {
            override suspend fun resolveEmailWithDisambiguation(input: String): ContactResolution =
                when {
                    isLiteralEmail(input) -> ContactResolution.Found(Contact(input, input))
                    input == "Known" -> ContactResolution.Found(Contact(input, address))
                    input.isEmpty() -> ContactResolution.Ambiguous(input, listOf(Contact("Other", address)))
                    else -> ContactResolution.NotFound(input)
                }

            override suspend fun resolveWithDisambiguation(input: String): ContactResolution =
                if (isLiteralPhone(input)) {
                    ContactResolution.Found(Contact(input, input))
                } else {
                    ContactResolution.NotFound(input)
                }
        }

    @Before fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        memory.entries().keys.forEach(memory::delete)
    }

    @After fun teardown() {
        memory.entries().keys.forEach(memory::delete)
        Dispatchers.resetMain()
    }

    @Test fun knownAndLiteralRecipientsNeverAsk(): Unit =
        runBlocking {
            val recovery = recovery { error("No choice needed") }
            for (recipient in listOf("Known", address)) {
                val found = recovery.resolveEmailWithDisambiguation(recipient) as ContactResolution.Found
                assertEquals(address, found.contact.phoneNumber)
            }
        }

    @Test fun unknownTypedAddressIsUsedOnlyThisTime(): Unit =
        runBlocking {
            val answers = ArrayDeque(listOf(context.getString(R.string.recipient_type_now), address, once()))
            val questions = mutableListOf<TaskIntent>()
            val result =
                recovery {
                    questions += it
                    answers.removeFirst()
                }.resolveEmailWithDisambiguation("Unknown")
            assertEquals(address, (result as ContactResolution.Found).contact.phoneNumber)
            assertTrue(questions.first().question!!.contains("email address for Unknown"))
            assertNull(memory.destination("Unknown", true))
            assertTrue(answers.isEmpty())
        }

    @Test fun alwaysRemembersAcrossInstancesAndCanBeCleared(): Unit =
        runBlocking {
            val answers = ArrayDeque(listOf(address, context.getString(R.string.app_chooser_always)))
            recovery { answers.removeFirst() }.resolveEmailWithDisambiguation("Unknown")
            assertEquals(address, RememberedRecipients(context).destination(" UNKNOWN ", true))
            assertNull(memory.destination("Unknown", false))
            val result = recovery { error("Remembered answer must not ask") }.resolveEmailWithDisambiguation("Unknown")
            assertEquals(address, (result as ContactResolution.Found).contact.phoneNumber)
            memory.entries().keys.forEach(memory::delete)
            assertTrue(memory.entries().isEmpty())
        }

    @Test fun phoneRecoveryUsesSeparateChannelAndNeverInventsNumber(): Unit =
        runBlocking {
            val answers = ArrayDeque(listOf(phone, once()))
            val result = recovery { answers.removeFirst() }.resolveWithDisambiguation("Unknown")
            assertEquals(phone, (result as ContactResolution.Found).contact.phoneNumber)
            assertNull(memory.destination("Unknown", false))
        }

    @Test fun pickAnotherContactUsesOwnersExplicitSelection(): Unit =
        runBlocking {
            val pick = context.getString(R.string.recipient_pick_contact)
            val answers = ArrayDeque(listOf(pick, "Other: $address", once()))
            val result = recovery { answers.removeFirst() }.resolveEmailWithDisambiguation("Unknown")
            assertEquals(address, (result as ContactResolution.Found).contact.phoneNumber)
            assertTrue(answers.isEmpty())
        }

    @Test fun cancelAndInvalidDestinationsDoNotResolve(): Unit =
        runBlocking {
            for (answers in listOf(ArrayDeque<String>(), ArrayDeque(listOf("Type", "not valid", "still invalid")))) {
                try {
                    recovery { answers.removeFirstOrNull() }.resolveEmailWithDisambiguation("Unknown")
                    error("Must not resolve")
                } catch (expected: RecipientPreparationException) {
                    assertTrue(expected.message!!.contains("Nothing ran"))
                }
            }
            assertTrue(memory.entries().isEmpty())
        }

    private fun once() = context.getString(R.string.app_chooser_this_time)

    private fun recovery(ask: suspend (TaskIntent) -> String?) = TaskRecipientRecovery(context, ask, memory, contacts)
}
