package com.ankitt.thefoodhub.domain.repository

import com.ankitt.thefoodhub.domain.model.Cookbook
import com.ankitt.thefoodhub.domain.model.CookbookNameResult
import com.ankitt.thefoodhub.domain.model.RecipeSummary
import kotlinx.coroutines.flow.Flow

/**
 * Cookbooks organize favorites. Only favorites can be filed, and removing a favorite takes it out
 * of every cookbook it was in.
 */
interface CookbookRepository {

    /** Sorted by name, ignoring case. */
    fun observeCookbooks(): Flow<List<Cookbook>>

    /** Most recently added first. */
    fun observeRecipes(cookbookId: Long): Flow<List<RecipeSummary>>

    /** The cookbooks [recipeId] is filed in. */
    fun observeCookbookIds(recipeId: Int): Flow<Set<Long>>

    suspend fun cookbookIds(recipeId: Int): Set<Long>

    suspend fun createCookbook(name: String): CookbookNameResult

    suspend fun renameCookbook(cookbookId: Long, name: String): CookbookNameResult

    /** Deletes the cookbook only; its recipes stay in favorites. */
    suspend fun deleteCookbook(cookbookId: Long)

    /** Files a favorite in [cookbookIds]. Cookbooks that no longer exist, and recipes that aren't favorites, are skipped. */
    suspend fun addRecipe(recipeId: Int, cookbookIds: Set<Long>)

    suspend fun removeRecipe(recipeId: Int, cookbookId: Long)
}
