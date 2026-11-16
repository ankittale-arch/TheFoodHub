package com.ankitt.thefoodhub.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ankitt.thefoodhub.core.AppConfig
import com.ankitt.thefoodhub.feature.calculator.CalculatorViewModel
import com.ankitt.thefoodhub.feature.detail.RecipeDetailViewModel
import com.ankitt.thefoodhub.feature.favorites.FavoritesViewModel
import com.ankitt.thefoodhub.feature.home.HomeViewModel
import com.ankitt.thefoodhub.feature.search.SearchViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf

/**
 * Called once from the iOS app's `init()`: `FoodHub.shared.start(isDebug: ...)`.
 * The API key is generated into the shared module from local.properties.
 */
object FoodHub {
    fun start(isDebug: Boolean) {
        initKoin(AppConfig(isDebug = isDebug))
    }
}

/**
 * Gives a SwiftUI screen its shared ViewModels with the same lifecycle rules Android uses.
 *
 * Create one per screen (e.g. as a `@StateObject` wrapper) and call [clear] from `deinit`.
 * [clear] cancels each ViewModel's `viewModelScope`, the same way leaving a screen does on Android.
 */
class ViewModelOwner : ViewModelStoreOwner, KoinComponent {

    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun homeViewModel(): HomeViewModel = provide { get<HomeViewModel>() }

    fun searchViewModel(): SearchViewModel = provide { get<SearchViewModel>() }

    fun favoritesViewModel(): FavoritesViewModel = provide { get<FavoritesViewModel>() }

    fun calculatorViewModel(): CalculatorViewModel = provide { get<CalculatorViewModel>() }

    fun recipeDetailViewModel(recipeId: Int): RecipeDetailViewModel =
        provide(key = "recipe-$recipeId") { get<RecipeDetailViewModel> { parametersOf(recipeId) } }

    fun clear() = viewModelStore.clear()

    private inline fun <reified VM : androidx.lifecycle.ViewModel> provide(
        key: String = VM::class.qualifiedName.orEmpty(),
        crossinline create: () -> VM,
    ): VM {
        val factory = viewModelFactory { initializer { create() } }
        return ViewModelProvider.create(this, factory)[key, VM::class]
    }
}
