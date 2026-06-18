package com.example.mindshelf.ui.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import java.util.UUID

data class TtsPlaybackState(
    val messageId: String? = null,
    val isSpeaking: Boolean = false,
    val isPaused: Boolean = false,
)

class TextToSpeechController(
    context: Context,
    private val onError: (() -> Unit)? = null,
) {
    private var tts: TextToSpeech? = null
    var state by mutableStateOf(TtsPlaybackState())
        private set

    private var ready = false
    private var lastSpokenText: String? = null
    private var pausing = false
    private var pendingSpeak: Pair<String, String>? = null
    private var pendingChunks: List<String> = emptyList()
    private var chunkIndex = 0

    init {
        tts = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                ready = false
                pendingSpeak = null
                pendingChunks = emptyList()
                state = TtsPlaybackState()
                onError?.invoke()
                return@TextToSpeech
            }
            ready = true
            val engine = tts ?: return@TextToSpeech
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
            }
            when (engine.setLanguage(Locale.forLanguageTag("zh-CN"))) {
                TextToSpeech.LANG_MISSING_DATA,
                TextToSpeech.LANG_NOT_SUPPORTED,
                -> engine.setLanguage(Locale.getDefault())
            }
            pendingSpeak?.let { (id, text) ->
                pendingSpeak = null
                speakInternal(id, text)
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                state = state.copy(isSpeaking = true, isPaused = false)
            }

            override fun onDone(utteranceId: String?) {
                if (chunkIndex < pendingChunks.lastIndex) {
                    chunkIndex++
                    speakChunk(pendingChunks[chunkIndex], TextToSpeech.QUEUE_ADD)
                    return
                }
                state = TtsPlaybackState()
                lastSpokenText = null
                pendingChunks = emptyList()
                chunkIndex = 0
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                resetAfterError()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                resetAfterError()
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (pausing) return
                state = TtsPlaybackState()
                lastSpokenText = null
                pendingChunks = emptyList()
                chunkIndex = 0
            }
        })
    }

    private fun resetAfterError() {
        state = TtsPlaybackState()
        lastSpokenText = null
        pendingChunks = emptyList()
        chunkIndex = 0
        onError?.invoke()
    }

    fun speak(messageId: String, text: String) {
        if (text.isBlank()) return
        if (!ready) {
            pendingSpeak = messageId to text
            state = TtsPlaybackState(messageId = messageId, isSpeaking = true, isPaused = false)
            return
        }
        speakInternal(messageId, text)
    }

    private fun speakInternal(messageId: String, text: String) {
        val engine = tts ?: return
        if (text.isBlank()) return
        lastSpokenText = text
        state = TtsPlaybackState(messageId = messageId, isSpeaking = true, isPaused = false)
        pendingChunks = splitForSpeech(text)
        chunkIndex = 0
        if (!speakChunk(pendingChunks.first(), TextToSpeech.QUEUE_FLUSH)) {
            resetAfterError()
        }
    }

    private fun speakChunk(text: String, queueMode: Int): Boolean {
        val engine = tts ?: return false
        val utteranceId = UUID.randomUUID().toString()
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            engine.speak(text, queueMode, null, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            engine.speak(text, queueMode, null)
        }
        return result != TextToSpeech.ERROR
    }

    fun pause() {
        if (!state.isSpeaking || state.isPaused) return
        pausing = true
        tts?.stop()
        pausing = false
        state = state.copy(isSpeaking = false, isPaused = true)
    }

    fun resume() {
        val messageId = state.messageId ?: return
        val text = lastSpokenText ?: return
        if (!state.isPaused) return
        speak(messageId, text)
    }

    fun stop() {
        pendingSpeak = null
        pendingChunks = emptyList()
        chunkIndex = 0
        tts?.stop()
        state = TtsPlaybackState()
        lastSpokenText = null
    }

    fun isPlayingMessage(messageId: String): Boolean =
        state.messageId == messageId && state.isSpeaking && !state.isPaused

    fun isPausedMessage(messageId: String): Boolean =
        state.messageId == messageId && state.isPaused

    fun destroy() {
        pendingSpeak = null
        pendingChunks = emptyList()
        chunkIndex = 0
        tts?.stop()
        tts?.shutdown()
        tts = null
        state = TtsPlaybackState()
        lastSpokenText = null
        ready = false
    }

    companion object {
        /** Many engines fail silently above ~4000 chars per utterance. */
        private const val MAX_CHUNK_LENGTH = 3500

        internal fun splitForSpeech(text: String): List<String> {
            if (text.length <= MAX_CHUNK_LENGTH) return listOf(text)
            val chunks = mutableListOf<String>()
            var remaining = text
            while (remaining.length > MAX_CHUNK_LENGTH) {
                val slice = remaining.substring(0, MAX_CHUNK_LENGTH)
                val breakAt = slice.lastIndexOfAny(charArrayOf('。', '！', '？', '.', '!', '?', '\n', ' '))
                val cut = if (breakAt > MAX_CHUNK_LENGTH / 2) breakAt + 1 else MAX_CHUNK_LENGTH
                chunks.add(remaining.substring(0, cut).trim())
                remaining = remaining.substring(cut).trim()
            }
            if (remaining.isNotBlank()) chunks.add(remaining)
            return chunks.ifEmpty { listOf(text) }
        }
    }
}

@Composable
fun rememberTextToSpeechController(
    onError: (() -> Unit)? = null,
): TextToSpeechController {
    val context = LocalContext.current
    val controller = remember(onError) { TextToSpeechController(context.applicationContext, onError) }
    DisposableEffect(controller) {
        onDispose { controller.destroy() }
    }
    return controller
}
