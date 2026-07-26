package com.jarvis.assistant.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ToolDeclaration(
    val functionDeclarations: List<FunctionDeclaration>? = null,
    val googleSearch: JsonObject? = null,
)

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

data class FunctionCallRequest(val name: String, val args: JsonObject)
