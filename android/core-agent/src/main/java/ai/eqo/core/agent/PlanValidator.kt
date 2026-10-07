// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/core/agent/PlanValidator.kt
package ai.eqo.core.agent

import ai.eqo.actions.ActionDispatcher
import ai.eqo.data.db.dao.UnknownActionDao
import ai.eqo.data.db.entities.UnknownActionEntity
import ai.eqo.data.models.Plan
import ai.eqo.data.models.PlanStep
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlanValidator
    @Inject
    constructor(
        private val actionDispatcher: dagger.Lazy<ActionDispatcher>,
        private val unknownActionDao: dagger.Lazy<UnknownActionDao>,
    ) {
        companion object {
            /** Closed study schema, independent of the broad donor action registry. */
            val STUDY_PARAMS =
                mapOf(
                    "observe" to emptySet(),
                    "scroll" to setOf("direction"),
                    "open_app" to setOf("app"),
                    "tap_text" to setOf("text"),
                    "type_text" to setOf("target", "text"),
                    "paste" to setOf("target", "text"),
                    "press_back" to emptySet(),
                    "press_home" to emptySet(),
                    "press_enter" to emptySet(),
                    "send_whatsapp" to setOf("body"),
                    "send_telegram" to setOf("body"),
                    "compose_sms" to setOf("to", "body"),
                    "compose_email" to setOf("to", "subject", "body"),
                )

            private const val MAX_STUDY_STEPS = 20
            private const val MAX_STUDY_TEXT = 8000

            fun validateRegistrySteps(
                steps: List<LoopStep>,
                enabled: Set<String>,
            ): List<String> = RegistryPlanVocabulary.errors(steps, enabled)

            fun validateStudySteps(steps: List<LoopStep>): List<String> {
                val errors = mutableListOf<String>()
                if (steps.isEmpty() || steps.size > MAX_STUDY_STEPS) errors.add("Plan must have 1 to 20 steps")
                if (steps.map { it.stepId }.distinct().size != steps.size) errors.add("Duplicate steps")
                steps.forEach { errors.addAll(studyStepErrors(it)) }
                return errors
            }

            private fun studyStepErrors(step: LoopStep): List<String> {
                val errors = mutableListOf<String>()
                val action = step.action
                val keys = STUDY_PARAMS[action.name]
                if (keys == null || action.params.keys != keys) errors.add("Unsupported action or parameters")
                if (action.params.values.any { unsupportedStudyText(it) }) {
                    errors.add("Unsupported text")
                }
                if (action.name == "scroll" && action.params["direction"] !in setOf("up", "down")) {
                    errors.add("Invalid direction")
                }
                if (action.name == "compose_sms" && !action.params["to"].orEmpty().matches(Regex("[+0-9 ()-]*"))) {
                    errors.add("Type a phone number, not a contact name")
                }
                if (action.name == "compose_email" &&
                    !action.params["to"].orEmpty().matches(EMAIL_RECIPIENT)
                ) {
                    errors.add("Type an email address, not a contact name")
                }
                if (action.name in setOf("open_app", "tap_text", "type_text", "paste") &&
                    action.params.values.any { it.isBlank() }
                ) {
                    errors.add("A target and text are required")
                }
                if (step.requiredPermission != null) errors.add("Model cannot select permissions")
                return errors
            }

            private val EMAIL_RECIPIENT = Regex("|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

            private fun unsupportedStudyText(value: String): Boolean =
                value.length > MAX_STUDY_TEXT || value.any { unsupportedStudyControl(it) }

            private fun unsupportedStudyControl(char: Char): Boolean = char.isISOControl() && char !in setOf('\n', '\t')

            private val DATA_PRODUCING_ACTIONS =
                setOf(
                    "GET_DIRECTIONS",
                    "GET_WEATHER",
                    "GET_NEWS",
                    "CALCULATE",
                    "CURRENCY_CONVERT",
                    "TRANSLATE",
                    "WEB_SEARCH",
                    "SUMMARIZE_URL",
                    "CHECK_STOCK",
                    "DEFINE_WORD",
                    "CONVERT_UNITS",
                    "FACT_CHECK",
                    "GET_SYSTEM_INFO",
                    "CHECK_TRAFFIC",
                    "CHECK_FLIGHT",
                    "TRACK_DELIVERY",
                    "CHECK_BALANCE",
                    "LIST_CALENDAR_TODAY",
                    "LIST_CALENDAR_WEEK",
                    "READ_MESSAGES",
                    "READ_EMAILS",
                    "READ_NOTES",
                    "READ_FILE",
                    "LIST_FILES",
                    "GET_SCREEN_TEXT",
                    "LIST_INSTALLED_APPS",
                    "ASK_USER",
                    "SPLIT_BILL",
                    "ANALYZE_SCREENSHOT",
                    "READ_AND_REMEMBER_SCREEN",
                    "RECALL_MEMORY",
                    "QUERY_KNOWLEDGE_GRAPH",
                )
        }

        fun validatePlan(plan: Plan): List<String> {
            val errors = mutableListOf<String>()
            for (step in plan.steps) {
                val err = validateStep(step)
                if (err != null) errors.add(err)
            }
            return errors
        }

        fun validateStep(step: PlanStep): String? {
            if (!actionDispatcher.get().isRegistered(step.action)) {
                return "Action '${step.action}' is not registered."
            }
            return null
        }

        suspend fun validateAndFix(
            plan: Plan,
            context: Context,
        ): Plan {
            val finalSteps = mutableListOf<PlanStep>()
            var currentOrder = 1

            for (step in plan.steps) {
                var updatedStep = step
                val isReg = actionDispatcher.get().isRegistered(step.action)

                if (!isReg) {
                    when (step.action.uppercase()) {
                        "VERIFY_APP", "SECURITY_CHECK" -> {
                            updatedStep = step.copy(action = "GET_SYSTEM_INFO")
                            logUnknownAction(step.action, plan.goal, "AUTO_FIXED")
                        }
                        "LAUNCH_APP", "OPEN_APP_OR_WEBSITE" -> {
                            val isWebsite =
                                step.action == "OPEN_APP_OR_WEBSITE" &&
                                    (
                                        step.params.containsKey("url") ||
                                            step.params.containsKey("website") ||
                                            step.params.containsKey("link") ||
                                            step.params.values.any { it.startsWith("http") }
                                    )
                            if (isWebsite) {
                                val urlValue =
                                    step.params["url"]
                                        ?: step.params["website"]
                                        ?: step.params["link"]
                                        ?: step.params.values.firstOrNull { it.startsWith("http") }
                                        ?: ""
                                updatedStep = step.copy(action = "SUMMARIZE_URL", params = mapOf("url" to urlValue))
                            } else {
                                val appNameValue =
                                    step.params["appName"]
                                        ?: step.params["app"]
                                        ?: step.params["packageName"]
                                        ?: step.params["package"]
                                        ?: ""
                                updatedStep = step.copy(action = "OPEN_APP", params = mapOf("appName" to appNameValue))
                            }
                            logUnknownAction(step.action, plan.goal, "AUTO_FIXED")
                        }
                        else -> {
                            logUnknownAction(step.action, plan.goal, "FAILED")
                        }
                    }
                }

                val commActions = listOf("SEND_WHATSAPP", "SEND_TELEGRAM", "MAKE_CALL", "SEND_SMS", "MAKE_VIDEO_CALL")
                if (commActions.contains(updatedStep.action.uppercase()) && updatedStep.params.containsKey("contact")) {
                    val contactName = updatedStep.params["contact"] ?: ""
                    if (contactName.isNotEmpty() && !isPhoneNumber(contactName) && !contactName.startsWith("@")) {
                        val resolvedPhone = resolveContactToPhoneNumber(context, contactName)
                        if (resolvedPhone != null) {
                            val updatedParams = updatedStep.params.toMutableMap().apply { put("contact", resolvedPhone) }
                            updatedStep = updatedStep.copy(order = currentOrder++, params = updatedParams)
                            finalSteps.add(updatedStep)
                        } else if (updatedStep.action.uppercase() == "SEND_TELEGRAM") {
                            // For Telegram, a contact name could also be a Telegram username directly
                            finalSteps.add(updatedStep.copy(order = currentOrder++))
                        } else {
                            val askStepId = "${updatedStep.stepId}_ask"
                            val askStep =
                                PlanStep(
                                    stepId = askStepId,
                                    order = currentOrder++,
                                    description = "Ask user for contact number of '$contactName'",
                                    action = "ASK_USER",
                                    params =
                                        mapOf(
                                            "question" to "I couldn't find a contact named '$contactName'. What is their phone number?",
                                        ),
                                    fallback = "",
                                )
                            finalSteps.add(askStep)
                            val updatedParams = updatedStep.params.toMutableMap().apply { put("contact", "$$askStepId") }
                            val updatedDependsOn = updatedStep.dependsOn.toMutableList().apply { if (!contains(askStepId)) add(askStepId) }
                            updatedStep = updatedStep.copy(order = currentOrder++, params = updatedParams, dependsOn = updatedDependsOn)
                            finalSteps.add(updatedStep)
                        }
                    } else {
                        updatedStep = updatedStep.copy(order = currentOrder++)
                        finalSteps.add(updatedStep)
                    }
                } else {
                    updatedStep = updatedStep.copy(order = currentOrder++)
                    finalSteps.add(updatedStep)
                }
            }

            val cleanedSteps = removeBadDependencies(finalSteps)
            return plan.copy(steps = cleanedSteps, estimatedSteps = cleanedSteps.size)
        }

        private fun removeBadDependencies(steps: List<PlanStep>): List<PlanStep> {
            return steps.map { step ->
                if (step.dependsOn.isEmpty()) return@map step
                val trueDeps =
                    step.dependsOn.filter { depId ->
                        val depStep = steps.find { it.stepId == depId }
                        depStep != null && DATA_PRODUCING_ACTIONS.contains(depStep.action.uppercase())
                    }
                step.copy(dependsOn = trueDeps)
            }
        }

        private suspend fun logUnknownAction(
            attemptedAction: String,
            goal: String,
            fixStatus: String,
        ) {
            try {
                unknownActionDao.get().insertUnknownAction(
                    UnknownActionEntity(attemptedAction = attemptedAction, goal = goal, fixStatus = fixStatus),
                )
            } catch (e: Exception) {
            }
        }

        private fun isPhoneNumber(contact: String): Boolean {
            val cleaned = contact.replace(" ", "").replace("-", "")
            return cleaned.startsWith("+") || (cleaned.isNotEmpty() && cleaned.all { it.isDigit() })
        }

        // lint false positive: both cursors are closed by `?.use { }` on every path,
        // but the Recycle detector does not model Kotlin's use() inlining. See #67.
        @Suppress("Recycle")
        private fun resolveContactToPhoneNumber(
            context: Context,
            contact: String,
        ): String? {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return null
            }
            try {
                val contentResolver = context.contentResolver
                val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val selectionExact = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?"
                contentResolver.query(uri, projection, selectionExact, arrayOf(contact.trim()), null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (idx >= 0) {
                            val number = cursor.getString(idx)
                            if (!number.isNullOrBlank()) return number.replace(" ", "").replace("-", "")
                        }
                    }
                }
                val selectionLike = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
                contentResolver.query(uri, projection, selectionLike, arrayOf("%${contact.trim()}%"), null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (idx >= 0) {
                            val number = cursor.getString(idx)
                            if (!number.isNullOrBlank()) return number.replace(" ", "").replace("-", "")
                        }
                    }
                }
            } catch (e: Exception) {
            }
            return null
        }
    }
