package com.ankitt.thefoodhub.data

import com.ankitt.thefoodhub.data.remote.dto.RecipeInformationDto
import kotlin.test.Test
import kotlin.test.assertEquals

class MappersTest {

    @Test
    fun spoonacularScoreConvertsToFiveStarRating() {
        assertEquals(4.8, 96.0.toFiveStarRating())
        assertEquals(4.2, 83.0.toFiveStarRating())
        assertEquals(0.0, 0.0.toFiveStarRating())
        assertEquals(5.0, 100.0.toFiveStarRating())
        assertEquals(5.0, 130.0.toFiveStarRating(), "out-of-range scores are clamped")
    }

    @Test
    fun fallsBackToHtmlInstructionsWhenNoStructuredStepsExist() {
        val dto = RecipeInformationDto(
            id = 1,
            title = "Soup",
            instructions = "<ol><li>Chop <b>onions</b>.</li><li>Simmer.</li></ol>",
        )

        val steps = dto.toEntity(cachedAtEpochMillis = 0).steps

        assertEquals(listOf("Chop onions.", "Simmer."), steps.map { it.text })
        assertEquals(listOf(1, 2), steps.map { it.number })
    }
}
