package com.dasmo.grammar.engine

import org.json.JSONObject

data class DasmoLint(
    val id: Int,
    val start: Int,
    val end: Int,
    val problem: String,
    val kind: String,
    val category: String,
    val severity: String,
    val colorHex: String,
    val message: String,
    val messageHtml: String,
    val suggestions: List<String>
) {
    val primarySuggestion: String?
        get() = suggestions.firstOrNull()

    val length: Int
        get() = (end - start).coerceAtLeast(0)

    companion object {
        fun fromJsonObject(json: JSONObject): DasmoLint {
            val suggestionsArray = json.optJSONArray("suggestions")
            val suggestionsList = mutableListOf<String>()
            if (suggestionsArray != null) {
                for (i in 0 until suggestionsArray.length()) {
                    suggestionsList.add(suggestionsArray.getString(i))
                }
            }

            return DasmoLint(
                id = json.optInt("id", 0),
                start = json.optInt("start", 0),
                end = json.optInt("end", 0),
                problem = json.optString("problem", ""),
                kind = json.optString("kind", "Grammar"),
                category = json.optString("category", "Correctness"),
                severity = json.optString("severity", "warning"),
                colorHex = json.optString("color", "#EF4444"),
                message = json.optString("message", ""),
                messageHtml = json.optString("messageHtml", ""),
                suggestions = suggestionsList
            )
        }
    }
}
