package com.jarvis.assistant.ui

enum class OrbState { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

data class AssistantUiState(
    val orbState: OrbState = OrbState.IDLE,
    val transcript: String = "",
    val response: String = "",
    val errorMessage: String? = null,
    val hasApiKey: Boolean = false,
)
