package com.ankitt.thefoodhub.data

import com.ankitt.thefoodhub.data.repository.FavoritesRepositoryImpl
import com.ankitt.thefoodhub.testutil.FakeFavoriteDao
import com.ankitt.thefoodhub.testutil.FixedClock
import com.ankitt.thefoodhub.testutil.recipeSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FavoritesRepositoryImplTest {

    private val dao = FakeFavoriteDao()
    private val clock = FixedClock()
    private val repo = FavoritesRepositoryImpl(dao, clock)

    @Test
    fun toggleAddsThenRemoves() = runTest {
        assertTrue(repo.toggleFavorite(recipeSummary(1)))
        assertTrue(repo.observeIsFavorite(1).first())

        assertFalse(repo.toggleFavorite(recipeSummary(1)))
        assertFalse(repo.observeIsFavorite(1).first())
    }

    @Test
    fun favoritesAreNewestFirst() = runTest {
        clock.nowMillis = 1
        repo.setFavorite(recipeSummary(1), favorite = true)
        clock.nowMillis = 2
        repo.setFavorite(recipeSummary(2), favorite = true)

        assertEquals(listOf(2, 1), repo.observeFavorites().first().map { it.id })
        assertEquals(setOf(1, 2), repo.observeFavoriteIds().first())
    }
}
