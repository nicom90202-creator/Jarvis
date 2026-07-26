package com.jarvis.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jarvis.assistant.ui.AssistantScreen
import com.jarvis.assistant.ui.AssistantViewModel
import com.jarvis.assistant.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AssistantViewModel by viewModels()
    private var hasMicPermission by mutableStateOf(false)

    private val requestMicPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            viewModel.onMicTapped()
        }
    }

    private val requestExtraPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* Ergebnisse werden bei Bedarf über DeviceActions.hasPermission neu geprüft. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasMicPermission = hasRecordAudioPermission()

        setContent {
            JarvisTheme {
                val uiState by viewModel.uiState.collectAsState()

                AssistantScreen(
                    uiState = uiState,
                    hasMicPermission = hasMicPermission,
                    onRequestMicPermission = {
                        requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onMicTapped = { viewModel.onMicTapped() },
                    onSendText = { text -> viewModel.sendTextMessage(text) },
                    onSaveApiKey = { key -> viewModel.saveApiKey(key) },
                    onDismissError = { viewModel.consumeError() },
                    onRequestExtraPermissions = {
                        requestExtraPermissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.WRITE_CALENDAR,
                            ),
                        )
                    },
                )
            }
        }
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
