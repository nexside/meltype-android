// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * 端末の音声認識。結果の文字列だけを返し、ログには残さない。
 * マイクの許可が無いときは onEnd("permit")、認識が無いときは onEnd("unavailable")。
 */
class VoiceInput(
    private val context: Context,
    private val onText: (String) -> Unit,
    private val onEnd: (String?) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null

    fun start(): Boolean {
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onEnd("permit")
            return false
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onEnd("unavailable")
            return false
        }
        stop()
        val rec = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: RuntimeException) {
            onEnd("unavailable")
            return false
        }
        recognizer = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onError(error: Int) {
                stop()
                onEnd(null)
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                stop()
                if (text.isNotEmpty()) onText(text) else onEnd(null)
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        return try {
            rec.startListening(intent)
            true
        } catch (e: SecurityException) {
            stop()
            onEnd("permit")
            false
        } catch (e: RuntimeException) {
            stop()
            onEnd("unavailable")
            false
        }
    }

    fun stop() {
        val rec = recognizer ?: return
        recognizer = null
        try {
            rec.cancel()
        } catch (e: RuntimeException) {
            // もう止まっている
        }
        try {
            rec.destroy()
        } catch (e: RuntimeException) {
            // 破棄できなくても入力は続ける
        }
    }
}
