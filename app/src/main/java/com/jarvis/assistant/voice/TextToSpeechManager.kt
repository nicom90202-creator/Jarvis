package com.jarvis.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Wrapper um Android's TextToSpeech-Engine, meldet Start/Ende des
 * Sprechens damit die UI die Kugel entsprechend animieren kann.
 */
class TextToSpeechManager(
    context: Context,
    private val onStart: () -> Unit = {},
    private val onDone: () -> Unit = {},
) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pendingQueue = mutableListOf<String>()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = onStart()
                    override fun onDone(utteranceId: String?) = onDone()

                    @Deprecated("Deprecated in Java", ReplaceWith(""))
                    override fun onError(utteranceId: String?) = onDone()
                })
                ready = true
                pendingQueue.forEach { speakInternal(it) }
                pendingQueue.clear()
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (ready) speakInternal(text) else pendingQueue.add(text)
    }

    private fun speakInternal(text: String) {
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
