package com.example.snipit.utils

object LabelSuggester {
    fun suggestLabels(text: String): List<String> {
        val suggestions = mutableListOf<String>()
        val lower = text.lowercase()

        if (Regex("\\b\\d{4,6}\\b").containsMatchIn(text) && lower.contains("otp")) {
            suggestions.add("OTP")
        }
        if (Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-z]{2,}").containsMatchIn(text)) {
            suggestions.add("Email")
        }
        if (Regex("\\+?[0-9][0-9()\\-\\s]{7,}").containsMatchIn(text)) {
            suggestions.add("Phone")
        }
        if (Regex("https?://\\S+").containsMatchIn(text)) {
            suggestions.add("Link")
        }

        return suggestions
    }
}