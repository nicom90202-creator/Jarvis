package com.jarvis.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.jarvis.assistant.R
import com.jarvis.assistant.ui.theme.JarvisBlueDeep
import com.jarvis.assistant.ui.theme.JarvisTextMuted
import com.jarvis.assistant.ui.theme.JarvisWhite

@Composable
fun AssistantScreen(
    uiState: AssistantUiState,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onMicTapped: () -> Unit,
    onSendText: (String) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onDismissError: () -> Unit,
    onRequestExtraPermissions: () -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }

    val statusText = when {
        uiState.errorMessage == "no_key" -> stringResource(R.string.no_key_error)
        uiState.errorMessage != null -> uiState.errorMessage
        uiState.orbState == OrbState.LISTENING -> stringResource(R.string.listening)
        uiState.orbState == OrbState.THINKING -> stringResource(R.string.thinking)
        uiState.orbState == OrbState.SPEAKING -> stringResource(R.string.speaking)
        else -> stringResource(R.string.idle_hint)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisWhite),
    ) {
        IconButton(
            onClick = { showSettings = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.settings_title),
                tint = JarvisTextMuted,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(horizontal = 32.dp, vertical = 88.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            OrbView(
                state = uiState.orbState,
                modifier = Modifier
                    .size(220.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (hasMicPermission) onMicTapped() else onRequestMicPermission()
                    },
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = if (uiState.orbState == OrbState.ERROR || uiState.errorMessage != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    JarvisTextMuted
                },
                textAlign = TextAlign.Center,
            )

            if (uiState.transcript.isNotBlank()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = uiState.transcript,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }

            if (uiState.response.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = uiState.response,
                    style = MaterialTheme.typography.bodyLarge,
                    color = JarvisBlueDeep,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            fun submit() {
                if (textInput.isNotBlank()) {
                    onSendText(textInput)
                    textInput = ""
                }
            }

            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.text_input_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = { submit() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.send),
                    tint = JarvisBlueDeep,
                )
            }
        }
    }

    if (showSettings) {
        ApiKeySettingsDialog(
            initialKey = "",
            onDismiss = { showSettings = false },
            onSave = { key ->
                onSaveApiKey(key)
                showSettings = false
                onDismissError()
            },
            onRequestExtraPermissions = onRequestExtraPermissions,
        )
    }
}
