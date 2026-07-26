package com.jarvis.assistant.data

import kotlinx.serialization.json.JsonObject

sealed class ChatMessage {
    data class UserText(val text: String) : ChatMessage()
    data class ModelText(val text: String) : ChatMessage()
    data class ModelFunctionCall(val name: String, val args: JsonObject) : ChatMessage()
    data class FunctionResult(val name: String, val response: JsonObject) : ChatMessage()
}
