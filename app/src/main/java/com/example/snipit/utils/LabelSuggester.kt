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
        val regex = Regex("""\+?[0-9][0-9()\-\s]{9,}""")
        val matches = regex.findAll(text)
        matches.forEach { match ->
            val candidate = match.value
            val digitCount = candidate.count { it.isDigit() }
            if (digitCount >= 10) {
                suggestions.add("Phone")
            }
        }
        val fullUrlRegex = Regex("""https?://\S+""")
        val fallbackUrlRegex = Regex("""(?<!@)(?<!\S)(?:www\.)?[a-zA-Z0-9\-]+\.[a-zA-Z]{2,}(?:/\S*)?(?!\S)""")
        if (fullUrlRegex.containsMatchIn(text) || fallbackUrlRegex.containsMatchIn(text)) {
            suggestions.add("Link")
        }

        return suggestions
    }
}