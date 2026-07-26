package com.jarvis.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Kapselt Android's on-device/cloud SpeechRecognizer für kontinuierliche
 * Sprach-Eingabe. Ruft [onResult] mit dem erkannten Text auf, bzw.
 * [onError] wenn nichts verstanden wurde oder ein Fehler auftrat.
 */
class SpeechRecognizerManager(
    private val context: Context,
    private val onPartialResult: (String) -> Unit = {},
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit = {},
) {
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        if (!isAvailable()) {
            onError("Spracherkennung ist auf diesem Gerät nicht verfügbar.")
            return
        }

        stopListening()

        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speechRecognizer

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onListeningStateChanged(true)
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                onListeningStateChanged(false)
            }

            override fun onError(error: Int) {
                onListeningStateChanged(false)
                onError(mapError(error))
            }

            override fun onResults(results: Bundle?) {
                onListeningStateChanged(false)
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (text.isNullOrBlank()) {
                    onError("Ich habe dich nicht verstanden.")
                } else {
                    onResult(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    onPartialResult(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        speechRecognizer.startListening(intent)
    }

    fun stopListening() {
        recognizer?.let {
            it.stopListening()
            it.destroy()
        }
        recognizer = null
    }

    fun destroy() {
        stopListening()
    }

    private fun mapError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Ich habe dich nicht verstanden."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Ich habe nichts gehört."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Netzwerkfehler bei der Spracherkennung."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon-Berechtigung fehlt."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Spracherkennung ist beschäftigt."
        else -> "Spracherkennung fehlgeschlagen (Code $error)."
    }
}
