// Origin: EQO email planning; local single-email slots, no storage or execution.
package ai.eqo.core.agent

/** Narrow self-contained email grammar. Other tasks still use model understanding. */
internal object EmailIntents {
    private val options = setOf(RegexOption.IGNORE_CASE)
    private val start = Regex("^(?:please\\s+)?(send|draft|compose|write)\\s+(?:an?\\s+)?e-?mail\\b", options)
    private val recipient =
        Regex("^to\\s+(.+?)(?=\\s+(?:with|subject|body|attach|and|but|do not|don't)\\b|$)", options)
    private val fields = Regex("\\b(?:with\\s+)?(subject|body)\\s*[:=]?\\s*\"([^\"]*)\"", options)
    private val named =
        Regex(
            "^(?:with\\s+(?:the\\s+)?file\\s+(?:called|named)|" +
                "(?:with\\s+)?attach(?:ment)?\\s*[:=])\\s*(.+)$",
            options,
        )
    private val generic =
        Regex("^(?:with\\s+(?:an?\\s+)?attachment|attach\\s+(?:a\\s+)?file)(?:,?\\s+choose the file myself)?$", options)
    private val photo = Regex("^(?:and\\s+)?attach\\s+(?:a\\s+)?(?:photo|picture|image)$", options)
    private val noSend = Regex("\\b(?:but\\s+)?(?:do not send|don't send|draft only|only type)\\b", options)
    private val quoted = Regex("\"[^\"]*\"")
    private val freeContent = Regex("\\b(?:subject|body|saying|message|text)\\b", options)
    private val multiple = Regex("\\b(?:then|and)\\b", options)
    private val channelCorrection = Regex("\\b(?:sms|text|whatsapp|telegram)\\b", options)
    private val taskCorrection = Regex("\\b(?:instead|actually|rather|cancel|forget)\\b", options)
    private val sendInstruction = Regex("\\bsend\\b", options)
    private val attachmentTail =
        Regex(
            "\\b(?:with\\s+(?:(?:an?\\s+)?attachment|(?:the\\s+)?file\\s+(?:called|named))|attach\\b).*$",
            options,
        )

    fun normalize(
        request: String,
        intent: TaskIntent,
    ): TaskIntent {
        val first = request.lineSequence().first().trim()
        val controls = first.replace(quoted, "")
        val command = start.find(first)
        // Once the owner corrects the original task/channel, trust model understanding of the whole history.
        // A later slot-only answer must not revive the first-line email; a later email correction is still allowed.
        val clarification = request.substringAfter("\nUser clarification ", "").replace(quoted, "")
        val singleEmail =
            command != null &&
                !multiple.containsMatchIn(controls) &&
                !channelCorrection.containsMatchIn(clarification) &&
                !taskCorrection.containsMatchIn(clarification)
        val draft = command?.groupValues?.get(1)?.lowercase() != "send" || noSend.containsMatchIn(controls)
        return if (singleEmail) {
            val send = if (draft && clarification.isEmpty()) "no" else intent.send
            val file = if (clarification.isEmpty()) requestedFile(first) else null
            val attachment =
                if (AttachmentSpec.PARAM !in intent.params && file != null) {
                    mapOf(AttachmentSpec.PARAM to file)
                } else {
                    emptyMap()
                }
            intent.copy(action = "send_message", channel = "email", send = send, params = intent.params + attachment)
        } else {
            intent
        }
    }

    private fun requestedFile(first: String): String? {
        // Mask quoted body/subject content without shifting the real filename's offsets.
        val controls = quoted.replace(first) { " ".repeat(it.value.length) }
        val tail = attachmentTail.find(controls)?.range?.first
        return tail?.let { fileSpec(first.substring(it).trim()) }
    }

    fun merge(
        previous: TaskIntent,
        updated: TaskIntent,
        answer: String,
    ): TaskIntent =
        if (previous.channel == "email" && updated.channel == "email" && updated.action == "send_message") {
            updated.copy(
                params = previous.params + emailParams(updated.params),
                send = clarifiedSend(previous.send, updated.send, answer),
            )
        } else {
            updated
        }

    private fun clarifiedSend(
        previous: String,
        updated: String,
        answer: String,
    ): String {
        val controls = answer.replace(quoted, "")
        return when {
            noSend.containsMatchIn(controls) -> "no"
            updated == "unknown" -> previous
            previous != "yes" && updated == "yes" && !sendInstruction.containsMatchIn(controls) -> previous
            else -> updated
        }
    }

    private fun emailParams(params: Map<String, String>): Map<String, String> =
        buildMap {
            putAll(params)
            if ("to" !in params) params["contact"]?.let { put("to", it) }
            if ("body" !in params) params["message"]?.let { put("body", it) }
        }

    @Suppress("ReturnCount") // Reject every unconsumed clause instead of silently dropping task content.
    fun from(request: String): TaskIntent? {
        val first = request.lineSequence().first().trim()
        val command = start.find(first) ?: return null
        val controls = first.replace(quoted, "")
        val withoutFields = first.replace(fields, "").replace(quoted, "")
        if (freeContent.containsMatchIn(withoutFields)) return null
        if (quoted.findAll(first.replace(fields, "")).any { noSend.containsMatchIn(it.value) }) return null
        // Do not collapse multi-action requests into one compose step.
        if (multiple.containsMatchIn(controls)) {
            return null
        }
        val slots =
            first
                .replace(fields, "")
                .replace(noSend, "")
                .removePrefix(command.value)
                .trim()
        val to = recipient.find(slots)
        val suffix = slots.replace(recipient, "").trim().trimEnd(',')
        val file = fileSpec(suffix)
        if (suffix.isNotEmpty() && file == null) return null
        val params =
            buildMap {
                to
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    ?.let { put("to", it) }
                fields.findAll(first).forEach { put(it.groupValues[1].lowercase(), it.groupValues[2]) }
                file?.let { put(AttachmentSpec.PARAM, it) }
            }
        val draft = command.groupValues[1].lowercase() != "send" || noSend.containsMatchIn(controls)
        return TaskIntent(
            "${if (draft) "Draft" else "Send"} an email",
            action = "send_message",
            params = params,
            channel = "email",
            send = if (draft) "no" else "yes",
            confidence = 1.0,
        )
    }

    private fun fileSpec(suffix: String): String? =
        when {
            generic.matches(suffix) -> AttachmentSpec.PICKER
            named.matches(suffix) -> {
                val name =
                    named
                        .matchEntire(suffix)!!
                        .groupValues[1]
                        .trim()
                        .removeSurrounding("\"")
                if (AttachmentSearch.isSearch(name) || '/' in name || '\\' in name) name else "find:$name"
            }
            photo.matches(suffix) -> "find:type=photo"
            else -> null
        }
}
