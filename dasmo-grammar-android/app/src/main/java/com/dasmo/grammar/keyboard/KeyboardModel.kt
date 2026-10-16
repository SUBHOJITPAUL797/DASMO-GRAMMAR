package com.dasmo.grammar.keyboard

enum class KeyboardMode {
    ALPHA_LOWER,
    ALPHA_UPPER,
    ALPHA_CAPS_LOCK,
    SYMBOLS,
    MORE_SYMBOLS
}

data class KeyModel(
    val primaryText: String,
    val secondaryText: String? = null,
    val keyCode: Int = KEY_CODE_NORMAL,
    val widthWeight: Float = 1.0f
) {
    companion object {
        const val KEY_CODE_NORMAL = 0
        const val KEY_CODE_SHIFT = -1
        const val KEY_CODE_BACKSPACE = -2
        const val KEY_CODE_ENTER = -3
        const val KEY_CODE_MODE_CHANGE = -4
        const val KEY_CODE_SPACE = -5
        const val KEY_CODE_MORE_SYMBOLS = -6
        const val KEY_CODE_SETTINGS = -7
    }
}
