// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/actions/CommunicationActions.kt
// Ported OpenDroid send flows keep their upstream step shape; cleanup tracked for a later pass.
@file:Suppress("ReturnCount", "LongMethod", "CyclomaticComplexMethod", "MagicNumber", "UnusedParameter")

package ai.eqo.actions.impl

import ai.eqo.actions.base.Action
import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.ContactResolution
import ai.eqo.core.agent.ContactResolver
import ai.eqo.core.agent.maskPhone
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URLEncoder

internal enum class EmailComposeOutcome {
    COMPOSED,
    VERIFIED_SENT,
    UNAVAILABLE,
}

internal fun interface EmailComposer {
    fun open(
        context: Context,
        to: String,
        subject: String,
        body: String,
    ): EmailComposeOutcome
}

private class AndroidEmailComposer(
    private val launcher: GatedIntentLauncher,
) : EmailComposer {
    override fun open(
        context: Context,
        to: String,
        subject: String,
        body: String,
    ): EmailComposeOutcome {
        val intent =
            Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:".toUri()
                putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        if (intent.resolveActivity(context.packageManager) == null) {
            return EmailComposeOutcome.UNAVAILABLE
        }
        launcher.open(intent)
        return EmailComposeOutcome.COMPOSED
    }
}

internal class CommunicationActions constructor(
    private val contactResolver: ContactResolver,
    private val callFlowExecutor: CallFlowExecutor,
    private val launcher: GatedIntentLauncher,
    private val locationSource: LocationSource = AndroidLocationSource(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** SHARE_CONTACT / SHARE_LOCATION reuse the WhatsApp, SMS and email routes below unchanged. */
    private val shareRoutes =
        object : ShareRoutes {
            override suspend fun whatsApp(
                phone: String,
                message: String,
            ): ActionResult = executeWhatsApp(phone, phone, message)

            override suspend fun sms(
                phone: String,
                message: String,
            ): ActionResult = executeSms(phone, phone, message)

            override suspend fun email(
                to: String,
                subject: String,
                body: String,
                context: Context,
            ): ActionResult =
                SendEmailAction(AndroidEmailComposer(launcher))
                    .execute(mapOf("to" to to, "subject" to subject, "body" to body), context)
        }

    fun getActions(): List<Action> =
        listOf(
            MakeCallAction(),
            SendWhatsAppAction(),
            SendTelegramAction(),
            OpenTelegramAction(),
            SendSmsAction(),
            SendEmailAction(AndroidEmailComposer(launcher)),
            SendWhatsAppGroupAction(),
            MakeVideoCallAction(),
            ReadMessagesAction(),
            ReadEmailsAction(),
        ) + ShareActions(contactResolver, locationSource, shareRoutes).getActions()

    companion object {
        /**
         * Legacy static resolve for backward compat.
         * Used by actions that don't yet support disambiguation.
         */
        private fun resolveContactToPhoneNumber(
            context: Context,
            contact: String,
        ): String {
            require(!contact.startsWith("\$")) { "Unresolved contact placeholder: $contact" }
            val cleaned = contact.replace(" ", "").replace("-", "")
            if (cleaned.startsWith("+") || (cleaned.isNotEmpty() && cleaned.all { it.isDigit() })) {
                return cleaned
            }
            val result = ContactResolver.resolve(context, contact)
            if (result is ContactResolver.ContactResult.Found) {
                return result.phoneNumber
            }
            throw IllegalArgumentException("Contact '$contact' not found in your contacts")
        }
    }

    /**
     * Build a NeedsInput with contact picker metadata.
     * Metadata stores match data as JSON so it survives serialization.
     */
    private fun buildContactPickerResult(
        contactQuery: String,
        matches: List<ai.eqo.core.agent.Contact>,
        action: String,
        extraMeta: Map<String, String> = emptyMap(),
    ): ActionResult.NeedsInput {
        val matchesJson =
            json.encodeToString(
                matches.map { mapOf("name" to it.name, "phone" to it.phoneNumber, "type" to it.type) },
            )
        return ActionResult.NeedsInput(
            question = "Which '$contactQuery' do you mean?",
            options =
                matches.mapIndexed { i, c ->
                    "${i + 1}. ${c.name} (${c.type}: ${maskPhone(c.phoneNumber)})"
                },
            metadata =
                mapOf(
                    "type" to "contact_picker",
                    "query" to contactQuery,
                    "action" to action,
                    "matches" to matchesJson,
                ) + extraMeta,
        )
    }

    // ── MAKE_CALL with disambiguation ────────────────────────

    private inner class MakeCallAction : Action {
        override val name: String = "MAKE_CALL"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val contact =
                params["contact"]
                    ?: params["number"]
                    ?: params["phone"]
                    ?: params["phoneNumber"]
                    ?: return ActionResult(false, null, "contact or number parameter missing")

            return when (val resolved = contactResolver.resolveWithDisambiguation(contact)) {
                is ContactResolution.Found -> executeCall(resolved.contact.phoneNumber, context)
                is ContactResolution.Ambiguous -> buildContactPickerResult(contact, resolved.matches, "MAKE_CALL")
                is ContactResolution.NotFound ->
                    ActionResult.NeedsInput(
                        question = "I couldn't find '$contact' in your contacts. What's their number?",
                        metadata = mapOf("param" to "contact"),
                    )
            }
        }
    }

    // ── SEND_WHATSAPP with disambiguation ────────────────────

    private inner class SendWhatsAppAction : Action {
        override val name: String = "SEND_WHATSAPP"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val contact = params["contact"] ?: return ActionResult(false, null, "contact is missing")
            val message = params["message"] ?: return ActionResult(false, null, "message is missing")

            return when (val resolved = contactResolver.resolveWithDisambiguation(contact)) {
                is ContactResolution.Found -> executeWhatsApp(resolved.contact.phoneNumber, contact, message)
                is ContactResolution.Ambiguous ->
                    buildContactPickerResult(
                        contact,
                        resolved.matches,
                        "SEND_WHATSAPP",
                        mapOf("message" to message),
                    )
                is ContactResolution.NotFound ->
                    ActionResult.NeedsInput(
                        question = "I couldn't find '$contact'. What's their WhatsApp number?",
                        metadata = mapOf("param" to "contact"),
                    )
            }
        }
    }

    // ── SEND_TELEGRAM with username / phone / disambiguation ───

    private inner class SendTelegramAction : Action {
        override val name: String = "SEND_TELEGRAM"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val contact =
                params["contact"]
                    ?: params["to"]
                    ?: params["recipient"]
                    ?: params["username"]
                    ?: return ActionResult(false, null, "contact is missing")
            val message =
                params["message"]
                    ?: params["text"]
                    ?: params["body"]
                    ?: return ActionResult(false, null, "message is missing")

            val trimmed = contact.trim()
            if (trimmed.startsWith("@") || (trimmed.isNotEmpty() && !trimmed.contains(" ") && !trimmed.all { it.isDigit() })) {
                // Direct Telegram username/handle
                return executeTelegram(trimmed.removePrefix("@"), contact, message, context, isUsername = true)
            }

            if (trimmed.startsWith("+") || (trimmed.isNotEmpty() && trimmed.all { it.isDigit() })) {
                // Direct phone number
                return executeTelegram(trimmed, contact, message, context, isUsername = false)
            }

            return when (val resolved = contactResolver.resolveWithDisambiguation(contact)) {
                is ContactResolution.Found -> executeTelegram(resolved.contact.phoneNumber, contact, message, context, isUsername = false)
                is ContactResolution.Ambiguous ->
                    buildContactPickerResult(
                        contact,
                        resolved.matches,
                        "SEND_TELEGRAM",
                        mapOf("message" to message),
                    )
                is ContactResolution.NotFound -> {
                    // If not found in device contacts, treat as Telegram username
                    executeTelegram(trimmed.removePrefix("@"), contact, message, context, isUsername = true)
                }
            }
        }
    }

    private inner class OpenTelegramAction : Action {
        override val name: String = "OPEN_TELEGRAM"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val contact = params["contact"] ?: params["username"] ?: params["channel"]
            return try {
                if (!contact.isNullOrBlank()) {
                    val domain = contact.trim().removePrefix("@")
                    val tgUri = "tg://resolve?domain=$domain".toUri()
                    val intent =
                        Intent(Intent.ACTION_VIEW, tgUri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        launcher.open(intent)
                        return ActionResult(true, "Opened Telegram chat with $contact!", null)
                    }
                    val webIntent =
                        Intent(Intent.ACTION_VIEW, "https://t.me/$domain".toUri()).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    launcher.open(webIntent)
                    return ActionResult(true, "Opened Telegram with $contact!", null)
                } else {
                    val pm = context.packageManager
                    val launchIntent =
                        pm.getLaunchIntentForPackage("org.telegram.messenger")
                            ?: pm.getLaunchIntentForPackage("org.telegram.messenger.web")
                            ?: pm.getLaunchIntentForPackage("org.telegram.plus")
                            ?: pm.getLaunchIntentForPackage("nekox.messenger")
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        launcher.open(launchIntent)
                        ActionResult(true, "Telegram is open!", null)
                    } else {
                        val webIntent =
                            Intent(Intent.ACTION_VIEW, "https://web.telegram.org".toUri()).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        launcher.open(webIntent)
                        ActionResult(true, "Opened Telegram Web in browser.", null)
                    }
                }
            } catch (e: Exception) {
                Log.e("OpenTelegram", "Failed to open Telegram: ${e.localizedMessage}")
                ActionResult(false, null, "Couldn't open Telegram right now.")
            }
        }
    }

    // ── SEND_SMS with disambiguation ─────────────────────────

    private inner class SendSmsAction : Action {
        override val name: String = "SEND_SMS"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val contact =
                params["contact"]
                    ?: params["to"]
                    ?: params["recipient"]
                    ?: return ActionResult(false, null, "contact parameter missing")
            val message =
                params["message"]
                    ?: params["text"]
                    ?: params["body"]
                    ?: return ActionResult(false, null, "message parameter missing")

            return when (val resolved = contactResolver.resolveWithDisambiguation(contact)) {
                is ContactResolution.Found -> executeSms(resolved.contact.phoneNumber, contact, message)
                is ContactResolution.Ambiguous ->
                    buildContactPickerResult(
                        contact,
                        resolved.matches,
                        "SEND_SMS",
                        mapOf("message" to message),
                    )
                is ContactResolution.NotFound ->
                    ActionResult.NeedsInput(
                        question = "I couldn't find '$contact'. What's their phone number?",
                        metadata = mapOf("param" to "contact"),
                    )
            }
        }
    }

    // ── Execution helpers ────────────────────────────────────

    private suspend fun executeCall(
        phone: String,
        context: Context,
    ): ActionResult = callFlowExecutor.execute(phone, context)

    private suspend fun executeWhatsApp(
        phone: String,
        contactLabel: String,
        message: String,
    ): ActionResult {
        return try {
            val encodedMsg = URLEncoder.encode(message, "UTF-8")
            val whatsappUri =
                if (phone.matches(Regex("\\+?[0-9]+"))) {
                    "https://api.whatsapp.com/send?phone=$phone&text=$encodedMsg".toUri()
                } else {
                    "whatsapp://send?text=$encodedMsg".toUri()
                }
            val intent =
                Intent(Intent.ACTION_VIEW, whatsappUri).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            launcher.open(intent)

            if (ai.eqo.accessibility.WhatsAppAutomator
                    .automateSend(message)
            ) {
                return ActionResult.Success(mapOf("message" to "WhatsApp Send pressed; delivery is not verified."))
            }
            return ActionResult.UserActionRequired(
                "WhatsApp draft opened, but EQO could not press Send. Nothing was verified as sent.",
            )
        } catch (e: Exception) {
            Log.e("SendWhatsApp", "WhatsApp failed: ${e.localizedMessage}")
            ActionResult(false, null, "WhatsApp didn't work. ${e.localizedMessage ?: "Please try again."}", true)
        }
    }

    private suspend fun executeTelegram(
        identifier: String,
        contactLabel: String,
        message: String,
        context: Context,
        isUsername: Boolean,
    ): ActionResult {
        return try {
            val encodedMsg = URLEncoder.encode(message, "UTF-8")
            val tgUri =
                if (isUsername) {
                    "tg://resolve?domain=$identifier&text=$encodedMsg".toUri()
                } else {
                    "tg://msg?to=$identifier&text=$encodedMsg".toUri()
                }

            val intent =
                Intent(Intent.ACTION_VIEW, tgUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

            val pm = context.packageManager
            val tgPackages = listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus", "nekox.messenger")
            val installedTgPkg =
                tgPackages.firstOrNull { pkg ->
                    try {
                        pm.getPackageInfo(pkg, 0)
                        true
                    } catch (e: Exception) {
                        false
                    }
                }

            if (installedTgPkg != null) {
                intent.setPackage(installedTgPkg)
            }

            if (intent.resolveActivity(pm) != null) {
                launcher.open(intent)
            } else {
                val fallbackUri =
                    if (isUsername) {
                        "https://t.me/$identifier".toUri()
                    } else {
                        "https://t.me/share/url?url=&text=$encodedMsg".toUri()
                    }
                val webIntent =
                    Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                launcher.open(webIntent)
            }

            kotlinx.coroutines.delay(2000)
            val currentPackage =
                ai.eqo.accessibility.EQOAccessibilityService
                    .getInstance()
                    ?.rootInActiveWindow
                    ?.packageName
                    ?.toString()
            if (intent.resolveActivity(pm) != null &&
                currentPackage == installedTgPkg &&
                ai.eqo.accessibility.TelegramAutomator
                    .automateSend(message)
            ) {
                return ActionResult.Success(mapOf("message" to "Telegram Send pressed; delivery is not verified."))
            }
            return ActionResult.UserActionRequired(
                "Telegram draft opened, but EQO could not press Send. Nothing was verified as sent.",
            )
        } catch (e: Exception) {
            Log.e("SendTelegram", "Telegram failed: ${e.localizedMessage}")
            ActionResult(false, null, "Telegram didn't work. ${e.localizedMessage ?: "Please try again."}", true)
        }
    }

    private suspend fun executeSms(
        phone: String,
        contactLabel: String,
        message: String,
    ): ActionResult {
        return try {
            val intent =
                Intent(Intent.ACTION_SENDTO).apply {
                    data = "smsto:$phone".toUri()
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            if (intent.resolveActivity(launcher.context.packageManager) == null) {
                return ActionResult.Failure("No messaging app is available to compose a text message.")
            }
            launcher.open(intent)
            kotlinx.coroutines.delay(1500)
            val current =
                ai.eqo.accessibility.EQOAccessibilityService
                    .getInstance()
                    ?.rootInActiveWindow
                    ?.packageName
                    ?.toString()
            val expected =
                android.provider.Telephony.Sms
                    .getDefaultSmsPackage(launcher.context)
            if (expected != null &&
                current == expected &&
                ai.eqo.accessibility.SmsAutomator
                    .automateSend()
            ) {
                ActionResult.Success(mapOf("message" to "SMS Send pressed; delivery is not verified."))
            } else {
                ActionResult.UserActionRequired(
                    "SMS draft opened, but EQO could not press Send. Nothing was verified as sent.",
                )
            }
        } catch (_: Exception) {
            ActionResult.Failure("Couldn't open the messaging app.")
        }
    }

    // ── Non-disambiguated actions (unchanged) ────────────────

    internal class SendEmailAction(
        private val emailComposer: EmailComposer,
    ) : Action {
        override val name: String = "SEND_EMAIL"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val to = params["to"] ?: return ActionResult(false, null, "to email is missing")
            val subject = params["subject"] ?: ""
            val body = params["body"] ?: ""
            return try {
                when (emailComposer.open(context, to, subject, body)) {
                    EmailComposeOutcome.VERIFIED_SENT ->
                        ActionResult(true, "Email sent successfully.", null)
                    EmailComposeOutcome.COMPOSED -> {
                        kotlinx.coroutines.delay(2000)
                        val service =
                            ai.eqo.accessibility.EQOAccessibilityService
                                .getInstance()
                        val inGmail = service?.rootInActiveWindow?.packageName?.toString() == "com.google.android.gm"
                        if (inGmail && service?.gatedActions?.findAndClick("Send")?.isSuccess == true) {
                            ActionResult.Success(mapOf("message" to "Gmail Send pressed; delivery is not verified."))
                        } else {
                            ActionResult.UserActionRequired(
                                "Email draft opened, but EQO could not press Send. Nothing was verified as sent.",
                            )
                        }
                    }
                    EmailComposeOutcome.UNAVAILABLE ->
                        ActionResult(false, null, "Couldn't open the email app. Is one installed?")
                }
            } catch (e: Exception) {
                Log.e("SendEmail", "Email compose launch failed")
                ActionResult(false, null, "Couldn't open the email app. Is one installed?")
            }
        }
    }

    private inner class SendWhatsAppGroupAction : Action {
        override val name: String = "SEND_WHATSAPP_GROUP"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val groupName = params["groupName"] ?: return ActionResult(false, null, "groupName parameter missing")
            val message = params["message"] ?: return ActionResult(false, null, "message parameter missing")
            return try {
                val intent =
                    Intent(Intent.ACTION_MAIN).apply {
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                launcher.open(intent)
                ActionResult.UserActionRequired(
                    "WhatsApp is open. Find the '$groupName' group, enter the message and tap Send; sending was not verified.",
                )
            } catch (e: Exception) {
                Log.e("WhatsAppGroup", "Group message failed: ${e.localizedMessage}")
                ActionResult(false, null, "Couldn't open WhatsApp. Is it installed?")
            }
        }
    }

    private inner class MakeVideoCallAction : Action {
        override val name: String = "MAKE_VIDEO_CALL"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val contact = params["contact"] ?: return ActionResult(false, null, "contact parameter missing")
            val phone = resolveContactToPhoneNumber(context, contact)
            val app = params["app"] ?: "whatsapp"
            return try {
                when (app.lowercase()) {
                    "whatsapp" -> {
                        val intent =
                            Intent(Intent.ACTION_VIEW, "https://api.whatsapp.com/send?phone=$phone".toUri()).apply {
                                setPackage("com.whatsapp")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        launcher.open(intent)
                        kotlinx.coroutines.delay(2000)
                        val service =
                            ai.eqo.accessibility.EQOAccessibilityService
                                .getInstance()
                        if (service?.rootInActiveWindow?.packageName?.toString() == "com.whatsapp" &&
                            service.gatedActions.findAndClick("Video call").isSuccess
                        ) {
                            return ActionResult.Success(
                                mapOf("message" to "WhatsApp video call button pressed; connection is not verified."),
                            )
                        }
                    }
                    else -> {
                        val pm = context.packageManager
                        val launchIntent =
                            pm.getLaunchIntentForPackage("com.google.android.apps.meetings")
                                ?: pm.getLaunchIntentForPackage("com.google.android.apps.tachyon")
                                ?: pm.getLaunchIntentForPackage("us.zoom.videomeetings")
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            launcher.open(launchIntent)
                        } else {
                            val dialIntent =
                                Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            // Telephony is optional — a device with no radio and no
                            // dialer app has nothing left to fall back to.
                            if (dialIntent.resolveActivity(context.packageManager) == null) {
                                return ActionResult(
                                    false,
                                    null,
                                    "No video call app is installed, and this device can't place phone calls. Try WhatsApp?",
                                )
                            }
                            launcher.open(dialIntent)
                        }
                    }
                }
                ActionResult.UserActionRequired("Calling app opened. Start the video call yourself; a call was not verified.")
            } catch (e: Exception) {
                Log.e("VideoCall", "Video call failed: ${e.localizedMessage}")
                ActionResult(false, null, "Couldn't start the video call. Try again?")
            }
        }
    }

    private inner class ReadMessagesAction : Action {
        override val name: String = "READ_MESSAGES"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val app = params["app"] ?: "sms"
            return try {
                val intent =
                    when (app.lowercase()) {
                        "whatsapp" -> context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                        "telegram" ->
                            context.packageManager.getLaunchIntentForPackage("org.telegram.messenger")
                                ?: context.packageManager.getLaunchIntentForPackage("org.telegram.messenger.web")
                                ?: context.packageManager.getLaunchIntentForPackage("org.telegram.plus")
                        else ->
                            Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_APP_MESSAGING)
                            }
                    }
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    launcher.open(intent)
                    ActionResult(true, "Here are your messages!", null)
                } else {
                    ActionResult(false, null, "Couldn't open the messaging app.")
                }
            } catch (e: Exception) {
                Log.e("ReadMessages", "Failed: ${e.localizedMessage}")
                ActionResult(false, null, "Couldn't open your messages right now.")
            }
        }
    }

    private inner class ReadEmailsAction : Action {
        override val name: String = "READ_EMAILS"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult =
            requireRegistryExecution() ?: try {
                val intent =
                    Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_APP_EMAIL)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                launcher.open(intent)
                ActionResult(true, "Your email is open!", null)
            } catch (e: Exception) {
                Log.e("ReadEmails", "Failed: ${e.localizedMessage}")
                ActionResult(false, null, "Couldn't open the email app.")
            }
    }
}
