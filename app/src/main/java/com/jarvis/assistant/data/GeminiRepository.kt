package com.jarvis.assistant.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class GeminiResult {
    data class Success(val text: String) : GeminiResult()
    data class Error(val message: String) : GeminiResult()
}

/**
 * Schlanker REST-Client für die Gemini "generateContent" API.
 * Der API-Schlüssel wird pro Anfrage übergeben, nicht fest verdrahtet,
 * damit er später bequem in den Einstellungen hinterlegt werden kann.
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
    """.trimIndent()

    suspend fun sendMessage(apiKey: String, history: List<ChatMessage>): GeminiResult =
        withContext(Dispatchers.IO) {
            try {
                val requestBody = GenerateContentRequest(
                    systemInstruction = SystemInstruction(parts = listOf(Part(systemInstruction))),
                    contents = history.map { message ->
                        Content(
                            role = if (message.role == ChatMessage.Role.USER) "user" else "model",
                            parts = listOf(Part(message.text)),
                        )
                    },
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
                    val text = parsed.candidates
                        ?.firstOrNull()
                        ?.content
                        ?.parts
                        ?.joinToString(separator = "") { it.text.orEmpty() }
                        ?.trim()

                    if (text.isNullOrBlank()) {
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

    @Serializable
    private data class GenerateContentRequest(
        @SerialName("systemInstruction") val systemInstruction: SystemInstruction,
        val contents: List<Content>,
    )

    @Serializable
    private data class SystemInstruction(val parts: List<Part>)

    @Serializable
    private data class Content(val role: String? = null, val parts: List<Part>)

    @Serializable
    private data class Part(val text: String? = null)

    @Serializable
    private data class GenerateContentResponse(val candidates: List<Candidate>? = null)

    @Serializable
    private data class Candidate(val content: Content? = null)

    @Serializable
    private data class GeminiErrorEnvelope(val error: GeminiError? = null)

    @Serializable
    private data class GeminiError(val message: String? = null)
}
