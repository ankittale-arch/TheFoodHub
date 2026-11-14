package com.ankitt.thefoodhub.domain.repository

import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.domain.model.PopularRecipe
import com.ankitt.thefoodhub.domain.model.RecipeDetail
import com.ankitt.thefoodhub.domain.model.RecipePage
import kotlinx.coroutines.flow.Flow

interface RecipeRepository {

    /** Network-only: results depend on the query and are cheap enough not to cache. */
    suspend fun searchRecipes(query: String, offset: Int, pageSize: Int): Outcome<RecipePage>

    /**
     * Random popular recipes for the home screen. Each call returns a new selection and costs quota,
     * so callers should not re-request on every recomposition or screen visit.
     *
     * The full recipes are also stored in the cache, so opening one afterwards needs no extra call.
     */
    suspend fun getRandomRecipes(
        count: Int,
        includeTags: List<String> = emptyList(),
        excludeTags: List<String> = emptyList(),
    ): Outcome<List<PopularRecipe>>

    /** Emits the cached recipe, or null until [refreshRecipe] stores it. The database is the source of truth. */
    fun observeRecipe(id: Int): Flow<RecipeDetail?>

    /**
     * Fetches the recipe into the cache. Skips the network when it is already cached and
     * [force] is false, which saves Spoonacular quota (each call costs points).
     */
    suspend fun refreshRecipe(id: Int, force: Boolean = false): Outcome<Unit>
}
