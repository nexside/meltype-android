// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.ime

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle

/** 音声入力のマイク許可だけを頼む。許可のダイアログが閉じたら自分も閉じる。 */
class MicPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }
        requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finish()
    }

    private companion object {
        const val REQUEST = 1
    }
}
