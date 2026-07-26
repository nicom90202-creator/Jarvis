package com.jarvis.assistant.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * Führt die Fähigkeiten aus, die Jarvis Gemini über [ToolRegistry] anbietet.
 * Anrufe/SMS werden bewusst nur *vorbereitet* (Dialer/SMS-App mit
 * ausgefülltem Inhalt geöffnet) – der Nutzer bestätigt den letzten Schritt
 * selbst, es wird nichts automatisch versendet.
 */
class DeviceActions(private val context: Context) {

    suspend fun execute(name: String, args: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        try {
            when (name) {
                "open_app" -> openApp(args.str("app_name"))
                "create_calendar_event" -> createCalendarEvent(args)
                "list_upcoming_events" -> listUpcomingEvents(args.int("days") ?: 7)
                "set_alarm" -> setAlarm(args)
                "set_timer" -> setTimer(args)
                "search_contact" -> searchContact(args.str("name"))
                "call_contact" -> callContact(args.str("name_or_number"))
                "compose_text_message" -> composeTextMessage(args)
                "toggle_flashlight" -> toggleFlashlight(args.bool("on") == true)
                "adjust_volume" -> adjustVolume(args)
                "open_maps" -> openMaps(args.str("query"))
                else -> status("error", "message" to "Unbekannte Funktion: $name")
            }
        } catch (e: Exception) {
            status("error", "message" to (e.message ?: "Unbekannter Fehler"))
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun permissionDenied(permission: String): JsonObject = buildJsonObject {
        put("status", "permission_denied")
        put("permission", permission)
    }

    // --- Apps ---

    private fun openApp(appName: String?): JsonObject {
        if (appName.isNullOrBlank()) return status("error", "message" to "Kein App-Name angegeben.")

        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = pm.queryIntentActivities(launcherIntent, 0)

        val match = candidates.firstOrNull {
            it.loadLabel(pm).toString().contains(appName, ignoreCase = true)
        } ?: return status("not_found", "app" to appName)

        val label = match.loadLabel(pm).toString()
        val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return status("not_found", "app" to appName)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return status("opened", "app" to label)
    }

    // --- Kalender ---

    private fun createCalendarEvent(args: JsonObject): JsonObject {
        if (!hasPermission(Manifest.permission.WRITE_CALENDAR)) {
            return permissionDenied(Manifest.permission.WRITE_CALENDAR)
        }
        val title = args.str("title") ?: return status("error", "message" to "Kein Titel angegeben.")
        val startMillis = args.str("start_iso")?.toEpochMillisOrNull()
            ?: return status("error", "message" to "Ungültige Startzeit.")
        val endMillis = args.str("end_iso")?.toEpochMillisOrNull()
            ?: return status("error", "message" to "Ungültige Endzeit.")

        val calendarId = defaultCalendarId()
            ?: return status("error", "message" to "Kein Kalender auf dem Gerät gefunden.")

        val values = android.content.ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
            args.str("location")?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
            args.str("description")?.let { put(CalendarContract.Events.DESCRIPTION, it) }
        }

        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        return if (uri != null) status("created", "title" to title) else status("error", "message" to "Termin konnte nicht angelegt werden.")
    }

    private fun listUpcomingEvents(days: Int): JsonObject {
        if (!hasPermission(Manifest.permission.READ_CALENDAR)) {
            return permissionDenied(Manifest.permission.READ_CALENDAR)
        }
        val now = System.currentTimeMillis()
        val end = now + days.coerceIn(1, 90) * 24L * 60 * 60 * 1000

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
        )
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString())
            .appendPath(end.toString())
            .build()

        val events = buildJsonObject {
            putJsonArray("events") {
                context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
                    val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                    val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                    val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                    while (cursor.moveToNext()) {
                        addJsonObject {
                            put("title", cursor.getString(titleIdx) ?: "")
                            put("start_millis", cursor.getLong(beginIdx))
                            put("end_millis", cursor.getLong(endIdx))
                        }
                    }
                }
            }
        }
        return events
    }

    private fun defaultCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            var fallback: Long? = null
            val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
            val primaryIdx = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                if (fallback == null) fallback = id
                if (primaryIdx >= 0 && cursor.getInt(primaryIdx) == 1) return id
            }
            return fallback
        }
        return null
    }

    // --- Wecker & Timer ---

    private fun setAlarm(args: JsonObject): JsonObject {
        val hour = args.int("hour") ?: return status("error", "message" to "Keine Stunde angegeben.")
        val minute = args.int("minute") ?: 0
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            args.str("label")?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            status("alarm_set", "hour" to hour.toString(), "minute" to minute.toString())
        } else {
            status("error", "message" to "Keine Uhr-App gefunden.")
        }
    }

    private fun setTimer(args: JsonObject): JsonObject {
        val seconds = args.int("seconds") ?: return status("error", "message" to "Keine Dauer angegeben.")
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            args.str("label")?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            status("timer_set", "seconds" to seconds.toString())
        } else {
            status("error", "message" to "Keine Uhr-App gefunden.")
        }
    }

    // --- Kontakte & Kommunikation ---

    private fun findContactPhone(nameOrNumber: String): String? {
        if (nameOrNumber.all { it.isDigit() || it in "+- ()" }) return nameOrNumber
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return null

        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            arrayOf("%$nameOrNumber%"),
            null,
        )?.use { cursor ->
            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (cursor.moveToFirst()) return cursor.getString(numberIdx)
        }
        return null
    }

    private fun searchContact(name: String?): JsonObject {
        if (name.isNullOrBlank()) return status("error", "message" to "Kein Name angegeben.")
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            return permissionDenied(Manifest.permission.READ_CONTACTS)
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        return buildJsonObject {
            putJsonArray("contacts") {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    selection,
                    arrayOf("%$name%"),
                    null,
                )?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (cursor.moveToNext()) {
                        addJsonObject {
                            put("name", cursor.getString(nameIdx) ?: "")
                            put("phone", cursor.getString(numberIdx) ?: "")
                        }
                    }
                }
            }
        }
    }

    private fun callContact(nameOrNumber: String?): JsonObject {
        if (nameOrNumber.isNullOrBlank()) return status("error", "message" to "Kein Kontakt angegeben.")
        val number = findContactPhone(nameOrNumber) ?: return status("contact_not_found", "query" to nameOrNumber)
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return status("dialer_opened", "number" to number)
    }

    private fun composeTextMessage(args: JsonObject): JsonObject {
        val target = args.str("name_or_number") ?: return status("error", "message" to "Kein Empfänger angegeben.")
        val message = args.str("message") ?: ""
        val number = findContactPhone(target) ?: return status("contact_not_found", "query" to target)
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            status("sms_app_opened", "number" to number)
        } else {
            status("error", "message" to "Keine SMS-App gefunden.")
        }
    }

    // --- Gerätesteuerung ---

    private fun toggleFlashlight(on: Boolean): JsonObject {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return status("error", "message" to "Kamera-Dienst nicht verfügbar.")
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return status("error", "message" to "Keine Taschenlampe gefunden.")
            cameraManager.setTorchMode(cameraId, on)
            status("ok", "on" to on.toString())
        } catch (e: Exception) {
            status("error", "message" to (e.message ?: "Taschenlampe fehlgeschlagen."))
        }
    }

    private fun adjustVolume(args: JsonObject): JsonObject {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return status("error", "message" to "Audio-Dienst nicht verfügbar.")
        val direction = when (args.str("direction")) {
            "up" -> AudioManager.ADJUST_RAISE
            "down" -> AudioManager.ADJUST_LOWER
            "mute" -> AudioManager.ADJUST_TOGGLE_MUTE
            else -> return status("error", "message" to "Ungültige Richtung.")
        }
        val stream = when (args.str("stream")) {
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            else -> AudioManager.STREAM_MUSIC
        }
        audioManager.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI)
        return status("ok")
    }

    private fun openMaps(query: String?): JsonObject {
        if (query.isNullOrBlank()) return status("error", "message" to "Kein Suchbegriff angegeben.")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            status("opened", "query" to query)
        } else {
            status("error", "message" to "Keine Karten-App gefunden.")
        }
    }

    // --- Helpers ---

    private fun status(status: String, vararg extra: Pair<String, String>): JsonObject = buildJsonObject {
        put("status", status)
        extra.forEach { (key, value) -> put(key, value) }
    }

    private fun String.toEpochMillisOrNull(): Long? = try {
        LocalDateTime.parse(this).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
}
