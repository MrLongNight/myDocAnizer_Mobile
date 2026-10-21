package com.example.util

import java.util.Locale

/**
 * Conservative local password-strength estimate for UI feedback.
 * This is not a substitute for a breached-password database, but it avoids
 * calling common words and predictable sequences "very strong" just because
 * punctuation and digits were appended.
 */
object PasswordStrengthEstimator {
    private val commonPatterns = listOf(
        "password", "passwort", "test", "admin", "qwerty", "letmein",
        "welcome", "iloveyou", "changeme", "1234", "abcd", "asdf"
    )

    fun score(password: String): Int {
        if (password.isBlank()) return 0
        val normalized = password.lowercase(Locale.ROOT)
        val hasCommonPattern = commonPatterns.any(normalized::contains)
        val hasSequentialPattern = password.windowed(4).any { window ->
            window.all { it.isDigit() } && window.zipWithNext().all { (a, b) -> b.code == a.code + 1 } ||
                window.all { it.isLetter() } && window.lowercase(Locale.ROOT).zipWithNext()
                    .all { (a, b) -> b.code == a.code + 1 }
        }
        val hasLongRepeatedRun = password.windowed(3).any { it[0] == it[1] && it[1] == it[2] }
        if (hasCommonPattern || hasSequentialPattern) return if (password.length >= 16) 1 else 0

        val classes = listOf(
            password.any(Char::isLowerCase),
            password.any(Char::isUpperCase),
            password.any(Char::isDigit),
            password.any { !it.isLetterOrDigit() }
        ).count { it }

        var score = when {
            password.length < 8 -> 0
            password.length < 12 -> 1
            password.length < 16 -> 2
            password.length < 20 -> 3
            else -> 4
        }
        if (classes >= 3) score++
        if (classes == 4 && password.length >= 12) score++
        val uniqueRatio = password.toSet().size.toFloat() / password.length.coerceAtLeast(1)
        if (uniqueRatio < 0.45f) score--
        if (hasLongRepeatedRun) score = minOf(score, 2)
        return score.coerceIn(0, 5)
    }
}
