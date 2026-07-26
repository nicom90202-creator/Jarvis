package com.jarvis.assistant.data

import com.jarvis.assistant.actions.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class GeminiResult {
    data class Success(val text: String) : GeminiResult()
    data class FunctionCalls(val calls: List<FunctionCallRequest>) : GeminiResult()
    data class Error(val message: String) : GeminiResult()
}

/**
 * Schlanker REST-Client für die Gemini "generateContent" API mit
 * Function-Calling (Jarvis' Geräte-Fähigkeiten) und Google-Suche
 * (aktuelle Informationen). Der API-Schlüssel wird pro Anfrage übergeben,
 * nicht fest verdrahtet, damit er bequem in den Einstellungen hinterlegt
 * werden kann.
 */
class GeminiRepository(
    private val model: String = "gemini-2.0-flash",
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val systemInstruction = """
        Du bist Jarvis, ein hilfsbereiter, präziser persönlicher Assistent auf dem Smartphone des Nutzers.
        Antworte kurz, natürlich gesprochen und auf Deutsch, außer der Nutzer spricht eine andere Sprache.
        Nutze die dir bereitgestellten Funktionen, um Kalender, Apps, Kontakte, Wecker/Timer, Taschenlampe,
        Lautstärke und Karten zu bedienen, und die Google-Suche für aktuelle Informationen. Rufe bei Anrufen
        oder SMS nur die vorgesehenen Funktionen auf, die die entsprechende App vorbereitet öffnen – du sendest
        nichts automatisch, der Nutzer bestätigt selbst.
    """.trimIndent()

    suspend fun sendMessage(apiKey: String, history: List<ChatMessage>): GeminiResult =
        withContext(Dispatchers.IO) {
            try {
                val requestBody = GenerateContentRequest(
                    systemInstruction = SystemInstruction(parts = listOf(Part(text = systemInstruction))),
                    contents = history.map { it.toContent() },
                    tools = ToolRegistry.declarations(),
                )

                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val bodyJson = json.encodeToString(requestBody)

                val request = Request.Builder()
                    .url(url)
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val apiError = runCatching { json.decodeFromString<GeminiErrorEnvelope>(responseBody) }
                            .getOrNull()?.error?.message
                        return@withContext GeminiResult.Error(
                            apiError ?: "Gemini-Fehler (HTTP ${response.code})",
                        )
                    }

                    val parsed = json.decodeFromString<GenerateContentResponse>(responseBody)
                    val parts = parsed.candidates?.firstOrNull()?.content?.parts.orEmpty()

                    val functionCalls = parts.mapNotNull { it.functionCall }
                        .map { FunctionCallRequest(it.name, it.args) }

                    if (functionCalls.isNotEmpty()) {
                        return@withContext GeminiResult.FunctionCalls(functionCalls)
                    }

                    val text = parts.mapNotNull { it.text }.joinToString(separator = "").trim()
                    if (text.isBlank()) {
                        GeminiResult.Error("Keine Antwort von Gemini erhalten.")
                    } else {
                        GeminiResult.Success(text)
                    }
                }
            } catch (e: IOException) {
                GeminiResult.Error("Verbindung zu Gemini fehlgeschlagen: ${e.message}")
            } catch (e: Exception) {
                GeminiResult.Error("Unerwarteter Fehler: ${e.message}")
            }
        }

    private fun ChatMessage.toContent(): Content = when (this) {
        is ChatMessage.UserText -> Content(role = "user", parts = listOf(Part(text = text)))
        is ChatMessage.ModelText -> Content(role = "model", parts = listOf(Part(text = text)))
        is ChatMessage.ModelFunctionCall -> Content(
            role = "model",
            parts = listOf(Part(functionCall = FunctionCallPart(name = name, args = args))),
        )
        is ChatMessage.FunctionResult -> Content(
            // Die Gemini-API kennt nur die Rollen "user" und "model" – die
            // Funktionsantwort wird als "user"-Turn zurückgegeben.
            role = "user",
            parts = listOf(Part(functionResponse = FunctionResponsePart(name = name, response = response))),
        )
    }

    @Serializable
    private data class GenerateContentRequest(
        @SerialName("systemInstruction") val systemInstruction: SystemInstruction,
        val contents: List<Content>,
        val tools: List<ToolDeclaration>,
    )

    @Serializable
    private data class SystemInstruction(val parts: List<Part>)

    @Serializable
    private data class Content(val role: String? = null, val parts: List<Part>)

    @Serializable
    private data class Part(
        val text: String? = null,
        val functionCall: FunctionCallPart? = null,
        val functionResponse: FunctionResponsePart? = null,
    )

    @Serializable
    private data class FunctionCallPart(val name: String, val args: JsonObject = JsonObject(emptyMap()))

    @Serializable
    private data class FunctionResponsePart(val name: String, val response: JsonObject)

    @Serializable
    private data class GenerateContentResponse(val candidates: List<Candidate>? = null)

    @Serializable
    private data class Candidate(val content: Content? = null)

    @Serializable
    private data class GeminiErrorEnvelope(val error: GeminiError? = null)

    @Serializable
    private data class GeminiError(val message: String? = null)
}
