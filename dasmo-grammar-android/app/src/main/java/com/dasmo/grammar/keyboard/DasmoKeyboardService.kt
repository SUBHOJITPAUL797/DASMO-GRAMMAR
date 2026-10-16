package com.dasmo.grammar.keyboard

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import com.dasmo.grammar.engine.DasmoEngine
import com.dasmo.grammar.engine.DasmoLint
import com.dasmo.grammar.engine.DasmoLogger
import com.dasmo.grammar.ui.SettingsActivity

class DasmoKeyboardService : InputMethodService() {

    companion object {
        private const val TAG = "DasmoKeyboardService"
        private const val BUFFER_WINDOW = 250
        private const val DOUBLE_SPACE_THRESHOLD_MS = 500L
    }

    private lateinit var engine: DasmoEngine
    private lateinit var ribbonView: SuggestionRibbonView
    private lateinit var keyboardView: KeyboardView
    private val handler = Handler(Looper.getMainLooper())

    private var activeLints: List<DasmoLint> = emptyList()
    private var lastAnalyzedText: String = ""
    private var lastSpaceTime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        DasmoLogger.i(TAG, "DasmoKeyboardService created on Android ${Build.VERSION.RELEASE} (${Build.MANUFACTURER} ${Build.MODEL})")
        engine = DasmoEngine.getInstance(this)
    }

    override fun onCreateInputView(): View {
        DasmoLogger.d(TAG, "onCreateInputView invoked")
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        ribbonView = SuggestionRibbonView(this).apply {
            onSuggestionClicked = { lint, replacement ->
                applySuggestionSafely(lint, replacement)
            }
            onIgnoreClicked = { lint ->
                engine.addCustomWord(lint.problem)
                triggerGrammarCheck()
            }
            onSettingsClicked = {
                val intent = Intent(this@DasmoKeyboardService, SettingsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
        }

        keyboardView = KeyboardView(this).apply {
            onKeyAction = { key ->
                handleKeyPress(key)
            }
            onBackspaceRepeat = {
                handleBackspace()
            }
        }

        rootLayout.addView(ribbonView)
        rootLayout.addView(keyboardView)

        return rootLayout
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        DasmoLogger.d(TAG, "onStartInputView: package=${info?.packageName}, fieldId=${info?.fieldId}")
        triggerGrammarCheck()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        triggerGrammarCheck()
    }

    private fun handleKeyPress(key: KeyModel) {
        val ic = currentInputConnection ?: return

        try {
            when (key.keyCode) {
                KeyModel.KEY_CODE_SHIFT -> {
                    keyboardView.toggleShift()
                }
                KeyModel.KEY_CODE_MODE_CHANGE -> {
                    keyboardView.toggleSymbols()
                }
                KeyModel.KEY_CODE_MORE_SYMBOLS -> {
                    keyboardView.toggleMoreSymbols()
                }
                KeyModel.KEY_CODE_BACKSPACE -> {
                    handleBackspace()
                }
                KeyModel.KEY_CODE_SPACE -> {
                    handleSpace(ic)
                }
                KeyModel.KEY_CODE_ENTER -> {
                    handleEnter(ic)
                }
                else -> {
                    ic.commitText(key.primaryText, 1)
                    if (keyboardView.getMode() == KeyboardMode.ALPHA_UPPER) {
                        keyboardView.setMode(KeyboardMode.ALPHA_LOWER)
                    }
                    triggerGrammarCheck()
                }
            }
        } catch (e: Exception) {
            DasmoLogger.e(TAG, "Error processing key ${key.primaryText}", e)
        }
    }

    private fun handleBackspace() {
        val ic = currentInputConnection ?: return
        try {
            ic.deleteSurroundingText(1, 0)
            triggerGrammarCheck()
        } catch (e: Exception) {
            DasmoLogger.e(TAG, "Error executing backspace", e)
        }
    }

    private fun handleSpace(ic: InputConnection) {
        val now = System.currentTimeMillis()
        val textBefore = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""

        // Double tap space shortcut: convert " " + " " -> ". "
        if (now - lastSpaceTime < DOUBLE_SPACE_THRESHOLD_MS && textBefore.endsWith(" ") && !textBefore.endsWith(". ")) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            keyboardView.setMode(KeyboardMode.ALPHA_UPPER)
            lastSpaceTime = 0
        } else {
            ic.commitText(" ", 1)
            lastSpaceTime = now

            if (keyboardView.getMode() == KeyboardMode.ALPHA_UPPER) {
                keyboardView.setMode(KeyboardMode.ALPHA_LOWER)
            }
        }
        triggerGrammarCheck()
    }

    private fun handleEnter(ic: InputConnection) {
        val action = currentInputEditorInfo?.imeOptions ?: EditorInfo.IME_NULL
        val actionId = action and EditorInfo.IME_MASK_ACTION
        if (actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(actionId)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
        triggerGrammarCheck()
    }

    private fun triggerGrammarCheck() {
        val ic = currentInputConnection ?: return
        val textBefore = try {
            ic.getTextBeforeCursor(BUFFER_WINDOW, 0)?.toString() ?: ""
        } catch (e: Exception) {
            DasmoLogger.w(TAG, "Unable to read textBeforeCursor: ${e.message}")
            return
        }

        // Auto-shift check: if ending with sentence terminator
        if (textBefore.matches(Regex(".*[.?!]\\s+$")) && keyboardView.getMode() == KeyboardMode.ALPHA_LOWER) {
            keyboardView.setMode(KeyboardMode.ALPHA_UPPER)
        }

        if (textBefore == lastAnalyzedText) return
        lastAnalyzedText = textBefore

        if (textBefore.isBlank()) {
            activeLints = emptyList()
            ribbonView.setLints(emptyList())
            return
        }

        engine.lintDebounced(textBefore, 250L) { lints ->
            handler.post {
                activeLints = lints
                ribbonView.setLints(lints)
            }
        }
    }

    private fun applySuggestionSafely(lint: DasmoLint, replacement: String) {
        val ic = currentInputConnection ?: return
        val textBefore = try {
            ic.getTextBeforeCursor(BUFFER_WINDOW, 0)?.toString() ?: return
        } catch (e: Exception) {
            DasmoLogger.e(TAG, "Failed reading text before cursor during apply", e)
            return
        }

        val problem = lint.problem
        val index = textBefore.lastIndexOf(problem)
        if (index != -1) {
            try {
                val deleteBeforeCursor = (textBefore.length - index).coerceAtLeast(0)
                ic.deleteSurroundingText(deleteBeforeCursor, 0)
                val restoredSuffix = textBefore.substring((index + problem.length).coerceAtMost(textBefore.length))
                ic.commitText(replacement + restoredSuffix, 1)

                vibrateKeyFeedback()

                lastAnalyzedText = ""
                triggerGrammarCheck()
            } catch (e: Exception) {
                DasmoLogger.e(TAG, "Error applying replacement '$replacement' for '${lint.problem}'", e)
            }
        }
    }

    private fun vibrateKeyFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (v?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(20)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore vibration errors on devices without vibrator
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        DasmoLogger.i(TAG, "DasmoKeyboardService destroyed")
        handler.removeCallbacksAndMessages(null)
    }
}
