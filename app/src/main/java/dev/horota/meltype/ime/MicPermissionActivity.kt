// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast

/** 入力欄がこの画面にフォーカスを奪われても、認識を最後まで走らせる。結果は VoiceBus に渡す。 */
object VoiceBus {
    var listener: ((String) -> Unit)? = null
}

class MicPermissionActivity : Activity() {
    private var voice: VoiceInput? = null
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "話して"
            textSize = 22f
            setPadding(dp(24), dp(20), dp(24), dp(20))
        })
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            listen()
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) listen() else finish()
    }

    private fun listen() {
        if (started) return
        started = true
        val session = VoiceInput(
            this,
            onText = { text ->
                VoiceBus.listener?.invoke(text)
                finish()
            },
            onEnd = { reason ->
                if (reason == "unavailable") {
                    Toast.makeText(this, "音声を使えません", Toast.LENGTH_SHORT).show()
                }
                finish()
            },
        )
        voice = session
        session.start()
    }

    override fun onDestroy() {
        voice?.stop()
        voice = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQUEST = 1
    }
}
