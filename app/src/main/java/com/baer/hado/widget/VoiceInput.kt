package com.baer.hado.widget

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.baer.hado.R

/**
 * Voice input: the system speech dialog when a speech app provides one (no permission needed),
 * otherwise the in-app SpeechRecognizer, which needs RECORD_AUDIO.
 */
object VoiceInput {

    fun recognizerIntent(prompt: String?): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .apply { if (!prompt.isNullOrBlank()) putExtra(RecognizerIntent.EXTRA_PROMPT, prompt) }

    fun hasSystemDialog(context: Context): Boolean =
        recognizerIntent(null).resolveActivity(context.packageManager) != null

    fun hasRecognitionService(context: Context): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    fun isAvailable(context: Context): Boolean =
        hasSystemDialog(context) || hasRecognitionService(context)
}

@Stable
class VoiceInputController(val available: Boolean) {
    var listening by mutableStateOf(false)
        internal set
    internal var onStart: () -> Unit = {}
    internal var onStop: () -> Unit = {}

    fun start() = onStart()
    fun stop() = onStop()
}

@Composable
fun rememberVoiceInput(
    prompt: String,
    onPartial: (String) -> Unit,
    onResult: (String) -> Unit
): VoiceInputController {
    val context = LocalContext.current
    val hasDialog = remember { VoiceInput.hasSystemDialog(context) }
    val hasService = remember { VoiceInput.hasRecognitionService(context) }
    val controller = remember { VoiceInputController(available = hasDialog || hasService) }
    val latestOnPartial by rememberUpdatedState(onPartial)
    val latestOnResult by rememberUpdatedState(onResult)
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    fun startRecognizer(onDevice: Boolean) {
        recognizer?.destroy()
        val r = if (onDevice) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.let(latestOnPartial)
            }

            override fun onResults(results: Bundle?) {
                controller.listening = false
                firstResult(results)?.let(latestOnResult)
            }

            override fun onError(error: Int) {
                Log.w("HAdo", "Speech recognizer error $error (onDevice=$onDevice)")
                val silence = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                // The on-device engine may lack the language; retry once with the default service.
                if (onDevice && !silence) {
                    startRecognizer(onDevice = false)
                    return
                }
                controller.listening = false
                if (!silence) {
                    Toast.makeText(context, R.string.voice_error, Toast.LENGTH_SHORT).show()
                }
            }
        })
        r.startListening(
            VoiceInput.recognizerIntent(null).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        )
        recognizer = r
        controller.listening = true
    }

    fun startInApp() {
        startRecognizer(onDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
    }

    val dialogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        controller.listening = false
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) latestOnResult(text)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startInApp()
        } else {
            Toast.makeText(context, R.string.voice_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    controller.onStart = start@{
        if (controller.listening) return@start
        if (hasDialog) {
            try {
                controller.listening = true
                dialogLauncher.launch(VoiceInput.recognizerIntent(prompt))
                return@start
            } catch (e: ActivityNotFoundException) {
                controller.listening = false
                Log.w("HAdo", "Speech dialog not found", e)
            }
        }
        if (!hasService) return@start
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) startInApp() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    controller.onStop = {
        recognizer?.stopListening()
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizer?.destroy()
            recognizer = null
        }
    }

    return controller
}
