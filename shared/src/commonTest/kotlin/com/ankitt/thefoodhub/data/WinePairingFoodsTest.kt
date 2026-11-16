package com.ankitt.thefoodhub.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WinePairingFoodsTest {

    private fun food(
        title: String,
        ingredients: List<String> = emptyList(),
        cuisines: List<String> = emptyList(),
        dishTypes: List<String> = emptyList(),
    ) = WinePairingFoods.forRecipe(title, ingredients, cuisines, dishTypes)

    @Test
    fun usesTheMainIngredientNamedInTheTitle() {
        assertEquals("pork", food("Cider-Braised Pork Shoulder With Caramelized Onion and Apple Confit"))
        assertEquals("salmon", food("Smoked Salmon Pasta"))
    }

    @Test
    fun prefersTheMoreSpecificTerm() {
        assertEquals("steak", food("Beef Steak with Herb Butter"))
    }

    @Test
    fun matchesWholeWordsAndPlurals() {
        assertNull(food("Graham Cracker Bars"), "\"ham\" must not match inside \"graham\"")
        assertEquals("scallop", food("Seared Scallops"))
    }

    @Test
    fun usesTheDishWhenTheTitleNamesNoMainIngredient() {
        assertEquals("risotto", food("Lemon Risotto"))
    }

    @Test
    fun fallsBackToIngredientsThenCuisineThenDessert() {
        assertEquals("chicken", food("Weeknight Fajitas", ingredients = listOf("chicken thighs", "bell pepper")))
        assertEquals("thai", food("Pad Se Ew", ingredients = listOf("rice noodles"), cuisines = listOf("Thai", "Asian")))
        assertEquals("dessert", food("Berry Crumble", dishTypes = listOf("dessert")))
    }

    @Test
    fun aDishOnlyCountsInTheTitle() {
        assertNull(food("Roasted Vegetables", ingredients = listOf("curry powder")))
    }
}
