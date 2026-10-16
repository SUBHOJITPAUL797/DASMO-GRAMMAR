package com.dasmo.grammar.engine

enum class DasmoDialect(val engineName: String, val displayName: String, val localeCode: String) {
    AMERICAN("American", "American English (US)", "en-US"),
    BRITISH("British", "British English (UK)", "en-GB"),
    AUSTRALIAN("Australian", "Australian English (AU)", "en-AU"),
    CANADIAN("Canadian", "Canadian English (CA)", "en-CA"),
    INDIAN("Indian", "Indian English (IN)", "en-IN");

    companion object {
        fun fromEngineName(name: String?): DasmoDialect {
            return entries.firstOrNull { it.engineName.equals(name, ignoreCase = true) } ?: AMERICAN
        }
    }
}
