package com.ankitt.thefoodhub.domain

import com.ankitt.thefoodhub.domain.model.IngredientCatalog
import com.ankitt.thefoodhub.domain.model.Nutrient
import com.ankitt.thefoodhub.domain.model.NutrientFilter
import com.ankitt.thefoodhub.domain.model.NutrientPreset
import com.ankitt.thefoodhub.domain.model.NutrientRange
import com.ankitt.thefoodhub.domain.model.SearchCriteria
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchCriteriaTest {

    @Test
    fun criteriaIsEmptyOnlyWithoutQueryIngredientsAndNutrients() {
        assertTrue(SearchCriteria(query = "  ").isEmpty)
        assertFalse(SearchCriteria(ingredients = listOf("egg")).isEmpty)
        assertFalse(SearchCriteria(nutrients = NutrientFilter(fat = NutrientRange(max = 10))).isEmpty)
    }

    @Test
    fun nutrientFilterReadsAndWritesEachNutrient() {
        val filter = NutrientFilter()
            .with(Nutrient.FAT, NutrientRange(max = 10))
            .with(Nutrient.CALORIES, NutrientRange(min = 200))

        assertEquals(NutrientRange(max = 10), filter.range(Nutrient.FAT))
        assertEquals(listOf(Nutrient.CALORIES, Nutrient.FAT), filter.activeNutrients)
    }

    @Test
    fun presetsMergeAndRemoveOnlyTheirOwnNutrients() {
        val filter = NutrientFilter()
            .applying(NutrientPreset.HIGH_PROTEIN)
            .applying(NutrientPreset.LOW_CARB)

        assertTrue(filter.hasPreset(NutrientPreset.HIGH_PROTEIN))
        assertTrue(filter.hasPreset(NutrientPreset.LOW_CARB))
        assertEquals(NutrientFilter(carbs = NutrientRange(max = 20)), filter.removing(NutrientPreset.HIGH_PROTEIN))
    }

    @Test
    fun normalizeTrimsLowercasesAndCollapsesSpaces() {
        assertEquals("bell pepper", IngredientCatalog.normalize("  Bell   PEPPER "))
        assertNull(IngredientCatalog.normalize("   "))
    }

    @Test
    fun suggestionsPreferPrefixMatchesAndSkipChosenOnes() {
        val suggestions = IngredientCatalog.suggestions("pe", exclude = listOf("peas"))

        assertEquals("peanut butter", suggestions.first())
        assertTrue("bell pepper" in suggestions)
        assertFalse("peas" in suggestions)
    }

    @Test
    fun addSkipsDuplicatesAndStopsAtTheLimit() {
        assertEquals(listOf("egg"), IngredientCatalog.add(listOf("egg"), " EGG "))

        val full = (1..IngredientCatalog.MAX_INGREDIENTS).map { "item $it" }
        assertEquals(full, IngredientCatalog.add(full, "rice"))
    }
}
