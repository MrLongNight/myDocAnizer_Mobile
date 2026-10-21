package com.example

import com.example.model.DocRule
import com.example.service.DocRuleRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocRuleMatchingSafetyTest {
    private fun rule(id: String, vararg keywords: String, excludes: List<String> = emptyList()) =
        DocRule(
            id = id,
            name = id,
            matchKeywords = keywords.toList(),
            excludeKeywords = excludes
        )

    @Test
    fun uniqueMatchReturnsRule() {
        val expected = rule("invoice", "acme", "rechnung")
        assertEquals(
            expected,
            DocRuleRepository.findUniqueMatchingRule(
                listOf(expected, rule("insurance", "allianz", "police")),
                "ACME GmbH Rechnung Nr. 123"
            )
        )
    }

    @Test
    fun overlappingRulesDoNotSilentlyChooseFirst() {
        val rules = listOf(
            rule("invoice", "acme", "rechnung"),
            rule("reminder", "acme", "rechnung")
        )
        assertNull(
            DocRuleRepository.findUniqueMatchingRule(
                rules,
                "ACME GmbH Rechnung Nr. 123"
            )
        )
    }

    @Test
    fun excludedKeywordPreventsMatch() {
        val candidate = rule("invoice", "acme", "rechnung", excludes = listOf("mahnung"))
        assertNull(
            DocRuleRepository.findUniqueMatchingRule(
                listOf(candidate),
                "ACME GmbH Rechnung - Mahnung"
            )
        )
    }

    @Test
    fun disabledRuleIsIgnored() {
        val candidate = rule("invoice", "acme", "rechnung").copy(isEnabled = false)
        assertNull(
            DocRuleRepository.findUniqueMatchingRule(
                listOf(candidate),
                "ACME GmbH Rechnung Nr. 123"
            )
        )
    } 
    @Test
    fun overlappingDefinitionsAreDetectedBeforeSaving() {
        val broad = rule("broad", "vodafone")
        val specific = rule("invoice", "vodafone", "rechnung")
        assertEquals("broad", DocRuleRepository.findPotentialRuleConflict(listOf(broad), specific)?.id)
        assertEquals(true, DocRuleRepository.hasPotentialRuleConflict(listOf(broad, specific)))
    }

    @Test
    fun exclusionThatAlwaysAppliesToOtherRulePreventsDefinitionConflict() {
        val broad = rule("broad", "vodafone", excludes = listOf("rechnung"))
        val invoice = rule("invoice", "vodafone", "rechnung")
        assertNull(DocRuleRepository.findPotentialRuleConflict(listOf(broad), invoice))
        assertEquals(false, DocRuleRepository.hasPotentialRuleConflict(listOf(broad, invoice)))
    }

    @Test
    fun disjointRulesCanCoexist() {
        assertEquals(
            false,
            DocRuleRepository.hasPotentialRuleConflict(
                listOf(rule("vodafone", "vodafone"), rule("allianz", "allianz"))
            )
        )
    }

}
