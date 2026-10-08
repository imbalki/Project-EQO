// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/core/agent/ContactResolver.kt
package ai.eqo.core.agent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Contact data model used throughout the disambiguation pipeline.
 */
data class Contact(
    val name: String,
    val phoneNumber: String,
    val type: String = "Mobile",
    val source: String = "contacts",
    val matchScore: Int = 0,
)

/**
 * Result of a contact resolution attempt.
 */
sealed class ContactResolution {
    /** Single contact found → use immediately */
    data class Found(
        val contact: Contact,
    ) : ContactResolution()

    /** Multiple contacts found → need user to pick */
    data class Ambiguous(
        val query: String,
        val matches: List<Contact>,
    ) : ContactResolution()

    data object PermissionDenied : ContactResolution()

    /** Nothing found → ask user for number */
    data class NotFound(
        val searchedName: String,
    ) : ContactResolution()
}

/** Resolves locally; multiple destinations always require clarification, never a remembered guess. */
@Singleton
open class ContactResolver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        // ── New disambiguation-aware resolve ──────────────────

        /**
         * Main resolve function with disambiguation support.
         * Returns single result OR multiple for user to pick.
         */
        open suspend fun resolveWithDisambiguation(input: String): ContactResolution {
            val query = input.trim()

            if (isLiteralPhone(query)) {
                return ContactResolution.Found(Contact(query, cleanPhone(query), source = "direct_input"))
            }
            if (!contactsGranted()) return ContactResolution.PermissionDenied
            return try {
                choose(query, findAllMatches(query))
            } catch (_: SecurityException) {
                ContactResolution.PermissionDenied
            }
        }

        open suspend fun resolveEmailWithDisambiguation(input: String): ContactResolution {
            val query = input.trim()
            return when {
                isLiteralEmail(query) ->
                    ContactResolution.Found(Contact(query, query, type = "Email", source = "direct_input"))
                !contactsGranted() -> ContactResolution.PermissionDenied
                else ->
                    try {
                        val matches =
                            queryContacts(
                                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                                arrayOf(
                                    ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
                                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                                    ContactsContract.CommonDataKinds.Email.TYPE,
                                ),
                                "${ContactsContract.CommonDataKinds.Email.DISPLAY_NAME} LIKE ?",
                                arrayOf("%${escapeLike(query)}%"),
                                email = true,
                            )
                        choose(query, matches)
                    } catch (_: SecurityException) {
                        ContactResolution.PermissionDenied
                    }
            }
        }

        private fun contactsGranted(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED

        private fun choose(
            query: String,
            matches: List<Contact>,
        ): ContactResolution {
            val distinct = matches.filter { it.phoneNumber.isNotBlank() }.distinctBy { it.name to it.phoneNumber }
            return when (distinct.size) {
                0 -> ContactResolution.NotFound(query)
                1 -> ContactResolution.Found(distinct.single())
                else -> ContactResolution.Ambiguous(query, distinct)
            }
        }

        private fun escapeLike(value: String): String {
            val escaped = value.replace("\\", "\\\\")
            return escaped.replace("%", "\\%").replace("_", "\\_")
        }

        /**
         * Find ALL contacts that match query using 4-tier search.
         */
        private fun findAllMatches(query: String): List<Contact> {
            val results = mutableListOf<Contact>()
            val seen = mutableSetOf<String>() // duplicate rows only; distinct names/numbers remain ambiguous

            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection =
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                )

            // Search 1: Exact name match (highest priority, score=100)
            queryContacts(
                uri,
                projection,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?",
                arrayOf(query),
            ).forEach { contact ->
                if (seen.add(contact.name + "\u0000" + contact.phoneNumber)) {
                    results.add(contact.copy(matchScore = 100))
                }
            }

            // Search 2: Case-insensitive exact match (score=90)
            queryContacts(
                uri,
                projection,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf(escapeLike(query)),
            ).forEach { contact ->
                if (seen.add(contact.name + "\u0000" + contact.phoneNumber)) {
                    results.add(contact.copy(matchScore = 90))
                }
            }

            // Search 3: Contains query anywhere in name (score=60)
            // This catches "Dad", "Dada", "Daddu" all together
            queryContacts(
                uri,
                projection,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%${escapeLike(query)}%"),
            ).forEach { contact ->
                if (seen.add(contact.name + "\u0000" + contact.phoneNumber)) {
                    // Boost score if the name IS the query (just different case).
                    // Boost if name starts with query and query is a full word:
                    // e.g., query="dad", name="Dad Mobile" gets 80
                    // but query="dad", name="Daddu" only gets 60
                    val score =
                        if (contact.name.equals(query, ignoreCase = true)) {
                            90
                        } else if (contact.name.startsWith(query, ignoreCase = true) &&
                            (
                                contact.name.length == query.length ||
                                    contact.name.getOrNull(query.length)?.let { it == ' ' || it == '(' } == true
                            )
                        ) {
                            80
                        } else {
                            60
                        }
                    results.add(contact.copy(matchScore = score))
                }
            }

            // Search 4: Relationship aliases (score=50)
            val aliases = getRelationshipAliases(query)
            aliases.forEach { alias ->
                queryContacts(
                    uri,
                    projection,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$alias%"),
                ).forEach { contact ->
                    if (seen.add(contact.name + "\u0000" + contact.phoneNumber)) {
                        results.add(contact.copy(matchScore = 50))
                    }
                }
            }

            // Sort by match score (highest first)
            return results.sortedByDescending { it.matchScore }
        }

        // lint false positive: the cursor is closed by `?.use { }` on every path,
        // but the Recycle detector does not model Kotlin's use() inlining. See #67.
        @Suppress("Recycle")
        private fun queryContacts(
            uri: android.net.Uri,
            projection: Array<String>,
            selection: String,
            selectionArgs: Array<String>,
            email: Boolean = false,
        ): List<Contact> {
            val contacts = mutableListOf<Contact>()
            context.contentResolver
                .query(
                    uri,
                    projection,
                    if (selection.contains("LIKE")) "$selection ESCAPE '\\'" else selection,
                    selectionArgs,
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(0) ?: continue
                        val number = cursor.getString(1) ?: continue
                        val type = cursor.getInt(2)
                        val destination = if (email) number.trim() else cleanPhone(number)
                        val valid =
                            if (email) {
                                destination.matches(
                                    Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),
                                )
                            } else {
                                isLiteralPhone(destination)
                            }
                        if (!valid) continue
                        contacts.add(
                            Contact(
                                name = name,
                                phoneNumber = destination,
                                type = if (email) "Email" else getPhoneTypeLabel(type),
                                source = "contacts",
                            ),
                        )
                    }
                }
            return contacts
        }

        /** Relationship word mappings for fuzzy matching */
        private fun getRelationshipAliases(query: String): List<String> {
            val lower = query.lowercase()
            return when {
                lower in
                    listOf(
                        "dad",
                        "daddy",
                        "father",
                        "papa",
                        "baba",
                        "abbu",
                        "pita",
                        "bapu",
                    )
                ->
                    listOf(
                        "dad",
                        "father",
                        "papa",
                        "baba",
                        "abbu",
                        "pita",
                        "daddy",
                        "bapu",
                    )

                lower in
                    listOf(
                        "mom",
                        "mum",
                        "mother",
                        "mama",
                        "maa",
                        "amma",
                        "mummy",
                        "ammi",
                    )
                ->
                    listOf(
                        "mom",
                        "mother",
                        "mama",
                        "maa",
                        "amma",
                        "mummy",
                        "ammi",
                        "mum",
                    )

                lower in
                    listOf(
                        "bro",
                        "brother",
                        "bhai",
                        "anna",
                        "dada",
                        "bhaiya",
                    )
                ->
                    listOf("brother", "bhai", "anna", "bhaiya", "bro")

                lower in
                    listOf(
                        "sis",
                        "sister",
                        "didi",
                        "akka",
                        "behen",
                    )
                ->
                    listOf("sister", "didi", "akka", "behen", "sis")

                lower in listOf("wife", "wifey", "patni", "biwi") ->
                    listOf("wife", "wifey", "patni", "biwi")

                lower in listOf("husband", "hubby", "pati") ->
                    listOf("husband", "hubby", "pati")

                lower in listOf("boss", "manager", "sir") ->
                    listOf("boss", "manager", "sir", "madam")

                else -> emptyList()
            }
        }

        private fun cleanPhone(number: String): String = number.replace(Regex("[\\s\\-()]"), "").trim()

        private fun getPhoneTypeLabel(type: Int): String =
            when (type) {
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                else -> "Phone"
            }
    }

/** Same literal classification at the permission and lookup boundaries. */
fun isLiteralPhone(input: String): Boolean = input.replace(Regex("[\\s\\-()]"), "").matches(Regex("\\+?[0-9]{7,}"))

fun isLiteralEmail(input: String): Boolean = input.trim().matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))

fun ContactResolution.failureMessage(): String =
    when (this) {
        is ContactResolution.Ambiguous ->
            "Several contacts match: " +
                matches.map { TaskDisplayText.escape(it.name) }.distinct().joinToString(", ") +
                ". Say the full name, or type the number or email if the names are the same. " +
                "Nothing was done."
        ContactResolution.PermissionDenied -> "Contacts permission is needed to find this person. Nothing was done."
        is ContactResolution.NotFound ->
            "No matching contact with a usable number or email was found. Nothing was done."
        is ContactResolution.Found -> error("Recipient is already resolved")
    }

/** Mask phone for privacy in picker display: 9876543210 → 98765***** */
fun maskPhone(phone: String): String =
    if (phone.length >= 5) {
        phone.take(5) + "*".repeat(phone.length - 5)
    } else {
        phone
    }
