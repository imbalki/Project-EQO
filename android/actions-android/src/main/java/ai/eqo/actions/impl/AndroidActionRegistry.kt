// Origin: EQO TASK-069 (#20), explicit registry replacing donor Hilt/Room dispatcher construction.
package ai.eqo.actions.impl

import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.accessibility.EqoAutomation
import ai.eqo.accessibility.UntrustedScreenText
import ai.eqo.actions.base.Action
import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.ActionSchema
import ai.eqo.core.agent.ContactResolver
import ai.eqo.core.security.AndroidSensitiveMemoryStore
import ai.eqo.core.security.SensitiveMemoryStore
import ai.eqo.core.util.DeviceCapabilities
import android.Manifest
import android.content.Context
import kotlinx.coroutines.CancellationException

/**
 * The only public executor entry point. Construction is explicit; action implementations
 * and their lists are module-internal. Later batches add families in create(), not another
 * dispatcher or vocabulary. The caller must still obtain contextual action approval.
 */
class AndroidActionRegistry internal constructor(
    private val context: Context,
    families: List<List<Action>>,
    private val permissions: PermissionRequester,
    private val unknownActions: UnknownActionSink,
) {
    private val executors: Map<String, Action> = families.flatten().associateBy { it.name }
    val enabledActionNames: Set<String> get() = executors.keys.toSet()

    init {
        require(families.flatten().size == executors.size) { "Duplicate executor name" }
        require(executors.keys.all { ActionSchema.getAction(it) != null }) { "Executor name absent from ActionSchema" }
    }

    suspend fun execute(
        actionName: String,
        params: Map<String, String>,
    ): ActionResult {
        val action =
            executors[actionName] ?: run {
                unknownActions.record(actionName)
                return ActionResult.UnknownAction(actionName, enabledActionNames.sorted())
            }
        val canonical = canonicalParams(actionName, params)
        val (validation, enriched) = ActionSchema.validateParams(actionName, canonical)
        if (validation is ActionSchema.ValidationResult.MissingParams) {
            return ActionResult.Failure("Missing or invalid parameters: ${validation.params.joinToString(", ")}.")
        }
        val ready = enriched.mapValues { it.value.toString() }
        val blankRequired = ActionSchema.getAction(actionName)!!.params.filter { it.required && ready[it.name].isNullOrBlank() }
        if (blankRequired.isNotEmpty()) {
            return ActionResult.Failure("Required parameters must not be empty: ${blankRequired.joinToString { it.name }}.")
        }
        return try {
            for (permission in requiredPermissions(actionName, ready)) {
                if (!permissions.request(permission)) {
                    return ActionResult.Failure("${permission.explanation} Permission was not granted; this step did not run.")
                }
            }
            val result = executeRegistered(action, ready, context)
            if (actionName in UNTRUSTED_OUTPUTS && result is ActionResult.Success) {
                ActionResult.Success(mapOf("message" to UntrustedScreenText.wrap(result.data.orEmpty())))
            } else {
                result
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ActionResult.Failure("This action could not be completed. No success was verified.")
        }
    }

    private fun requiredPermissions(
        name: String,
        params: Map<String, String>,
    ): List<ActionPermission.Runtime> {
        val required = mutableListOf<ActionPermission.Runtime>()
        val contact = params["contact"].orEmpty().trim()
        val phone = contact.replace(Regex("[+\\-\\s()]"), "")
        val isNumber = phone.isNotEmpty() && phone.all { it.isDigit() }
        val telegramUsername =
            name == "SEND_TELEGRAM" &&
                (contact.startsWith("@") || (contact.isNotEmpty() && !contact.contains(" ") && !contact.all { it.isDigit() }))
        if (name in CONTACT_ACTIONS && !isNumber && !telegramUsername) {
            required += ActionPermission.Runtime(Manifest.permission.READ_CONTACTS, "Allow contacts access to find this person.")
        }
        if (name == "MAKE_CALL" && DeviceCapabilities.canMakeCalls(context)) {
            required += ActionPermission.Runtime(Manifest.permission.CALL_PHONE, "Allow phone access to place this call.")
            required +=
                ActionPermission.Runtime(Manifest.permission.READ_PHONE_STATE, "Allow phone state access to verify that the call started.")
        }
        return required
    }

    /** Upstream accepted aliases are mapped to schema keys before the one schema validator. */
    private fun canonicalParams(
        name: String,
        params: Map<String, String>,
    ): Map<String, String> {
        val result = params.toMutableMap()
        if (name == "SET_RINGER_MODE") {
            result["mode"]?.lowercase()?.trim()?.let {
                result["mode"] = mapOf("mute" to "silent", "vibration" to "vibrate")[it] ?: it
            }
        }
        if (name == "SET_VOLUME") {
            result["type"]?.lowercase()?.trim()?.let {
                result["type"] = mapOf("ringtone" to "ring", "ringer" to "ring", "notif" to "notification")[it] ?: it
            }
        }
        if (name in setOf("TOGGLE_FLASHLIGHT", "TOGGLE_WIFI", "TOGGLE_BLUETOOTH", "TOGGLE_MOBILE_DATA", "TOGGLE_HOTSPOT", "TOGGLE_DND")) {
            val raw = (result["state"] ?: result["on"])?.lowercase()?.trim()
            if (raw != null) {
                result["state"] =
                    when (raw) {
                        "on", "true", "enable", "yes" -> "on"
                        "off", "false", "disable", "no" -> "off"
                        else -> "toggle"
                    }
            }
        }
        if (name == "ADD_NOTE") {
            if ("title" !in result) result["name"]?.let { result["title"] = it }
            if ("content" !in result) (result["text"] ?: result["body"])?.let { result["content"] = it }
        }
        if (name in setOf("READ_NOTES", "RECALL_MEMORY") && "query" !in result) {
            result["topic"]?.let { result["query"] = it }
        }
        if (name == "READ_AND_REMEMBER_SCREEN" && "topic" !in result) result["query"]?.let { result["topic"] = it }
        if (name == "SET_REMINDER") {
            if ("text" !in result) result["title"]?.let { result["text"] = it }
            if ("datetime" !in result) result["time"]?.let { result["datetime"] = it }
        }
        if (name == "ASK_USER" && "question" !in result) result["message"]?.let { result["question"] = it }
        if (name == "CHAT" &&
            "response" !in result
        ) {
            (result["message"] ?: result["text"] ?: result["content"])?.let { result["response"] = it }
        }
        if (name in CONTACT_ACTIONS && "contact" !in result) {
            val aliases = if (name == "MAKE_CALL") listOf("number", "phone", "phoneNumber") else listOf("to", "recipient", "username")
            aliases.firstNotNullOfOrNull { result[it] }?.let { result["contact"] = it }
        }
        if (name in setOf("SEND_SMS", "SEND_TELEGRAM") && "message" !in result) {
            (result["text"] ?: result["body"])?.let { result["message"] = it }
        }
        return result
    }

    companion object {
        private val CONTACT_ACTIONS = setOf("SEND_SMS", "SEND_WHATSAPP", "SEND_TELEGRAM", "MAKE_CALL", "MAKE_VIDEO_CALL")
        private val UNTRUSTED_OUTPUTS =
            setOf("READ_FILE", "LIST_FILES", "LIST_INSTALLED_APPS", "GET_CLIPBOARD", "GET_SYSTEM_INFO", "ANALYZE_SCREENSHOT")

        fun create(
            context: Context,
            permissions: PermissionRequester,
            automation: () -> EqoAutomation? = { EQOAccessibilityService.getInstance()?.automation },
            unknownActions: UnknownActionSink = UnknownActionSink { android.util.Log.w("EqoActions", "Unsupported action requested") },
            options: RegistryOptions = RegistryOptions(),
        ): AndroidActionRegistry = createWithStore(context, permissions, automation, unknownActions, options)

        internal fun createWithStore(
            context: Context,
            permissions: PermissionRequester,
            automation: () -> EqoAutomation?,
            unknownActions: UnknownActionSink,
            options: RegistryOptions,
        ): AndroidActionRegistry {
            val launcher = GatedIntentLauncher(context, automation)
            val calls = CallFlowExecutor(options.callVerifier ?: AndroidCallFlowVerifier(), launcher)
            val http = options.informationHttp ?: AndroidInformationHttp(context)
            val memoryStore = options.memoryStore ?: AndroidSensitiveMemoryStore(context)
            return AndroidActionRegistry(
                context,
                listOf(
                    CommunicationActions(ContactResolver(context), calls, launcher).getActions(),
                    AdvancedControlActions().getActions(),
                    SystemActions(launcher, permissions, automation, options.screenAnalyzer).getActions(),
                    CalendarActions(launcher, permissions, automation).getActions(),
                    ProductivityMemoryActions(
                        automation,
                        options.productivityStore,
                        options.screenMemoryExtractor,
                    ).getActions(),
                    InformationActions(launcher, permissions, http, automation).getActions(),
                    ConversationActions().getActions(),
                    listOf(SaveSensitiveInfoAction(memoryStore)),
                ),
                permissions,
                unknownActions,
            )
        }
    }
}

/**
 * Optional collaborators for [AndroidActionRegistry.create]. Everything defaults to a fail-closed state:
 * without a [ProductivityStore] the persistence-backed actions refuse with a typed "needs the database" failure.
 */
class RegistryOptions(
    val screenAnalyzer: ScreenAnalyzer? = null,
    val productivityStore: ProductivityStore = UnavailableProductivityStore,
    val screenMemoryExtractor: ScreenMemoryExtractor? = null,
) {
    internal var callVerifier: CallFlowVerifier? = null
    internal var informationHttp: InformationHttp? = null
    internal var memoryStore: SensitiveMemoryStore? = null
}
