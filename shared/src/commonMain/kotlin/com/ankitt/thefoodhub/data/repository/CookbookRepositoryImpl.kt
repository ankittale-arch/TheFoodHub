package com.ankitt.thefoodhub.data.repository

import com.ankitt.thefoodhub.data.local.CookbookDao
import com.ankitt.thefoodhub.data.local.CookbookEntity
import com.ankitt.thefoodhub.data.toDomain
import com.ankitt.thefoodhub.domain.model.Cookbook
import com.ankitt.thefoodhub.domain.model.CookbookNameError
import com.ankitt.thefoodhub.domain.model.CookbookNameResult
import com.ankitt.thefoodhub.domain.model.RecipeSummary
import com.ankitt.thefoodhub.domain.repository.CookbookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

internal class CookbookRepositoryImpl(
    private val cookbookDao: CookbookDao,
    private val clock: Clock,
) : CookbookRepository {

    override fun observeCookbooks(): Flow<List<Cookbook>> =
        cookbookDao.observeAllWithCounts()
            .map { rows -> rows.map { Cookbook(id = it.id, name = it.name, recipeCount = it.recipeCount) } }
            .distinctUntilChanged()

    override fun observeRecipes(cookbookId: Long): Flow<List<RecipeSummary>> =
        cookbookDao.observeRecipes(cookbookId).map { list -> list.map { it.toDomain() } }.distinctUntilChanged()

    override fun observeCookbookIds(recipeId: Int): Flow<Set<Long>> =
        cookbookDao.observeCookbookIds(recipeId).map { it.toSet() }.distinctUntilChanged()

    override suspend fun cookbookIds(recipeId: Int): Set<Long> = cookbookDao.cookbookIds(recipeId).toSet()

    override suspend fun createCookbook(name: String): CookbookNameResult {
        val cleaned = Cookbook.normalizeName(name)
        validate(cleaned, exceptId = NO_COOKBOOK)?.let { return CookbookNameResult.Rejected(it) }
        val id = cookbookDao.insert(CookbookEntity(name = cleaned, createdAtEpochMillis = now()))
        return CookbookNameResult.Saved(id)
    }

    override suspend fun renameCookbook(cookbookId: Long, name: String): CookbookNameResult {
        val cleaned = Cookbook.normalizeName(name)
        validate(cleaned, exceptId = cookbookId)?.let { return CookbookNameResult.Rejected(it) }
        cookbookDao.rename(cookbookId, cleaned)
        return CookbookNameResult.Saved(cookbookId)
    }

    override suspend fun deleteCookbook(cookbookId: Long) = cookbookDao.delete(cookbookId)

    override suspend fun addRecipe(recipeId: Int, cookbookIds: Set<Long>) {
        if (cookbookIds.isEmpty()) return
        cookbookDao.addRecipe(recipeId, cookbookIds.toList(), addedAtEpochMillis = now())
    }

    override suspend fun removeRecipe(recipeId: Int, cookbookId: Long) = cookbookDao.removeRecipe(cookbookId, recipeId)

    private suspend fun validate(name: String, exceptId: Long): CookbookNameError? = when {
        name.isEmpty() -> CookbookNameError.Blank
        name.length > Cookbook.MAX_NAME_LENGTH -> CookbookNameError.TooLong
        cookbookDao.isNameTaken(name, exceptId) -> CookbookNameError.Taken
        else -> null
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()

    private companion object {
        /** Room's generated ids start at 1, so no cookbook has id 0. */
        const val NO_COOKBOOK = 0L
    }
}
