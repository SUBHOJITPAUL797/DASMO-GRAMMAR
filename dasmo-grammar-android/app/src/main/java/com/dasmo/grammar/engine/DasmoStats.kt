package com.dasmo.grammar.engine

import org.json.JSONObject

data class DasmoStats(
    val wordCount: Int,
    val charCount: Int,
    val sentenceCount: Int,
    val readingTimeMinutes: Int,
    val readabilityScore: Int,
    val readabilityGrade: String
) {
    companion object {
        fun fromJsonObject(json: JSONObject): DasmoStats {
            return DasmoStats(
                wordCount = json.optInt("wordCount", 0),
                charCount = json.optInt("charCount", 0),
                sentenceCount = json.optInt("sentenceCount", 0),
                readingTimeMinutes = json.optInt("readingTimeMinutes", 1),
                readabilityScore = json.optInt("readabilityScore", 100),
                readabilityGrade = json.optString("readabilityGrade", "Standard")
            )
        }

        fun empty(): DasmoStats {
            return DasmoStats(0, 0, 0, 0, 100, "N/A")
        }
    }
}
