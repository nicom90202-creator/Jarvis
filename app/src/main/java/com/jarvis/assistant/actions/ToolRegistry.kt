package com.jarvis.assistant.actions

import com.jarvis.assistant.data.FunctionDeclaration
import com.jarvis.assistant.data.ToolDeclaration
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Beschreibt Jarvis' Fähigkeiten gegenüber Gemini (Function Calling) sowie
 * die Google-Suche für aktuelle Informationen (Grounding). Jede Deklaration
 * hier muss ein Gegenstück in [DeviceActions.execute] haben.
 */
object ToolRegistry {

    fun declarations(): List<ToolDeclaration> = listOf(
        ToolDeclaration(functionDeclarations = functionDeclarations),
        ToolDeclaration(googleSearch = JsonObject(emptyMap())),
    )

    private fun schema(build: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(build)

    private val functionDeclarations = listOf(
        FunctionDeclaration(
            name = "open_app",
            description = "Öffnet eine auf dem Gerät installierte App anhand ihres Namens.",
            parameters = schema {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("app_name") {
                        put("type", "STRING")
                        put("description", "Name der App, z. B. \"Spotify\" oder \"Kamera\".")
                    }
                }
                putJsonArray("required") { add("app_name") }
            },
        ),
        FunctionDeclaration(
            name = "create_calendar_event",
            description = "Legt einen Termin im Standard-Kalender des Nutzers an.",
            parameters = schema {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("title") { put("type", "STRING") }
                    putJsonObject("start_iso") {
                        put("type", "STRING")
                        put("description", "Startzeit im ISO-8601-Format, z. B. 2026-07-27T15:00:00.")
                    }
                    putJsonObject("end_iso") {
                        put("type", "STRING")
                        put("description", "Endzeit im ISO-8601-Format.")
                    }
                    putJsonObject("location") { put("type", "STRING") }
                    putJsonObject("description") { put("type", "STRING") }
                }
                putJsonArray("required") { add("title"); add("start_iso"); add("end_iso") }
            },
        ),
        FunctionDeclaration(
            name = "list_upcoming_events",
            description = "Listet anstehende Kalendertermine der nächsten N Tage auf.",
            parameters = schema {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("days") {
                        put("type", "INTEGER")
                        put("description", "Anzahl der Tage in die Zukunft, z. B. 7.")
                    }
                }
                putJsonArray("required") { add("days") }
            },
        ),
        FunctionDeclaration(
            name = "set_alarm",
            description = "Stellt einen Wecker über die System-Uhr-App.",
            parameters = schema {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("hour") { put("type", "INTEGER") }
                    putJsonObject("minute") { put("type", "INTEGER") }
                    putJsonObject("label") { put("type", "STRING") }
                }
                putJsonArray("required") { add("hour"); add("minute") }
            },
        ),
        FunctionDeclaration(
            name = "set_timer",
            description = "Stellt einen Countdown-Timer über die System-Uhr-App.",
            parameters = schema {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("seconds") { put("type", "INTEGER") }
                    putJsonObject("label") { put("type", "STRING") }
                }
                putJsonArray("required") { add("seconds") }
            },
        ),
        FunctionDeclaration(
            name = "search_contact",
            description = "Sucht einen Kontakt nach Name und gibt Telefonnummern zurück.",
            parameters = schema {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("name") { put("type", "STRING") }
                }
                putJsonArray("required") { add("name") }
            },
        ),
        FunctionDeclaration(
            name = "call_contact",
            description = "Öffnet die Telefon-App mit vorausgefüllter Nummer/Name. Der Nutzer muss den Anruf selbst bestätigen.",
            parameters = schema {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("name_or_number") { put("type", "STRING") }
                }
                putJsonArray("required") { add("name_or_number") }
            },
        ),
        FunctionDeclaration(
            name = "compose_text_message",
            description = "Öffnet die SMS-App mit vorausgefülltem Empfänger und Text. Der Nutzer muss selbst auf Senden tippen.",
            parameters = schema {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("name_or_number") { put("type", "STRING") }
                    putJsonObject("message") { put("type", "STRING") }
                }
                putJsonArray("required") { add("name_or_number"); add("message") }
            },
        ),
        FunctionDeclaration(
            name = "toggle_flashlight",
            description = "Schaltet die Taschenlampe des Geräts an oder aus.",
            parameters = schema {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("on") { put("type", "BOOLEAN") }
                }
                putJsonArray("required") { add("on") }
            },
        ),
        FunctionDeclaration(
            name = "adjust_volume",
            description = "Ändert die Lautstärke eines Audio-Streams.",
            parameters = schema {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("direction") {
                        put("type", "STRING")
                        putJsonArray("enum") { add("up"); add("down"); add("mute") }
                    }
                    putJsonObject("stream") {
                        put("type", "STRING")
                        putJsonArray("enum") { add("music"); add("ring"); add("alarm") }
                    }
                }
                putJsonArray("required") { add("direction") }
            },
        ),
        FunctionDeclaration(
            name = "open_maps",
            description = "Öffnet Google Maps mit einer Suche/Route zu einem Ort.",
            parameters = schema {
                put("type", "OBJECT")
                putJsonObject("properties") {
                    putJsonObject("query") { put("type", "STRING") }
                }
                putJsonArray("required") { add("query") }
            },
        ),
    )
}
