// SPDX-License-Identifier: GPL-3.0-or-later
package dev.horota.meltype.input

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * 入力欄ごとの動き。
 * bypass: Meltype を通さずキーをすべてアプリに渡す (学習もしない)。
 * startDirect: 英数 (直接入力) から始める。codeInput: 本家のコード入力モード (meltype_set_code_input)。
 * noLearning: 入力は動かして学習だけ止める (meltype_set_private)。
 * surroundingText: 入力欄がカーソルの前後の文字を返せるか。TYPE_NULL (Termux など) は常に空を返すので false。
 */
/** 画面下に出す数字キー。NONE なら出さない。 */
enum class DigitPadKind { NONE, DIGITS, PHONE, DECIMAL }

data class FieldPolicy(
    val bypass: Boolean,
    val startDirect: Boolean,
    val codeInput: Boolean,
    val noLearning: Boolean = false,
    val surroundingText: Boolean = true,
    val digitPad: DigitPadKind = DigitPadKind.NONE,
)

object EditorPolicy {
    /** 最初から入れておくコード入力のアプリ (設定画面で変えられる)。 */
    val DEFAULT_CODE_APPS: Set<String> = setOf(
        "com.termux",
        "com.foxdebug.acode",
    )

    private val BYPASS = FieldPolicy(bypass = true, startDirect = true, codeInput = false)

    /** imeOptions は EditorInfo.imeOptions。IME_FLAG_NO_PERSONALIZED_LEARNING (シークレットタブなど) なら学習しない。 */
    fun of(inputType: Int, packageName: String?, codeApps: Set<String>, imeOptions: Int = 0): FieldPolicy =
        fieldPolicy(inputType, packageName, codeApps).let { policy ->
            if (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) policy.copy(noLearning = true) else policy
        }

    private fun fieldPolicy(inputType: Int, packageName: String?, codeApps: Set<String>): FieldPolicy {
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val codeApp = packageName != null && packageName in codeApps
        if (isPassword(inputClass, variation)) {
            // 数字の暗証番号だけ、変換は通さず数字行を出す。文字のパスワードは今までどおり何も出さない。
            if (inputClass == InputType.TYPE_CLASS_NUMBER) {
                return FieldPolicy(bypass = true, startDirect = true, codeInput = false, noLearning = true, digitPad = DigitPadKind.DIGITS)
            }
            return BYPASS
        }
        if (inputClass == InputType.TYPE_NULL) {
            return if (codeApp) {
                FieldPolicy(bypass = false, startDirect = false, codeInput = true, surroundingText = false)
            } else {
                BYPASS
            }
        }
        val digits = digitPadOf(inputClass, inputType)
        val startDirect = when (inputClass) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> true
            InputType.TYPE_CLASS_TEXT -> variation in DIRECT_TEXT_VARIATIONS
            else -> false
        }
        return FieldPolicy(
            bypass = false,
            startDirect = startDirect,
            codeInput = codeApp,
            noLearning = digits != DigitPadKind.NONE,
            digitPad = digits,
        )
    }

    private val DIRECT_TEXT_VARIATIONS = setOf(
        InputType.TYPE_TEXT_VARIATION_URI,
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
    )

    private fun digitPadOf(inputClass: Int, inputType: Int): DigitPadKind = when (inputClass) {
        InputType.TYPE_CLASS_PHONE -> DigitPadKind.PHONE
        InputType.TYPE_CLASS_NUMBER ->
            if (inputType and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0) DigitPadKind.DECIMAL else DigitPadKind.DIGITS
        InputType.TYPE_CLASS_DATETIME -> DigitPadKind.DIGITS
        else -> DigitPadKind.NONE
    }

    private fun isPassword(inputClass: Int, variation: Int): Boolean = when (inputClass) {
        InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else -> false
    }
}
