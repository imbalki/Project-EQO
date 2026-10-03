/*
 * EQO (TASK-010, issue #15): navigate Chrome and fill a test form through CDP.
 *
 * Acceptance criterion (AC3): "Navigate and fill a local test form end to end". This is the
 * spike's payoff capability. It is composed from real Chrome DevTools Protocol methods over a
 * connected [ChromeCdpClient]:
 *   - navigate(url)         -> Page.enable + Page.navigate
 *   - fillField(sel, text)  -> Runtime.evaluate that sets the input value and dispatches the
 *                              real 'input'/'change' events a form listens for
 *   - readField(sel)        -> Runtime.evaluate that reads the value back for verification
 *   - fillForm(url, fields) -> navigate + fill every field + read back, so the caller can prove
 *                              the fill landed end to end
 *
 * The controller runs only after [ChromeCdpSetup] has verified the endpoint and marked
 * NAVIGATE / FILL_FORM ready; it does not itself reach a device. Selectors and text are
 * embedded as JSON string literals (valid JS string literals) so quotes/backslashes cannot
 * break out of the generated script.
 */
package ai.eqo.browser.cdp

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** One form field to fill: a CSS [selector] and the [text] to type into it. */
data class FormField(
    val selector: String,
    val text: String,
)

/** Result of [ChromePageController.fillForm]: the navigation target and per-field read-back. */
data class FormFillResult(
    val navigatedTo: String,
    /** selector -> the value read back from the field after filling (proof the fill landed). */
    val readBack: Map<String, String>,
) {
    /** True when every field read back exactly the text that was typed. */
    fun allFieldsMatch(fields: List<FormField>): Boolean = fields.all { readBack[it.selector] == it.text }
}

class ChromePageController(
    private val client: ChromeCdpClient,
) {
    /** Navigate the active page to [url]. */
    suspend fun navigate(url: String) {
        client.send("Page.enable")
        client.send(
            "Page.navigate",
            buildJsonObject { put("url", url) },
        )
    }

    /**
     * Fill the field at [selector] with [text], dispatching the DOM events a form listens for.
     * @return the value the field holds after the write (read back in the same evaluate).
     */
    suspend fun fillField(
        selector: String,
        text: String,
    ): String {
        val expression =
            FILL_TEMPLATE
                .replace("__SELECTOR__", jsString(selector))
                .replace("__TEXT__", jsString(text))
        return evaluateString(expression)
    }

    /** Read the current value of the field at [selector], or null when absent. */
    suspend fun readField(selector: String): String? {
        val expression = READ_TEMPLATE.replace("__SELECTOR__", jsString(selector))
        return evaluateStringOrNull(expression)
    }

    /**
     * End-to-end (AC3): navigate to [url], fill each [FormField], then read every field back so
     * [FormFillResult.allFieldsMatch] can assert the fill landed.
     */
    suspend fun fillForm(
        url: String,
        fields: List<FormField>,
    ): FormFillResult {
        navigate(url)
        val readBack = mutableMapOf<String, String>()
        for (field in fields) {
            fillField(field.selector, field.text)
            readBack[field.selector] = readField(field.selector) ?: ""
        }
        return FormFillResult(navigatedTo = url, readBack = readBack)
    }

    /** Runtime.evaluate returning a non-null string value. */
    private suspend fun evaluateString(expression: String): String =
        evaluateStringOrNull(expression)
            ?: throw CdpException(-1, "evaluate returned no string value for: $expression")

    /** Runtime.evaluate returning a string value or null. */
    private suspend fun evaluateStringOrNull(expression: String): String? {
        val result =
            client.send(
                "Runtime.evaluate",
                buildJsonObject {
                    put("expression", expression)
                    put("returnByValue", true)
                },
            )
        // CDP wraps the value as result.result.value; JsonNull / absent means null.
        val value = result.jsonObject["result"]?.jsonObject?.get("value") ?: return null
        if (value is JsonNull) return null
        return value.jsonPrimitive.content
    }

    /** Encode [s] as a JSON string literal (== a safe JS string literal) with quotes included. */
    private fun jsString(s: String): String = JsonPrimitive(s).toString()

    companion object {
        private val FILL_TEMPLATE =
            "(function(){var el=document.querySelector(__SELECTOR__);" +
                "if(!el){return null;}" +
                "el.focus();el.value=__TEXT__;" +
                "el.dispatchEvent(new Event('input',{bubbles:true}));" +
                "el.dispatchEvent(new Event('change',{bubbles:true}));" +
                "return el.value;})()"

        private val READ_TEMPLATE =
            "(function(){var el=document.querySelector(__SELECTOR__);" +
                "return el?el.value:null;})()"
    }
}
