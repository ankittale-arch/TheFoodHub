package com.ankitt.thefoodhub.data.repository

import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.data.local.IngredientDensityEntity
import com.ankitt.thefoodhub.data.local.IngredientSubstitutesEntity
import com.ankitt.thefoodhub.data.local.KitchenDao
import com.ankitt.thefoodhub.data.remote.SpoonacularApi
import com.ankitt.thefoodhub.domain.model.IngredientSubstitutes
import com.ankitt.thefoodhub.domain.model.KitchenMath
import com.ankitt.thefoodhub.domain.repository.KitchenRepository
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

internal class KitchenRepositoryImpl(
    private val api: SpoonacularApi,
    private val dao: KitchenDao,
    private val clock: Clock,
) : KitchenRepository {

    override suspend fun savedGramsPerCup(ingredient: String): Double? =
        dao.getDensity(KitchenMath.normalizeIngredient(ingredient))?.gramsPerCup

    override suspend fun getGramsPerCup(ingredient: String): Outcome<Double?> {
        val key = KitchenMath.normalizeIngredient(ingredient)
        dao.getDensity(key)?.let { return Outcome.Success(it.gramsPerCup) }

        // One cup in grams is all a mass ↔ volume conversion needs; every other unit pair is then
        // worked out on the device, so one point covers the ingredient for good.
        val outcome = api.convertAmount(ingredientName = key, sourceAmount = 1.0, sourceUnit = "cups", targetUnit = "grams")
        return when (outcome) {
            is Outcome.Success -> {
                val gramsPerCup = outcome.value.targetAmount?.takeIf { it > 0 && it.isFinite() }
                dao.upsertDensity(IngredientDensityEntity(key, gramsPerCup, clock.now().toEpochMilliseconds()))
                Outcome.Success(gramsPerCup)
            }
            is Outcome.Failure -> outcome
        }
    }

    override suspend fun getSubstitutes(ingredient: String): Outcome<IngredientSubstitutes?> {
        val key = KitchenMath.normalizeIngredient(ingredient)
        dao.getSubstitutes(key)?.let { return Outcome.Success(it.toDomain()) }

        return when (val outcome = api.getIngredientSubstitutes(key)) {
            is Outcome.Success -> {
                val substitutes = outcome.value.substitutes.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                val entity = IngredientSubstitutesEntity(key, substitutes, clock.now().toEpochMilliseconds())
                dao.upsertSubstitutes(entity)
                Outcome.Success(entity.toDomain())
            }
            is Outcome.Failure -> outcome
        }
    }

    override fun observeSavedDensityIngredients(): Flow<List<String>> = dao.observeDensityIngredients()

    override fun observeSavedSubstituteIngredients(): Flow<List<String>> = dao.observeSubstituteIngredients()

    private fun IngredientSubstitutesEntity.toDomain(): IngredientSubstitutes? =
        substitutes.takeIf { it.isNotEmpty() }?.let { IngredientSubstitutes(ingredient = ingredient, substitutes = it) }
}

