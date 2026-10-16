package com.dasmo.grammar.engine

import android.content.Context
import android.content.SharedPreferences

class DasmoDictionary(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("dasmo_user_dictionary", Context.MODE_PRIVATE)

    fun getWords(): Set<String> {
        return prefs.getStringSet("words", emptySet()) ?: emptySet()
    }

    fun addWord(word: String) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return
        val current = getWords().toMutableSet()
        current.add(trimmed)
        prefs.edit().putStringSet("words", current).apply()
    }

    fun removeWord(word: String) {
        val current = getWords().toMutableSet()
        if (current.remove(word.trim())) {
            prefs.edit().putStringSet("words", current).apply()
        }
    }

    fun containsWord(word: String): Boolean {
        val trimmed = word.trim()
        val words = getWords()
        return words.contains(trimmed) || words.any { it.equals(trimmed, ignoreCase = true) }
    }

    fun clear() {
        prefs.edit().remove("words").apply()
    }
}
