package com.assistant.gemini.service

import android.service.voice.VoiceInteractionService

/**
 * System entry point for the VoiceInteractionService.
 * When the user designates this app as their Default Assistant in system settings,
 * the Android OS communicates with this service to manage sessions and triggers.
 */
class GeminiVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        // Assistant service is registered and bound by the Android system
    }

    override fun onShutdown() {
        super.onShutdown()
    }
}
