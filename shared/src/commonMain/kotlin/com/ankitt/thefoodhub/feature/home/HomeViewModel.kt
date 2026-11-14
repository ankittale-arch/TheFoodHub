package com.ankitt.thefoodhub.feature.home

import androidx.lifecycle.viewModelScope
import com.ankitt.thefoodhub.core.Outcome
import com.ankitt.thefoodhub.core.mvi.MviViewModel
import com.ankitt.thefoodhub.domain.model.PopularRecipe
import com.ankitt.thefoodhub.domain.repository.RecipeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Home screen: chips plus a carousel of random (popular) recipes.
 *
 * Every random-recipes call costs quota and returns a new selection, so each source is fetched
 * once and kept for the lifetime of the ViewModel. Switching chips back and forth is free, and
 * All/Popular share one request. The home screen has no one-off events, hence no effect type.
 */
class HomeViewModel(
    private val recipeRepository: RecipeRepository,
) : MviViewModel<HomeState, HomeIntent, Nothing>(HomeState()) {

    /** Where a category's recipes come from. Several categories can share one source. */
    private sealed interface Source {
        data object Untagged : Source
        data object Quick : Source
        data class Tagged(val diet: Diet) : Source
    }

    private val cache = mutableMapOf<Source, List<PopularRecipe>>()
    private val loading = mutableMapOf<Source, Job>()

    init {
        show()
    }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.SelectCategory -> {
                updateState { copy(category = intent.category) }
                show()
            }
            is HomeIntent.SelectDiet -> {
                updateState { copy(category = HomeCategory.Diets, diet = intent.diet) }
                show()
            }
            HomeIntent.Retry -> {
                cache.remove(currentSource())
                show()
            }
        }
    }

    private fun currentSource(state: HomeState = currentState): Source = when (state.category) {
        HomeCategory.All, HomeCategory.Popular -> Source.Untagged
        HomeCategory.QuickAndEasy -> Source.Quick
        HomeCategory.Diets -> Source.Tagged(state.diet)
    }

    /** Renders the current category from the cache, fetching its source first if needed. */
    private fun show() {
        val source = currentSource()
        val cached = cache[source]
        if (cached != null) {
            updateState { copy(recipes = arrange(cached, category), isLoading = false, error = null) }
            return
        }
        updateState { copy(recipes = emptyList(), isLoading = true, error = null) }
        // Already on its way (e.g. All → Popular while loading): don't pay for a second request.
        if (loading[source]?.isActive == true) return
        loading[source] = viewModelScope.launch { fetch(source) }
    }

    private suspend fun fetch(source: Source) {
        val outcome = when (source) {
            Source.Untagged -> recipeRepository.getRandomRecipes(count = PAGE_COUNT)
            Source.Quick -> recipeRepository.getRandomRecipes(count = QUICK_FETCH_COUNT)
            is Source.Tagged -> recipeRepository.getRandomRecipes(count = PAGE_COUNT, includeTags = listOf(source.diet.tag))
        }
        // The user may have switched chips meanwhile; still cache the result, but only render it if it is current.
        val isCurrent = currentSource() == source
        when (outcome) {
            is Outcome.Success -> {
                val recipes = if (source == Source.Quick) outcome.value.filter(::isQuick) else outcome.value
                cache[source] = recipes
                if (isCurrent) updateState { copy(recipes = arrange(recipes, category), isLoading = false, error = null) }
            }
            is Outcome.Failure -> {
                if (isCurrent) updateState { copy(recipes = emptyList(), isLoading = false, error = outcome.error) }
            }
        }
    }

    private fun arrange(recipes: List<PopularRecipe>, category: HomeCategory): List<PopularRecipe> =
        if (category == HomeCategory.Popular) {
            // Stable sort: equal or missing like counts keep the API order, and missing ones go last.
            recipes.sortedByDescending { it.likes ?: -1 }
        } else {
            recipes
        }

    private fun isQuick(recipe: PopularRecipe): Boolean =
        recipe.readyInMinutes?.let { it <= QUICK_MAX_MINUTES } == true

    companion object {
        const val PAGE_COUNT = 10

        /** The random endpoint cannot filter by time, so fetch extra and keep the quick ones. */
        const val QUICK_FETCH_COUNT = 40
        const val QUICK_MAX_MINUTES = 30
    }
}
