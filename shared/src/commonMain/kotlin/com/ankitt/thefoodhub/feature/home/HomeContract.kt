package com.ankitt.thefoodhub.feature.home

import com.ankitt.thefoodhub.core.DataError
import com.ankitt.thefoodhub.domain.model.PopularRecipe
import com.ankitt.thefoodhub.domain.model.WinePairing

/** The chips on the home screen. */
enum class HomeCategory {
    /** Random recipes in the order Spoonacular returns them. */
    All,

    /** The same recipes as [All], sorted by likes. */
    Popular,

    /** Random recipes ready in at most [HomeViewModel.QUICK_MAX_MINUTES] minutes. */
    QuickAndEasy,

    /** Random recipes tagged with the selected [Diet]. */
    Diets,
}

/** Diet options behind the Diets chip. [tag] is the value sent as Spoonacular's `include-tags`. */
enum class Diet(val tag: String) {
    Vegetarian("vegetarian"),
    Vegan("vegan"),
    GlutenFree("gluten free"),
    Ketogenic("ketogenic"),
    Paleo("paleo"),
}

data class HomeState(
    val category: HomeCategory = HomeCategory.All,
    /** Used when [category] is [HomeCategory.Diets]; kept so the chip can show the last choice. */
    val diet: Diet = Diet.Vegetarian,
    val recipes: List<PopularRecipe> = emptyList(),
    val isLoading: Boolean = true,
    val error: DataError? = null,
    /** The wine pairing for the recipe centred in the carousel. */
    val winePairing: WinePairingState = WinePairingState.Hidden,
)

/** The "Pairs well with" section under the carousel. */
sealed interface WinePairingState {
    /** No recipe is shown, so neither is the section. */
    data object Hidden : WinePairingState

    data object Loading : WinePairingState

    data class Ready(val pairing: WinePairing) : WinePairingState

    /** The recipe has nothing a wine pairing applies to, or Spoonacular has no pairing for it. */
    data object NoPairing : WinePairingState

    data class Failed(val error: DataError) : WinePairingState
}

sealed interface HomeIntent {
    data class SelectCategory(val category: HomeCategory) : HomeIntent
    /** Also switches to [HomeCategory.Diets]. */
    data class SelectDiet(val diet: Diet) : HomeIntent
    data object Retry : HomeIntent

    /** The carousel settled on this recipe; its wine pairing is shown below. */
    data class FocusRecipe(val recipeId: Int) : HomeIntent

    data object RetryWinePairing : HomeIntent
}
