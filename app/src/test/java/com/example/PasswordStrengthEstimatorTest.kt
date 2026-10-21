package com.example

import com.example.util.PasswordStrengthEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordStrengthEstimatorTest {
    @Test
    fun commonPasswordWithDigitsAndPunctuationIsNotStrong() {
        assertTrue(PasswordStrengthEstimator.score("TestPasswort1234.") <= 1)
    }

    @Test
    fun sequentialDigitsArePenalized() {
        assertTrue(PasswordStrengthEstimator.score("MeinGeheimes1234") <= 1)
    }

    @Test
    fun blankPasswordHasNoStrength() {
        assertEquals(0, PasswordStrengthEstimator.score(""))
    }

    @Test
    fun longRandomLookingPasswordCanBeVeryStrong() {
        assertTrue(PasswordStrengthEstimator.score("vK8!rT2#pL9@xQ4z") >= 4)
    }

    @Test
    fun repeatedCharactersLimitStrength() {
        assertTrue(PasswordStrengthEstimator.score("aaaBBB111!!!") <= 2)
    }
}
