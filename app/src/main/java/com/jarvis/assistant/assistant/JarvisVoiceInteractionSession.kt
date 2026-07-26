package com.jarvis.assistant.assistant

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.jarvis.assistant.ui.AssistantScreen
import com.jarvis.assistant.ui.AssistantViewModel
import com.jarvis.assistant.ui.theme.JarvisTheme

/**
 * Zeigt die Jarvis-Oberfläche (Kugel + Mikrofon) als System-Assistent-Fenster,
 * z. B. nach Auslösen der Assist-Geste, sobald Jarvis als Standard-Assistent
 * ausgewählt ist. Da eine VoiceInteractionSession kein natürlicher
 * Lifecycle-/ViewModel-/SavedState-Owner ist, werden diese hier manuell
 * bereitgestellt, damit Compose darin funktioniert.
 */
class JarvisVoiceInteractionSession(context: Context) :
    VoiceInteractionSession(context),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var assistantViewModel: AssistantViewModel? = null

    override fun onCreateContentView(): View {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        val application = context.applicationContext as Application
        val viewModel = AssistantViewModel(application)
        assistantViewModel = viewModel

        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@JarvisVoiceInteractionSession)
            setViewTreeViewModelStoreOwner(this@JarvisVoiceInteractionSession)
            setViewTreeSavedStateRegistryOwner(this@JarvisVoiceInteractionSession)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this@JarvisVoiceInteractionSession))

            setContent {
                JarvisTheme {
                    val uiState by viewModel.uiState.collectAsState()
                    AssistantScreen(
                        uiState = uiState,
                        hasMicPermission = true,
                        onRequestMicPermission = {},
                        onMicTapped = { viewModel.onMicTapped() },
                        onSendText = { text -> viewModel.sendTextMessage(text) },
                        onSaveApiKey = { key -> viewModel.saveApiKey(key) },
                        onDismissError = { viewModel.consumeError() },
                    )
                }
            }
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onHide() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onHide()
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        assistantViewModel = null
        super.onDestroy()
    }
}
