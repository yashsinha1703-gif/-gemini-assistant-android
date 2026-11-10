package com.assistant.gemini.service

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Factory service that instantiates GeminiVoiceInteractionSession
 * each time the assistant is invoked by user action.
 */
class GeminiVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return GeminiVoiceInteractionSession(this)
    }
}
