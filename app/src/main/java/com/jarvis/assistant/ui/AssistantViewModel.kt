package com.jarvis.assistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.data.ApiKeyStore
import com.jarvis.assistant.data.ChatMessage
import com.jarvis.assistant.data.GeminiRepository
import com.jarvis.assistant.data.GeminiResult
import com.jarvis.assistant.voice.SpeechRecognizerManager
import com.jarvis.assistant.voice.TextToSpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val apiKeyStore = ApiKeyStore(application)
    private val geminiRepository = GeminiRepository()
    private val history = mutableListOf<ChatMessage>()

    private var speechRecognizerManager: SpeechRecognizerManager? = null

    private val textToSpeechManager = TextToSpeechManager(
        context = application,
        onStart = { _uiState.update { it.copy(orbState = OrbState.SPEAKING) } },
        onDone = { _uiState.update { it.copy(orbState = OrbState.IDLE) } },
    )

    private val _uiState = MutableStateFlow(
        AssistantUiState(hasApiKey = apiKeyStore.hasGeminiApiKey()),
    )
    val uiState: StateFlow<AssistantUiState> = _uiState

    fun onMicTapped() {
        val state = _uiState.value.orbState
        if (state == OrbState.LISTENING) {
            speechRecognizerManager?.stopListening()
            return
        }
        if (state == OrbState.THINKING || state == OrbState.SPEAKING) {
            return
        }
        if (!apiKeyStore.hasGeminiApiKey()) {
            _uiState.update { it.copy(errorMessage = "no_key") }
            return
        }
        startListening()
    }

    private fun startListening() {
        textToSpeechManager.stop()
        _uiState.update { it.copy(errorMessage = null, transcript = "", response = "") }

        val manager = SpeechRecognizerManager(
            context = getApplication(),
            onPartialResult = { partial -> _uiState.update { it.copy(transcript = partial) } },
            onResult = { finalText -> handleRecognizedSpeech(finalText) },
            onError = { message ->
                _uiState.update { it.copy(orbState = OrbState.IDLE, errorMessage = message) }
            },
            onListeningStateChanged = { isListening ->
                _uiState.update { it.copy(orbState = if (isListening) OrbState.LISTENING else it.orbState) }
            },
        )
        speechRecognizerManager = manager
        manager.startListening()
    }

    private fun handleRecognizedSpeech(text: String) {
        _uiState.update { it.copy(transcript = text, orbState = OrbState.THINKING) }
        history.add(ChatMessage(ChatMessage.Role.USER, text))

        val apiKey = apiKeyStore.getGeminiApiKey()
        if (apiKey.isNullOrBlank()) {
            _uiState.update { it.copy(orbState = OrbState.IDLE, errorMessage = "no_key") }
            return
        }

        viewModelScope.launch {
            when (val result = geminiRepository.sendMessage(apiKey, history.toList())) {
                is GeminiResult.Success -> {
                    history.add(ChatMessage(ChatMessage.Role.ASSISTANT, result.text))
                    _uiState.update { it.copy(response = result.text, orbState = OrbState.SPEAKING) }
                    textToSpeechManager.speak(result.text)
                }
                is GeminiResult.Error -> {
                    _uiState.update {
                        it.copy(orbState = OrbState.ERROR, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun saveApiKey(key: String) {
        apiKeyStore.setGeminiApiKey(key)
        _uiState.update { it.copy(hasApiKey = apiKeyStore.hasGeminiApiKey(), errorMessage = null) }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null, orbState = OrbState.IDLE) }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizerManager?.destroy()
        textToSpeechManager.shutdown()
    }
}
