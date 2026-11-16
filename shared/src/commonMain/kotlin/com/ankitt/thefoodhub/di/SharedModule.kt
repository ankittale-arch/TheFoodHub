package com.ankitt.thefoodhub.di

import com.ankitt.thefoodhub.core.AppConfig
import com.ankitt.thefoodhub.data.local.FavoriteDao
import com.ankitt.thefoodhub.data.local.FoodHubDatabase
import com.ankitt.thefoodhub.data.local.KitchenDao
import com.ankitt.thefoodhub.data.local.RecipeDao
import com.ankitt.thefoodhub.data.local.WinePairingDao
import com.ankitt.thefoodhub.data.remote.SpoonacularApi
import com.ankitt.thefoodhub.data.remote.createHttpClient
import com.ankitt.thefoodhub.data.repository.FavoritesRepositoryImpl
import com.ankitt.thefoodhub.data.repository.KitchenRepositoryImpl
import com.ankitt.thefoodhub.data.repository.RecipeRepositoryImpl
import com.ankitt.thefoodhub.data.repository.WinePairingRepositoryImpl
import com.ankitt.thefoodhub.domain.repository.FavoritesRepository
import com.ankitt.thefoodhub.domain.repository.KitchenRepository
import com.ankitt.thefoodhub.domain.repository.RecipeRepository
import com.ankitt.thefoodhub.domain.repository.WinePairingRepository
import com.ankitt.thefoodhub.feature.calculator.CalculatorViewModel
import com.ankitt.thefoodhub.feature.detail.RecipeDetailViewModel
import com.ankitt.thefoodhub.feature.favorites.FavoritesViewModel
import com.ankitt.thefoodhub.feature.home.HomeViewModel
import com.ankitt.thefoodhub.feature.search.SearchViewModel
import io.ktor.client.HttpClient
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * Provides per platform:
 * - `HttpClientEngine` (OkHttp on Android, Darwin on iOS)
 * - `FoodHubDatabase` (each platform decides where the database file lives)
 */
internal expect val platformModule: Module

internal fun dataModule(config: AppConfig): Module = module {
    single { config }
    single<Clock> { Clock.System }

    single<HttpClient> { createHttpClient(engine = get(), config = get()) }
    singleOf(::SpoonacularApi)

    single<RecipeDao> { get<FoodHubDatabase>().recipeDao() }
    single<FavoriteDao> { get<FoodHubDatabase>().favoriteDao() }
    single<WinePairingDao> { get<FoodHubDatabase>().winePairingDao() }
    single<KitchenDao> { get<FoodHubDatabase>().kitchenDao() }

    singleOf(::RecipeRepositoryImpl) { bind<RecipeRepository>() }
    singleOf(::FavoritesRepositoryImpl) { bind<FavoritesRepository>() }
    singleOf(::WinePairingRepositoryImpl) { bind<WinePairingRepository>() }
    singleOf(::KitchenRepositoryImpl) { bind<KitchenRepository>() }
}

internal val viewModelModule: Module = module {
    viewModelOf(::HomeViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::FavoritesViewModel)
    viewModelOf(::CalculatorViewModel)
    viewModel { (recipeId: Int) -> RecipeDetailViewModel(recipeId, get(), get()) }
}

fun sharedModules(config: AppConfig): List<Module> =
    listOf(dataModule(config), viewModelModule, platformModule)

/** Entry point for both apps. Android passes `androidContext(...)` via [appDeclaration]. */
fun initKoin(config: AppConfig, appDeclaration: KoinAppDeclaration = {}): KoinApplication =
    startKoin {
        appDeclaration()
        modules(sharedModules(config))
    }
