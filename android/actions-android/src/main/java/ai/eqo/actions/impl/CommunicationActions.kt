// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/actions/CommunicationActions.kt
// Ported OpenDroid send flows keep their upstream step shape; cleanup tracked for a later pass.
@file:Suppress("ReturnCount", "LongMethod", "CyclomaticComplexMethod", "MagicNumber", "UnusedParameter")

package ai.eqo.actions.impl

import ai.eqo.actions.base.Action
import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.ContactResolution
import ai.eqo.core.agent.ContactResolver
import ai.eqo.core.agent.failureMessage
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.net.toUri
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

/** Opens an email draft that already carries files. Only Gmail is driven, because only Gmail's Send button is known. */
internal fun interface AttachmentEmailComposer {
    fun open(
        context: Context,
        to: String,
        subject: String,
        body: String,
        files: List<ShareFile>,
    ): EmailComposeOutcome
}

private const val GMAIL_PACKAGE = "com.google.android.gm"
private const val WHATSAPP_PACKAGE = "com.whatsapp"

private class AndroidAttachmentEmailComposer(
    private val launcher: GatedIntentLauncher,
) : AttachmentEmailComposer {
    override fun open(
        context: Context,
        to: String,
        subject: String,
        body: String,
        files: List<ShareFile>,
    ): EmailComposeOutcome {
        val intent =
            FileShareIntents.build(files, GMAIL_PACKAGE).apply {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
        if (intent.resolveActivity(context.packageManager) == null) return EmailComposeOutcome.UNAVAILABLE
        grantRead(context, GMAIL_PACKAGE, files)
        launcher.open(intent)
        return EmailComposeOutcome.COMPOSED
    }
}

/** Gives the receiving app a read grant for exactly these files; the share also carries the grant flag. */
private fun grantRead(
    context: Context,
    packageName: String,
    files: List<ShareFile>,
) {
    files.forEach {
        try {
            context.grantUriPermission(packageName, it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: RuntimeException) {
            Log.w("EqoShare", "Could not pre-grant a file; the share flag still applies")
        }
    }
}

private const val NO_ATTACHMENTS = "Attaching files is not available in this build."
private const val MIN_JID_DIGITS = 7
private const val SEND_SETTLE_MS = 1500L

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
<<<<<<< HEAD
    private val locationSource: LocationSource = AndroidLocationSource(),
    private val automation: () -> ai.eqo.accessibility.EqoAutomation? = { null },
=======
>>>>>>> 1190536 (fix(android): keep CommunicationActions' automation lambda as the last parameter)
    private val attachments: AttachmentShare? = null,
    // Keep last: callers and tests pass it as a trailing lambda.
    private val automation: () -> ai.eqo.accessibility.EqoAutomation? = { null },
) {
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
                SendEmailAction(AndroidEmailComposer(launcher), contactResolver)
                    .execute(mapOf("to" to to, "subject" to subject, "body" to body), context)
        }

    fun getActions(): List<Action> =
        listOf(
            MakeCallAction(),
            SendWhatsAppAction(),
            WhatsAppCallAction(),
            SendTelegramAction(),
            OpenTelegramAction(),
            SendSmsAction(),
            SendEmailAction(
                AndroidEmailComposer(launcher),
                contactResolver,
                attachments,
                AndroidAttachmentEmailComposer(launcher),
            ),
            SendWhatsAppGroupAction(),
            MakeVideoCallAction(),
            ReadMessagesAction(),
            ReadEmailsAction(),
        ) + ShareActions(contactResolver, locationSource, shareRoutes).getActions()

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
                else -> ActionResult.Failure(resolved.failureMessage())
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
                is ContactResolution.Found ->
                    executeWhatsApp(resolved.contact.phoneNumber, contact, message, params["attachment"])
                else -> ActionResult.Failure(resolved.failureMessage())
            }
        }
    }

    private inner class WhatsAppCallAction : Action {
        override val name: String = "WHATSAPP_CALL"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val contact = params["contact"] ?: return ActionResult.Failure("contact is missing")
            val video =
                params["video"]?.toBooleanStrictOrNull()
                    ?: return ActionResult.Failure("video must be true or false")
            return when (val resolved = contactResolver.resolveWithDisambiguation(contact)) {
                is ContactResolution.Found ->
                    WhatsAppCallFlow(launcher, automation).execute(resolved.contact.phoneNumber, video)
                // Several matches, no match or no Contacts permission: say so plainly, never guess a person.
                else -> ActionResult.Failure(resolved.failureMessage())
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
            if (trimmed.startsWith("@")) {
                return executeTelegram(trimmed.removePrefix("@"), contact, message, context, isUsername = true)
            }

            return when (val resolved = contactResolver.resolveWithDisambiguation(contact)) {
                is ContactResolution.Found -> executeTelegram(resolved.contact.phoneNumber, contact, message, context, isUsername = false)
                else -> ActionResult.Failure(resolved.failureMessage())
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
                Log.e("OpenTelegram", "Failed to open Telegram")
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
                is ContactResolution.Found ->
                    executeSms(resolved.contact.phoneNumber, contact, message, params["attachment"])
                else -> ActionResult.Failure(resolved.failureMessage())
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
        attachment: String? = null,
    ): ActionResult {
        if (!attachment.isNullOrBlank()) return executeWhatsAppWithFiles(phone, message, attachment)
        return try {
            launcher.openWhatsAppChat(phone, message)

            if (ai.eqo.accessibility.WhatsAppAutomator
                    .automateSend(message)
            ) {
                return ActionResult.Success(mapOf("message" to "WhatsApp Send pressed; delivery is not verified."))
            }
            return ActionResult.UserActionRequired(
                "WhatsApp draft opened, but EQO could not press Send. Nothing was verified as sent.",
            )
        } catch (e: Exception) {
            Log.e("SendWhatsApp", "WhatsApp failed")
            ActionResult(false, null, "WhatsApp could not be opened. No send was verified.", true)
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
            Log.e("SendTelegram", "Telegram failed")
            ActionResult(false, null, "Telegram could not be opened. No send was verified.", true)
        }
    }

    private suspend fun executeSms(
        phone: String,
        contactLabel: String,
        message: String,
        attachment: String? = null,
    ): ActionResult {
        if (!attachment.isNullOrBlank()) return executeSmsWithFiles(phone, message, attachment)
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

    // ── Sends that carry files ───────────────────────────────

    private suspend fun executeWhatsAppWithFiles(
        phone: String,
        message: String,
        attachment: String,
    ): ActionResult {
        val share = attachments ?: return ActionResult.Failure(NO_ATTACHMENTS)
        val ready =
            when (val prepared = share.prepareOnIo(attachment)) {
                is PreparedShare.Refused -> return ActionResult.Failure(prepared.message)
                is PreparedShare.Ready -> prepared
            }
        return try {
            val digits = phone.filter { it.isDigit() }
            val intent =
                FileShareIntents.build(ready.files, WHATSAPP_PACKAGE).apply {
                    putExtra(Intent.EXTRA_TEXT, message)
                    // Opens the chat with this number directly; without it WhatsApp asks which chat to use.
                    if (digits.length >= MIN_JID_DIGITS) putExtra("jid", "$digits@s.whatsapp.net")
                }
            grantRead(launcher.context, WHATSAPP_PACKAGE, ready.files)
            launcher.open(intent)
            if (ai.eqo.accessibility.WhatsAppAutomator
                    .automateSendSharedFiles()
            ) {
                ActionResult.Success(
                    mapOf("message" to "WhatsApp Send pressed with ${ready.files.size} file(s); not verified."),
                )
            } else {
                ActionResult.UserActionRequired(
                    "WhatsApp opened with the file, but EQO could not press Send. Nothing was verified as sent.",
                )
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            share.discard(ready.files)
            Log.e("SendWhatsApp", "WhatsApp file share failed")
            ActionResult.Failure("WhatsApp didn't open with the file. Is WhatsApp installed?")
        }
    }

    private suspend fun executeSmsWithFiles(
        phone: String,
        message: String,
        attachment: String,
    ): ActionResult {
        val share = attachments ?: return ActionResult.Failure(NO_ATTACHMENTS)
        val smsApp =
            Telephony.Sms.getDefaultSmsPackage(launcher.context)
                ?: return ActionResult.Failure("No messaging app is available to compose a text message.")
        val ready =
            when (val prepared = share.prepareOnIo(attachment)) {
                is PreparedShare.Refused -> return ActionResult.Failure(prepared.message)
                is PreparedShare.Ready -> prepared
            }
        return try {
            val intent =
                FileShareIntents.build(ready.files, smsApp).apply {
                    putExtra("address", phone)
                    putExtra("sms_body", message)
                    putExtra(Intent.EXTRA_TEXT, message)
                }
            grantRead(launcher.context, smsApp, ready.files)
            launcher.open(intent)
            kotlinx.coroutines.delay(SEND_SETTLE_MS)
            val current =
                ai.eqo.accessibility.EQOAccessibilityService
                    .getInstance()
                    ?.rootInActiveWindow
                    ?.packageName
                    ?.toString()
            if (current == smsApp &&
                ai.eqo.accessibility.SmsAutomator
                    .automateSend()
            ) {
                ActionResult.Success(
                    mapOf("message" to "Messages Send pressed with ${ready.files.size} file(s); not verified."),
                )
            } else {
                ActionResult.UserActionRequired(
                    "The message opened with the file, but EQO could not press Send. Nothing was verified as sent.",
                )
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            share.discard(ready.files)
            ActionResult.Failure("Couldn't open the messaging app with the file.")
        }
    }

    // ── Non-disambiguated actions (unchanged) ────────────────

    internal class SendEmailAction(
        private val emailComposer: EmailComposer,
        private val contactResolver: ContactResolver,
        private val attachments: AttachmentShare? = null,
        private val attachmentComposer: AttachmentEmailComposer? = null,
    ) : Action {
        override val name: String = "SEND_EMAIL"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val input = params["to"] ?: return ActionResult(false, null, "to email is missing")
            val resolved = contactResolver.resolveEmailWithDisambiguation(input)
            if (resolved !is ContactResolution.Found) return ActionResult.Failure(resolved.failureMessage())
            val to = resolved.contact.phoneNumber
            val subject = params["subject"] ?: ""
            val body = params["body"] ?: ""
            val attachment = params["attachment"]
            val composer = attachmentComposer
            val shared =
                if (attachment.isNullOrBlank()) {
                    null
                } else {
                    val share = attachments
                    if (share == null || composer == null) return ActionResult.Failure(NO_ATTACHMENTS)
                    when (val prepared = share.prepareOnIo(attachment)) {
                        is PreparedShare.Refused -> return ActionResult.Failure(prepared.message)
                        is PreparedShare.Ready -> prepared
                    }
                }
            return try {
                val outcome =
                    if (shared != null && composer != null) {
                        composer.open(context, to, subject, body, shared.files)
                    } else {
                        emailComposer.open(context, to, subject, body)
                    }
                if (shared != null && outcome != EmailComposeOutcome.COMPOSED) attachments?.discard(shared.files)
                when (outcome) {
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
                if (shared != null) attachments?.discard(shared.files)
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
                Log.e("WhatsAppGroup", "Group message failed")
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
            val resolved = contactResolver.resolveWithDisambiguation(contact)
            if (resolved !is ContactResolution.Found) return ActionResult.Failure(resolved.failureMessage())
            val phone = resolved.contact.phoneNumber
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
                Log.e("VideoCall", "Video call failed")
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
                Log.e("ReadMessages", "Failed")
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
                Log.e("ReadEmails", "Failed")
                ActionResult(false, null, "Couldn't open the email app.")
            }
    }
}
