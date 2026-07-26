package com.jarvis.assistant.assistant

import android.service.voice.VoiceInteractionService

/**
 * Registriert Jarvis beim System als Anbieter eines Digitalen Assistenten
 * (Einstellungen > Apps > Standard-Apps > Digitaler Assistent). Die eigentliche
 * UI/Logik läuft in [JarvisVoiceInteractionSession], sobald das System eine
 * Session anfordert (z. B. per Assist-Geste).
 */
class JarvisVoiceInteractionService : VoiceInteractionService()
