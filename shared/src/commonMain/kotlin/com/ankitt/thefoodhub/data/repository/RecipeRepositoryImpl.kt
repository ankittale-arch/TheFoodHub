package com.ankitt.thefoodhub.data.repository

import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.core.map
import com.ankitt.thefoodhub.data.local.RecipeDao
import com.ankitt.thefoodhub.data.remote.SpoonacularApi
import com.ankitt.thefoodhub.data.toDomain
import com.ankitt.thefoodhub.data.toEntity
import com.ankitt.thefoodhub.data.toPopularRecipe
import com.ankitt.thefoodhub.domain.model.PopularRecipe
import com.ankitt.thefoodhub.domain.model.RecipeDetail
import com.ankitt.thefoodhub.domain.model.RecipePage
import com.ankitt.thefoodhub.domain.model.SearchCriteria
import com.ankitt.thefoodhub.domain.repository.RecipeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

internal class RecipeRepositoryImpl(
    private val api: SpoonacularApi,
    private val recipeDao: RecipeDao,
    private val clock: Clock,
) : RecipeRepository {

    override suspend fun searchRecipes(criteria: SearchCriteria, offset: Int, pageSize: Int): Outcome<RecipePage> =
        api.searchRecipes(criteria = criteria, offset = offset, number = pageSize).map { it.toDomain() }

    override suspend fun getRandomRecipes(
        count: Int,
        includeTags: List<String>,
        excludeTags: List<String>,
    ): Outcome<List<PopularRecipe>> {
        val number = count.coerceIn(1, MAX_RANDOM_RECIPES)
        return when (val outcome = api.getRandomRecipes(number, includeTags, excludeTags)) {
            is Outcome.Success -> {
                val recipes = outcome.value.recipes.distinctBy { it.id }
                val now = clock.now().toEpochMilliseconds()
                // The response already has full details; caching them makes opening a card free.
                recipes.forEach { recipeDao.upsert(it.toEntity(cachedAtEpochMillis = now)) }
                Outcome.Success(recipes.map { it.toPopularRecipe() })
            }
            is Outcome.Failure -> outcome
        }
    }

    override fun observeRecipe(id: Int): Flow<RecipeDetail?> =
        recipeDao.observe(id)
            .map { it?.toDomain() }
            .distinctUntilChanged()

    override suspend fun refreshRecipe(id: Int, force: Boolean): Outcome<Unit> {
        if (!force && recipeDao.exists(id)) return Outcome.Success(Unit)

        return when (val outcome = api.getRecipeInformation(id)) {
            is Outcome.Success -> {
                recipeDao.upsert(outcome.value.toEntity(cachedAtEpochMillis = clock.now().toEpochMilliseconds()))
                Outcome.Success(Unit)
            }
            is Outcome.Failure -> outcome
        }
    }

    private companion object {
        /** Spoonacular's limit for `number`. */
        const val MAX_RANDOM_RECIPES = 100
    }
}
