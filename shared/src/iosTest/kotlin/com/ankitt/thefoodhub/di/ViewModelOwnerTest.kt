package com.ankitt.thefoodhub.di

import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/** Exercises the real iOS Koin graph (Darwin engine, Room file) the way the SwiftUI app will. */
class ViewModelOwnerTest {

    @BeforeTest
    fun startKoin() = FoodHub.start(isDebug = false)

    @AfterTest
    fun stop() = stopKoin()

    @Test
    fun ownerCachesViewModelsPerKey() {
        val owner = ViewModelOwner()

        assertSame(owner.searchViewModel(), owner.searchViewModel())
        assertSame(owner.favoritesViewModel(), owner.favoritesViewModel())
        assertSame(owner.recipeDetailViewModel(1), owner.recipeDetailViewModel(1))
        assertNotSame(owner.recipeDetailViewModel(1), owner.recipeDetailViewModel(2))

        owner.clear()
    }

    @Test
    fun clearingCreatesFreshViewModels() {
        val owner = ViewModelOwner()
        val first = owner.searchViewModel()

        owner.clear()

        assertNotSame(first, owner.searchViewModel())
        owner.clear()
    }

    @Test
    fun separateOwnersDoNotShareViewModels() {
        val a = ViewModelOwner()
        val b = ViewModelOwner()

        assertNotSame(a.searchViewModel(), b.searchViewModel())

        a.clear()
        b.clear()
    }
}
