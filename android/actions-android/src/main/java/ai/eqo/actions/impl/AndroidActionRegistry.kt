// Origin: EQO TASK-069 (#20), explicit registry replacing donor Hilt/Room dispatcher construction.
package ai.eqo.actions.impl

import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.accessibility.EqoAutomation
import ai.eqo.accessibility.UntrustedScreenText
import ai.eqo.actions.base.Action
import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.ActionSchema
import ai.eqo.core.agent.ContactResolution
import ai.eqo.core.agent.ContactResolver
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.failureMessage
import ai.eqo.core.agent.isLiteralEmail
import ai.eqo.core.agent.isLiteralPhone
import ai.eqo.core.security.AndroidSensitiveMemoryStore
import ai.eqo.core.security.SensitiveMemoryStore
import ai.eqo.core.util.DeviceCapabilities
import android.Manifest
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The only public executor entry point. Construction is explicit; action implementations
 * and their lists are module-internal. Later batches add families in create(), not another
 * dispatcher or vocabulary. The caller must still obtain contextual action approval.
 */
@Suppress("TooManyFunctions") // Single execution boundary also owns its shared prerequisite inventory.
class AndroidActionRegistry internal constructor(
    private val context: Context,
    families: List<List<Action>>,
    private val permissions: PermissionRequester,
    private val unknownActions: UnknownActionSink,
    private val contactResolver: ContactResolver = ContactResolver(context),
    private val allFilesAccess: () -> Boolean = { android.os.Environment.isExternalStorageManager() },
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
        val blankRequired = blankRequiredParams(actionName, ready)
        if (blankRequired.isNotEmpty()) {
            return ActionResult.Failure("Required parameters must not be empty: ${blankRequired.joinToString { it.name }}.")
        }
        return try {
            if (needsSharedAccess(actionName, ready) && !requestAllFilesAccess()) {
                android.util.Log.w("EqoRun", "action=$actionName reason=needs_all_files_access")
                return ActionResult.UserActionRequired(
                    "Turn on All files access for EQO. This step did not run. Stop and explicitly restart this plan.",
                )
            }
            for (permission in requiredPermissions(actionName, ready)) {
                android.util.Log.i("EqoRun", "action=$actionName permission=${permission.name} check=request")
                if (!permissions.request(permission)) {
                    android.util.Log.w("EqoRun", "action=$actionName permission=${permission.name} denied")
                    return ActionResult.UserActionRequired(
                        "${permission.explanation} This step did not run. " +
                            "Stop and explicitly restart the plan to grant access.",
                    )
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

    private fun blankRequiredParams(
        actionName: String,
        params: Map<String, String>,
    ) = ActionSchema.getAction(actionName)!!.params.filter {
        it.required &&
            params[it.name].isNullOrBlank() &&
            !ai.eqo.core.agent.AttachmentSpec
                .allowsEmptyText(actionName, it.name, params)
    }

    /** Lookup is local and happens before approval. The approved snapshot contains the literal destination. */
    suspend fun prepareRecipients(steps: List<LoopStep>): RecipientPlan {
        val names = mutableMapOf<String, String>()
        val prepared =
            steps.map { step ->
                val name = step.action.name
                if (name !in CONTACT_ACTIONS) return@map step
                val key = if (name == "SEND_EMAIL") "to" else "contact"
                val input =
                    step.action.params[key]
                        .orEmpty()
                        .trim()
                if (name == "SEND_TELEGRAM" && input.startsWith("@")) return@map step
                val needed = requiredPermissions(name, step.action.params)
                val permission = needed.firstOrNull { it.name == Manifest.permission.READ_CONTACTS }
                if (permission != null && !permissions.request(permission)) {
                    throw RecipientPreparationException(ContactResolution.PermissionDenied.failureMessage())
                }
                if (name == "SEND_EMAIL") {
                    val recipients = withContext(Dispatchers.IO) { resolveEmailRecipients(input, contactResolver) }
                    names[step.stepId] = recipients.labels.joinToString(", ")
                    return@map step.copy(
                        action =
                            step.action.copy(
                                params = step.action.params + (key to recipients.addresses.joinToString(", ")),
                            ),
                    )
                }
                val resolved =
                    withContext(Dispatchers.IO) {
                        contactResolver.resolveWithDisambiguation(input)
                    }
                if (resolved !is ContactResolution.Found) throw RecipientPreparationException(resolved.failureMessage())
                names[step.stepId] = resolved.contact.name
                step.copy(
                    action = step.action.copy(params = step.action.params + (key to resolved.contact.phoneNumber)),
                )
            }
        return RecipientPlan(prepared, names.toMap())
    }

    /** Settings is human-driven before a run starts, never an automation/takeover exemption. */
    suspend fun prepareFileAccess(steps: List<LoopStep>): Boolean =
        steps.none { needsSharedAccess(it.action.name, it.action.params) } || requestAllFilesAccess()

    /** One shared inventory for preview, preflight and execution; literal recipients need no lookup grant. */
    fun plannedRuntimePermissions(steps: List<LoopStep>): List<ActionPermission.Runtime> =
        steps.flatMap { requiredPermissions(it.action.name, it.action.params) }.distinctBy { it.name }

    suspend fun prepareRuntimeAccess(steps: List<LoopStep>): Boolean {
        val required = plannedRuntimePermissions(steps)
        return required.all { permissions.request(it) }
    }

    private fun needsSharedAccess(
        name: String,
        params: Map<String, String>,
    ): Boolean = name == "FIND_FILES" || name == "LIST_FILES" && !FileActions.usesWorkspace(params["folder"].orEmpty())

    private suspend fun requestAllFilesAccess(): Boolean {
        if (allFilesAccess()) return true
        return permissions.request(
            ActionPermission.SpecialAccess(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                true,
                ALL_FILES_INSTRUCTION,
                allFilesAccess,
            ),
        ) &&
            allFilesAccess()
    }

    private fun requiredPermissions(
        name: String,
        params: Map<String, String>,
    ): List<ActionPermission.Runtime> {
        val required = mutableListOf<ActionPermission.Runtime>()
        val contact = params[if (name == "SEND_EMAIL") "to" else "contact"].orEmpty().trim()
        val direct =
            if (name == "SEND_EMAIL") {
                splitEmailRecipients(contact).all { isLiteralEmail(it) }
            } else {
                isLiteralPhone(contact) ||
                    (name == "SEND_TELEGRAM" && contact.startsWith("@"))
            }
        if (name in CONTACT_ACTIONS && !direct) {
            required +=
                ActionPermission.Runtime(
                    Manifest.permission.READ_CONTACTS,
                    ContactResolution.PermissionDenied.failureMessage(),
                )
        }
        required += sharePermissions(name, params)
        extraPermission(name, params)?.let { required += it }
        if (name == "MAKE_CALL" && DeviceCapabilities.canMakeCalls(context)) {
            required += ActionPermission.Runtime(Manifest.permission.CALL_PHONE, "Allow phone access to place this call.")
            required +=
                ActionPermission.Runtime(Manifest.permission.READ_PHONE_STATE, "Allow phone state access to verify that the call started.")
        }
        return required
    }

    private fun extraPermission(
        name: String,
        params: Map<String, String>,
    ): ActionPermission.Runtime? {
        val extra =
            when (name) {
                "TOGGLE_FLASHLIGHT" -> Manifest.permission.CAMERA to "Allow camera access to control the flashlight."
                "GET_WEATHER" ->
                    if (params["location"].isNullOrBlank() || params["location"] == "current location") {
                        Manifest.permission.ACCESS_COARSE_LOCATION to "Allow approximate location for local weather."
                    } else {
                        null
                    }
                "CREATE_CALENDAR_EVENT" ->
                    if (isDirectCalendarInsert(params)) {
                        Manifest.permission.WRITE_CALENDAR to "Allow calendar access to save this event."
                    } else {
                        null
                    }
                else -> null
            }
        return extra?.let { ActionPermission.Runtime(it.first, it.second) }
    }

    /** Contacts access to find a named recipient or contact; precise location only to read the position. */
    private fun sharePermissions(
        name: String,
        params: Map<String, String>,
    ): List<ActionPermission.Runtime> {
        if (name !in SHARE_ACTIONS) return emptyList()
        val to = params["to"].orEmpty().replace(Regex("[+\\-\\s()]"), "")
        val toIsNumber = to.isNotEmpty() && to.all { it.isDigit() }
        val byEmail = params["via"]?.trim()?.lowercase() == "email"
        val contactsPermission =
            ActionPermission.Runtime(Manifest.permission.READ_CONTACTS, "Allow contacts access to find this person.")
        val locationPermission =
            ActionPermission.Runtime(Manifest.permission.ACCESS_FINE_LOCATION, "Allow location to share where you are.")
        return buildList {
            if (name == "SHARE_CONTACT" || !(byEmail || toIsNumber)) {
                add(contactsPermission)
            }
            if (name == "SHARE_LOCATION") {
                add(locationPermission)
            }
        }
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
            if (result["content"].isNullOrBlank()) result["title"]?.let { result["content"] = it }
        }
        if (name == "LIST_FILES" && "folder" !in result) result["path"]?.let { result["folder"] = it }
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
        if (name in CONTACT_ACTIONS && name != "SEND_EMAIL" && "contact" !in result) {
            val aliases = if (name == "MAKE_CALL") listOf("number", "phone", "phoneNumber") else listOf("to", "recipient", "username")
            aliases.firstNotNullOfOrNull { result[it] }?.let { result["contact"] = it }
        }
        if (name in setOf("SEND_SMS", "SEND_TELEGRAM") && "message" !in result) {
            (result["text"] ?: result["body"])?.let { result["message"] = it }
        }
        return result
    }

    companion object {
        private const val ALL_FILES_INSTRUCTION =
            "Turn on All files access for EQO, then return here to continue with this plan."
        private val CONTACT_ACTIONS =
            setOf(
                "SEND_SMS",
                "SEND_WHATSAPP",
                "WHATSAPP_CALL",
                "SEND_TELEGRAM",
                "MAKE_CALL",
                "MAKE_VIDEO_CALL",
                "SEND_EMAIL",
            )
        private val SHARE_ACTIONS = setOf("SHARE_CONTACT", "SHARE_LOCATION")
        private val UNTRUSTED_OUTPUTS =
            setOf(
                "READ_FILE",
                "LIST_FILES",
                "FIND_FILES",
                "LIST_INSTALLED_APPS",
                "GET_CLIPBOARD",
                "GET_SYSTEM_INFO",
                "ANALYZE_SCREENSHOT",
                "READ_NOTIFICATIONS",
                "LIST_MACROS",
                "DETECT_ROUTINES",
            )

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
            val contacts = options.contactResolver ?: ContactResolver(context)
            val files = SharedStorageServices.create(context, automation, options)
            val calls = CallFlowExecutor(options.callVerifier ?: AndroidCallFlowVerifier(), launcher)
            val http = options.informationHttp ?: AndroidInformationHttp(context)
            val memoryStore = options.memoryStore ?: AndroidSensitiveMemoryStore(context)
            val daos = options.automationDaos ?: RoomAutomationDaos.forContext(context)
            val autoReply = options.autoReplyConfig ?: SettingsAutoReplyConfigStore(context)
            // The registry does not exist yet while its macro/routine executors are built; they reach it through here.
            var registry: AndroidActionRegistry? = null
            val nested = NestedActionRunner { registry }
            return AndroidActionRegistry(
                context,
                listOf(
                    CommunicationActions(
                        contacts,
                        calls,
                        launcher,
                        automation = automation,
                        attachments = files.attachments,
                    ).getActions(),
                    FileActions(files.browser).getActions(),
                    AdvancedControlActions().getActions(),
                    SystemActions(launcher, permissions, automation, options.screenAnalyzer, files.screenshots)
                        .getActions(),
                    CalendarActions(launcher, permissions, automation).getActions(),
                    ProductivityMemoryActions(
                        automation,
                        options.productivityStore,
                        options.screenMemoryExtractor,
                    ).getActions(),
                    InformationActions(launcher, permissions, http, automation).getActions(),
                    ConversationActions().getActions(),
                    listOf(SaveSensitiveInfoAction(memoryStore)),
                    NotificationActions(daos, autoReply).getActions(),
                    MacroActions(daos, nested).getActions(),
                    RoutineActions(daos, HabitRoutineEngine(daos, nested)).getActions(),
                ),
                permissions,
                unknownActions,
                contacts,
                options.allFilesAccess ?: { android.os.Environment.isExternalStorageManager() },
            ).also { registry = it }
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
    var attachmentSelection: AttachmentSelection? = null
    internal var sharedMediaSource: SharedMediaSource? = null
    internal var sharedFolderMapStore: SharedFolderMapStore? = null
    internal var contactResolver: ContactResolver? = null
    internal var callVerifier: CallFlowVerifier? = null
    internal var informationHttp: InformationHttp? = null
    internal var memoryStore: SensitiveMemoryStore? = null
    internal var automationDaos: AutomationDaos? = null
    internal var autoReplyConfig: AutoReplyConfigStore? = null

    // Test seams for the file features; production uses Android's real shared storage.
    internal var allFilesAccess: (() -> Boolean)? = null
    internal var storageRoot: java.io.File? = null
    internal var lastScreenshotStore: LastScreenshotStore? = null
    internal var screenshotWriter: ScreenshotWriter? = null
    internal var shareUri: ((java.io.File) -> android.net.Uri)? = null
}

/** Display labels stay local; only steps are passed to the immutable approval snapshot. */
data class RecipientPlan(
    val steps: List<LoopStep>,
    val names: Map<String, String>,
)

class RecipientPreparationException(
    message: String,
) : IllegalArgumentException(message)
