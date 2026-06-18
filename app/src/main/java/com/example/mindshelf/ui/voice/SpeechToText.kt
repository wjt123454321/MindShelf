package com.example.mindshelf.ui.voice

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.mindshelf.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

class SpeechToTextController(
    private val context: Context,
) {
    companion object {
        const val ERROR_NOT_AVAILABLE = 100
        const val ERROR_USE_INTENT_FALLBACK = 101
    }

    private var recognizer: SpeechRecognizer? = null
    private var destroyed = false
    var isListening: Boolean = false
        private set

    fun isRecognizerAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun isIntentRecognitionAvailable(): Boolean {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        return intent.resolveActivity(context.packageManager) != null
    }

    fun canUseAnyRecognition(): Boolean = isRecognizerAvailable() || isIntentRecognitionAvailable()

    /** @deprecated 使用 [canUseAnyRecognition] */
    fun isAvailable(): Boolean = canUseAnyRecognition()

    fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onListeningChange: (Boolean) -> Unit,
        onError: (Int) -> Unit,
    ) {
        if (destroyed) return
        if (!isRecognizerAvailable()) {
            onError(
                if (isIntentRecognitionAvailable()) ERROR_USE_INTENT_FALLBACK else ERROR_NOT_AVAILABLE,
            )
            return
        }
        stop()
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        if (destroyed) return
                        isListening = true
                        onListeningChange(true)
                    }

                    override fun onBeginningOfSpeech() = Unit

                    override fun onRmsChanged(rmsdB: Float) = Unit

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        if (destroyed) return
                        isListening = false
                        onListeningChange(false)
                    }

                    override fun onError(error: Int) {
                        if (destroyed) return
                        isListening = false
                        onListeningChange(false)
                        onError(error)
                    }

                    override fun onResults(results: Bundle?) {
                        if (destroyed) return
                        isListening = false
                        onListeningChange(false)
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        onFinal(text)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        if (destroyed) return
                        val text = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (text.isNotBlank()) onPartial(text)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }
        } catch (_: Exception) {
            recognizer = null
            onError(
                if (isIntentRecognitionAvailable()) ERROR_USE_INTENT_FALLBACK else ERROR_NOT_AVAILABLE,
            )
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            recognizer?.destroy()
            recognizer = null
            isListening = false
            onError(
                if (isIntentRecognitionAvailable()) ERROR_USE_INTENT_FALLBACK else ERROR_NOT_AVAILABLE,
            )
        }
    }

    fun stop() {
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {
        }
        isListening = false
    }

    fun destroy() {
        destroyed = true
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
        isListening = false
    }
}

@Composable
fun rememberSpeechToTextController(): SpeechToTextController {
    val context = LocalContext.current
    val controller = remember { SpeechToTextController(context) }
    DisposableEffect(controller) {
        onDispose { controller.destroy() }
    }
    return controller
}

data class SpeechUiState(
    val isListening: Boolean = false,
    val speechBaseInput: String = "",
)

fun isNetworkAvailable(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        cm.activeNetwork != null
    } catch (_: SecurityException) {
        true
    } catch (_: Exception) {
        true
    }
}

fun speechErrorMessage(context: Context, errorCode: Int): String = when (errorCode) {
    SpeechToTextController.ERROR_NOT_AVAILABLE -> context.getString(R.string.voice_not_available)
    SpeechToTextController.ERROR_USE_INTENT_FALLBACK -> context.getString(R.string.voice_error_generic)
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    -> context.getString(R.string.voice_error_network)
    SpeechRecognizer.ERROR_AUDIO,
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
    -> context.getString(R.string.voice_error_audio)
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
    -> context.getString(R.string.voice_error_no_match)
    else -> context.getString(R.string.voice_error_generic)
}
