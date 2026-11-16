package com.ankitt.thefoodhub.data.repository

import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.data.local.WinePairingDao
import com.ankitt.thefoodhub.data.remote.SpoonacularApi
import com.ankitt.thefoodhub.data.toDomain
import com.ankitt.thefoodhub.data.toEntity
import com.ankitt.thefoodhub.domain.model.WinePairing
import com.ankitt.thefoodhub.domain.repository.WinePairingRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

internal class WinePairingRepositoryImpl(
    private val api: SpoonacularApi,
    private val dao: WinePairingDao,
    private val clock: Clock,
) : WinePairingRepository {

    override suspend fun getPairing(food: String): Outcome<WinePairing?> {
        val key = food.trim().lowercase()
        val now = clock.now().toEpochMilliseconds()

        val cached = dao.get(key)
        if (cached != null && now - cached.cachedAtEpochMillis < CACHE_PERIOD_MILLIS) {
            return Outcome.Success(cached.toDomain())
        }

        return when (val outcome = api.getWinePairing(key)) {
            is Outcome.Success -> {
                val entity = outcome.value.toEntity(food = key, cachedAtEpochMillis = now)
                dao.upsert(entity)
                Outcome.Success(entity.toDomain())
            }
            is Outcome.Failure -> outcome
        }
    }

    private companion object {
        /** Pairings rarely change; refreshing monthly keeps the suggested bottle's price current. */
        val CACHE_PERIOD_MILLIS = 30.days.inWholeMilliseconds
    }
}
