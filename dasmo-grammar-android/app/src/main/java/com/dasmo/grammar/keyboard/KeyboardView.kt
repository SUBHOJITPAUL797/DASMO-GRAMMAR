package com.dasmo.grammar.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onKeyAction: ((KeyModel) -> Unit)? = null
    var onBackspaceRepeat: (() -> Unit)? = null

    private var currentMode = KeyboardMode.ALPHA_LOWER
    private val rowsContainer = mutableListOf<LinearLayout>()
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var isRepeatingBackspace = false

    private val backspaceRunnable = object : Runnable {
        override fun run() {
            if (isRepeatingBackspace) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onBackspaceRepeat?.invoke()
                repeatHandler.postDelayed(this, 50L) // 20 deletes/sec
            }
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#0F172A")) // Deep Slate Navy
        setPadding(dp(3), dp(6), dp(3), dp(8))
        buildKeyboard()
    }

    fun setMode(mode: KeyboardMode) {
        currentMode = mode
        buildKeyboard()
    }

    fun getMode(): KeyboardMode = currentMode

    fun toggleShift() {
        currentMode = when (currentMode) {
            KeyboardMode.ALPHA_LOWER -> KeyboardMode.ALPHA_UPPER
            KeyboardMode.ALPHA_UPPER -> KeyboardMode.ALPHA_CAPS_LOCK
            KeyboardMode.ALPHA_CAPS_LOCK -> KeyboardMode.ALPHA_LOWER
            else -> KeyboardMode.ALPHA_LOWER
        }
        buildKeyboard()
    }

    fun toggleSymbols() {
        currentMode = when (currentMode) {
            KeyboardMode.ALPHA_LOWER, KeyboardMode.ALPHA_UPPER, KeyboardMode.ALPHA_CAPS_LOCK -> KeyboardMode.SYMBOLS
            KeyboardMode.SYMBOLS, KeyboardMode.MORE_SYMBOLS -> KeyboardMode.ALPHA_LOWER
        }
        buildKeyboard()
    }

    fun toggleMoreSymbols() {
        currentMode = if (currentMode == KeyboardMode.SYMBOLS) {
            KeyboardMode.MORE_SYMBOLS
        } else {
            KeyboardMode.SYMBOLS
        }
        buildKeyboard()
    }

    private fun buildKeyboard() {
        removeAllViews()
        rowsContainer.clear()

        val layout = when (currentMode) {
            KeyboardMode.ALPHA_LOWER -> getAlphaLayout(false)
            KeyboardMode.ALPHA_UPPER, KeyboardMode.ALPHA_CAPS_LOCK -> getAlphaLayout(true)
            KeyboardMode.SYMBOLS -> getSymbolsLayout()
            KeyboardMode.MORE_SYMBOLS -> getMoreSymbolsLayout()
        }

        for (row in layout) {
            val rowView = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(48)).apply {
                    topMargin = dp(2)
                    bottomMargin = dp(2)
                }
            }

            for (key in row) {
                val keyBtn = createKeyButton(key)
                rowView.addView(keyBtn)
            }

            rowsContainer.add(rowView)
            addView(rowView)
        }
    }

    private fun createKeyButton(key: KeyModel): TextView {
        val tv = TextView(context).apply {
            text = key.primaryText
            textSize = when {
                key.primaryText.length > 2 -> 13f
                key.keyCode != KeyModel.KEY_CODE_NORMAL -> 14f
                else -> 19f
            }
            gravity = Gravity.CENTER
            setTypeface(null, if (key.keyCode != KeyModel.KEY_CODE_NORMAL) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(Color.parseColor("#F8FAFC"))

            background = createKeyBackground(key)

            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, key.widthWeight).apply {
                leftMargin = dp(2)
                rightMargin = dp(2)
            }
        }

        // Special continuous touch handling for Backspace
        if (key.keyCode == KeyModel.KEY_CODE_BACKSPACE) {
            tv.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        view.isPressed = true
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onKeyAction?.invoke(key)
                        isRepeatingBackspace = true
                        repeatHandler.postDelayed(backspaceRunnable, 400L) // 400ms initial hold
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        isRepeatingBackspace = false
                        repeatHandler.removeCallbacks(backspaceRunnable)
                        true
                    }
                    else -> false
                }
            }
        } else {
            tv.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onKeyAction?.invoke(key)
            }
        }

        return tv
    }

    private fun createKeyBackground(key: KeyModel): StateListDrawable {
        val normalColor = when (key.keyCode) {
            KeyModel.KEY_CODE_ENTER -> Color.parseColor("#10B981") // Grammarly / Dasmo Emerald
            KeyModel.KEY_CODE_NORMAL, KeyModel.KEY_CODE_SPACE -> Color.parseColor("#1E293B") // Key surface
            else -> Color.parseColor("#334155") // Functional keys
        }

        val pressedColor = when (key.keyCode) {
            KeyModel.KEY_CODE_ENTER -> Color.parseColor("#059669")
            else -> Color.parseColor("#475569")
        }

        val normalDrawable = GradientDrawable().apply {
            setColor(normalColor)
            cornerRadius = dp(7).toFloat()
        }

        val pressedDrawable = GradientDrawable().apply {
            setColor(pressedColor)
            cornerRadius = dp(7).toFloat()
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
    }

    private fun getAlphaLayout(isUpper: Boolean): List<List<KeyModel>> {
        val r1Chars = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        val r2Chars = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        val r3Chars = listOf("z", "x", "c", "v", "b", "n", "m")

        val shiftLabel = when (currentMode) {
            KeyboardMode.ALPHA_CAPS_LOCK -> "⇪"
            KeyboardMode.ALPHA_UPPER -> "⇧"
            else -> "⇧"
        }

        val row1 = r1Chars.map { KeyModel(if (isUpper) it.uppercase() else it) }
        val row2 = r2Chars.map { KeyModel(if (isUpper) it.uppercase() else it) }

        val row3 = mutableListOf<KeyModel>()
        row3.add(KeyModel(shiftLabel, keyCode = KeyModel.KEY_CODE_SHIFT, widthWeight = 1.4f))
        for (c in r3Chars) {
            row3.add(KeyModel(if (isUpper) c.uppercase() else c))
        }
        row3.add(KeyModel("⌫", keyCode = KeyModel.KEY_CODE_BACKSPACE, widthWeight = 1.4f))

        val row4 = listOf(
            KeyModel("?123", keyCode = KeyModel.KEY_CODE_MODE_CHANGE, widthWeight = 1.3f),
            KeyModel(",", widthWeight = 0.9f),
            KeyModel("space", keyCode = KeyModel.KEY_CODE_SPACE, widthWeight = 4.2f),
            KeyModel(".", widthWeight = 0.9f),
            KeyModel("↵", keyCode = KeyModel.KEY_CODE_ENTER, widthWeight = 1.3f)
        )

        return listOf(row1, row2, row3, row4)
    }

    private fun getSymbolsLayout(): List<List<KeyModel>> {
        val row1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map { KeyModel(it) }
        val row2 = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/").map { KeyModel(it) }
        val row3 = mutableListOf(
            KeyModel("=/<", keyCode = KeyModel.KEY_CODE_MORE_SYMBOLS, widthWeight = 1.4f)
        )
        row3.addAll(listOf("*", "\"", "'", ":", ";", "!", "?").map { KeyModel(it) })
        row3.add(KeyModel("⌫", keyCode = KeyModel.KEY_CODE_BACKSPACE, widthWeight = 1.4f))

        val row4 = listOf(
            KeyModel("ABC", keyCode = KeyModel.KEY_CODE_MODE_CHANGE, widthWeight = 1.3f),
            KeyModel(",", widthWeight = 0.9f),
            KeyModel("space", keyCode = KeyModel.KEY_CODE_SPACE, widthWeight = 4.2f),
            KeyModel(".", widthWeight = 0.9f),
            KeyModel("↵", keyCode = KeyModel.KEY_CODE_ENTER, widthWeight = 1.3f)
        )

        return listOf(row1, row2, row3, row4)
    }

    private fun getMoreSymbolsLayout(): List<List<KeyModel>> {
        val row1 = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆").map { KeyModel(it) }
        val row2 = listOf("£", "€", "¥", "¢", "^", "°", "=", "{", "}", "\\").map { KeyModel(it) }
        val row3 = mutableListOf(
            KeyModel("?123", keyCode = KeyModel.KEY_CODE_MORE_SYMBOLS, widthWeight = 1.4f)
        )
        row3.addAll(listOf("%", "_", "<", ">", "[", "]", "§").map { KeyModel(it) })
        row3.add(KeyModel("⌫", keyCode = KeyModel.KEY_CODE_BACKSPACE, widthWeight = 1.4f))

        val row4 = listOf(
            KeyModel("ABC", keyCode = KeyModel.KEY_CODE_MODE_CHANGE, widthWeight = 1.3f),
            KeyModel(",", widthWeight = 0.9f),
            KeyModel("space", keyCode = KeyModel.KEY_CODE_SPACE, widthWeight = 4.2f),
            KeyModel(".", widthWeight = 0.9f),
            KeyModel("↵", keyCode = KeyModel.KEY_CODE_ENTER, widthWeight = 1.3f)
        )

        return listOf(row1, row2, row3, row4)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isRepeatingBackspace = false
        repeatHandler.removeCallbacks(backspaceRunnable)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
