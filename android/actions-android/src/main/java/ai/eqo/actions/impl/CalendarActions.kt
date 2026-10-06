// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51
// Origin: path: app/src/main/java/com/opendroid/ai/actions/CalendarActions.kt; EQO TASK-074 port.
package ai.eqo.actions.impl

import ai.eqo.accessibility.A11yError
import ai.eqo.accessibility.A11yResult
import ai.eqo.accessibility.EqoAutomation
import ai.eqo.actions.base.Action
import ai.eqo.actions.base.ActionResult
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import kotlinx.coroutines.CancellationException
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Calendar, alarm, timer and reminder executors; every system hand-off goes through [GatedIntentLauncher]. */
internal class CalendarActions(
    private val launcher: GatedIntentLauncher,
    private val permissions: PermissionRequester,
    private val automation: () -> EqoAutomation?,
) {
    fun getActions(): List<Action> =
        listOf(
            RegisteredExecutor("CREATE_CALENDAR_EVENT", ::createEvent),
            RegisteredExecutor("LIST_CALENDAR_TODAY") { _, _ -> openCalendar() },
            RegisteredExecutor("LIST_CALENDAR_WEEK") { _, _ -> openCalendar() },
            RegisteredExecutor("SET_REMINDER") { params, _ -> reminder(params) },
        ) + ClockActions(launcher).getActions()

    private suspend fun createEvent(
        params: Map<String, String>,
        context: Context,
    ): ActionResult =
        if (isDirectInsert(params)) {
            val refusal = writeRefusal(context)
            refusal ?: insertEvent(params, context)
        } else {
            calendarDraft(params, "Set the requested date, time and details in Calendar; the event is not saved yet.")
        }

    /** Donor only supports an event starting now, lasting one hour. Never silently ignore a requested date/time. */
    private fun isDirectInsert(params: Map<String, String>): Boolean =
        params["date"].orEmpty().lowercase(Locale.ROOT) in setOf("today", "now") &&
            params["time"].isNullOrBlank() &&
            params["duration"].orEmpty() in setOf("", "1 hour") &&
            params["attendees"].isNullOrBlank() &&
            params["location"].isNullOrBlank()

    private suspend fun writeRefusal(context: Context): ActionResult? {
        val gate = takeoverGate()
        return when {
            !gate.isSuccess -> gate.toActionResult()
            !permissions.request(
                ActionPermission.Runtime(
                    Manifest.permission.WRITE_CALENDAR,
                    "Allow calendar access to save this event.",
                ),
            ) -> ActionResult.Failure("Calendar permission was not granted; no event was saved.")
            context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED ->
                ActionResult.Failure("Calendar access is not granted; no event was saved.")
            else -> null
        }
    }

    private fun takeoverGate(): A11yResult =
        automation()?.runAction { A11yResult.success("Ready.") } ?: A11yResult.failure(A11yError.AccessibilityDisabled)

    private fun insertEvent(
        params: Map<String, String>,
        context: Context,
    ): ActionResult {
        val start = Calendar.getInstance().timeInMillis
        val values =
            ContentValues().apply {
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.DTEND, start + ONE_HOUR_MS)
                put(CalendarContract.Events.TITLE, params["title"] ?: "New Event")
                put(CalendarContract.Events.DESCRIPTION, params["description"] ?: "Created by EQO")
                put(CalendarContract.Events.CALENDAR_ID, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
        return try {
            val result =
                automation()?.runAction {
                    val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    if (uri == null) {
                        A11yResult.failure(A11yError.ActionRejected("calendar insert"))
                    } else {
                        A11yResult.success("Calendar event saved.")
                    }
                } ?: A11yResult.failure(A11yError.AccessibilityDisabled)
            if (result.isSuccess) {
                ActionResult.Success(mapOf("message" to "Calendar event saved for now, lasting one hour."))
            } else {
                result.toActionResult()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            calendarDraft(
                params,
                "Direct calendar insertion failed. Finish creating the event in Calendar; it is not saved yet.",
            )
        }
    }

    private fun calendarDraft(
        params: Map<String, String>,
        message: String,
    ): ActionResult {
        val intent =
            Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, params["title"] ?: params["text"] ?: "New Event")
                putExtra(CalendarContract.Events.DESCRIPTION, params["description"] ?: "Created by EQO")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        launcher.open(intent)
        return ActionResult.UserActionRequired(message)
    }

    private fun openCalendar(): ActionResult {
        val builder = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
        ContentUris.appendId(builder, Calendar.getInstance().timeInMillis)
        launcher.open(Intent(Intent.ACTION_VIEW, builder.build()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return ActionResult.UserActionRequired(
            "Calendar opened. Read the schedule there; no events were retrieved by EQO.",
        )
    }

    private fun reminder(params: Map<String, String>): ActionResult {
        val intent =
            Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, params["title"] ?: params["text"] ?: "New Reminder")
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, Calendar.getInstance().timeInMillis)
                putExtra(CalendarContract.Events.DESCRIPTION, params["description"] ?: "Created by EQO")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        launcher.open(intent)
        return ActionResult.UserActionRequired(
            "Calendar opened. Set the requested reminder time and save it yourself; no reminder was verified.",
        )
    }

    private companion object {
        const val ONE_HOUR_MS = 60L * 60L * 1000L
    }
}

/** Alarm and timer hand-offs to the Clock app; never claims an alarm or timer was verified. */
private class ClockActions(
    private val launcher: GatedIntentLauncher,
) {
    fun getActions(): List<Action> =
        listOf(
            RegisteredExecutor("SET_ALARM", ::setAlarm),
            RegisteredExecutor("SET_TIMER", ::setTimer),
        )

    private fun setAlarm(
        params: Map<String, String>,
        context: Context,
    ): ActionResult {
        val (hour, minute) =
            AlarmTimeParser.parse(params["time"].orEmpty())
                ?: return ActionResult.Failure("Invalid alarm time. Use 5 am, 7:30, or 14:00.")
        val intent =
            Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, params["label"]?.trim() ?: "EQO Alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        return launchAlarm(intent, context) ?: openClockFallback(context, hour, minute)
    }

    /** Returns null only when Android has no matching handler; a takeover refusal never reaches the fallback. */
    private fun launchAlarm(
        intent: Intent,
        context: Context,
    ): ActionResult? =
        if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            null
        } else {
            try {
                launcher.open(intent)
                ActionResult.UserActionRequired("Confirm the alarm in Clock; no alarm was verified as set.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: ActivityNotFoundException) {
                null
            } catch (_: SecurityException) {
                ActionResult.Failure("Android refused the alarm request.")
            }
        }

    private fun openClockFallback(
        context: Context,
        hour: Int,
        minute: Int,
    ): ActionResult {
        val packages = context.packageManager
        val fallback =
            packages.getLaunchIntentForPackage("com.google.android.deskclock")
                ?: packages.getLaunchIntentForPackage("com.android.deskclock")
        return if (fallback == null) {
            ActionResult.Failure("No clock app is available. Open Clock manually.")
        } else {
            launcher.open(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ActionResult.UserActionRequired(
                "Clock opened. Set the alarm for ${AlarmTimeParser.format(hour, minute)} yourself.",
            )
        }
    }

    private fun setTimer(
        params: Map<String, String>,
        context: Context,
    ): ActionResult {
        val duration = params["duration"] ?: return ActionResult.Failure("Timer duration is required.")
        val seconds = TimerDurationParser.parse(duration)
        return if (seconds == null) {
            ActionResult.Failure("Invalid timer duration.")
        } else {
            launchTimer(seconds, params["label"] ?: "EQO Timer", context)
        }
    }

    private fun launchTimer(
        seconds: Int,
        label: String,
        context: Context,
    ): ActionResult {
        val intent =
            Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        return if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            ActionResult.Failure("No timer app is available.")
        } else {
            launcher.open(intent)
            ActionResult.UserActionRequired(
                "Confirm the $seconds second timer in Clock; no running timer was verified.",
            )
        }
    }
}
