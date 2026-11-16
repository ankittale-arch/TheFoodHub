package com.ankitt.thefoodhub.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ankitt.thefoodhub.R
import com.ankitt.thefoodhub.ui.components.BottomBarItem
import com.ankitt.thefoodhub.ui.calculator.CalculatorRoute
import com.ankitt.thefoodhub.ui.components.BarIconSize
import com.ankitt.thefoodhub.ui.components.FoodHubBottomBar
import com.ankitt.thefoodhub.ui.detail.RecipeDetailRoute
import com.ankitt.thefoodhub.ui.favorites.FavoritesRoute
import com.ankitt.thefoodhub.ui.home.HomeRoute
import com.ankitt.thefoodhub.ui.navigation.CalculatorDestination
import com.ankitt.thefoodhub.ui.navigation.FavoritesDestination
import com.ankitt.thefoodhub.ui.navigation.HomeDestination
import com.ankitt.thefoodhub.ui.navigation.RecipeDetailDestination
import com.ankitt.thefoodhub.ui.navigation.SearchDestination
import com.ankitt.thefoodhub.ui.search.SearchRoute
import kotlin.reflect.KClass

private enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val icon: ImageVector,
    val labelRes: Int,
) {
    Home(HomeDestination, HomeDestination::class, Icons.Outlined.Home, R.string.nav_home),
    Search(SearchDestination, SearchDestination::class, Icons.Outlined.Search, R.string.nav_search),
    Favorites(FavoritesDestination, FavoritesDestination::class, Icons.Outlined.FavoriteBorder, R.string.nav_favorites),
    Calculator(CalculatorDestination, CalculatorDestination::class, Icons.Outlined.Calculate, R.string.nav_calculator),
}

/** Height of the floating bar (icon plus 6 dp padding on each side) plus its bottom margin. */
private val BottomBarSpace = BarIconSize + 12.dp + 16.dp + 12.dp

@Composable
fun FoodHubApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { currentDestination.isOn(it) }
    var focusSearchField by rememberSaveable { mutableStateOf(false) }

    // The bar floats over the content (the home gradient runs behind it), so top-level screens
    // reserve space for it themselves instead of using a Scaffold bottom bar.
    val barSpace = PaddingValues(
        bottom = BottomBarSpace + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )
    val topLevelModifier = Modifier
        .padding(barSpace)
        .consumeWindowInsets(WindowInsets.navigationBars)

    val openRecipe: (id: Int, title: String) -> Unit = { id, title ->
        navController.navigate(RecipeDetailDestination(recipeId = id, title = title))
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = HomeDestination,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable<HomeDestination> {
                HomeRoute(
                    onSearchClick = {
                        focusSearchField = true
                        navController.navigateToTab(TopLevelDestination.Search)
                    },
                    onRecipeClick = { openRecipe(it.id, it.title) },
                    contentPadding = barSpace,
                )
            }
            composable<SearchDestination> {
                SearchRoute(
                    onRecipeClick = { openRecipe(it.id, it.title) },
                    modifier = topLevelModifier,
                    focusSearchField = focusSearchField,
                    onSearchFieldFocused = { focusSearchField = false },
                )
            }
            composable<FavoritesDestination> {
                FavoritesRoute(onRecipeClick = { openRecipe(it.id, it.title) }, modifier = topLevelModifier)
            }
            composable<CalculatorDestination> {
                CalculatorRoute(modifier = topLevelModifier)
            }
            composable<RecipeDetailDestination> { entry ->
                val route = entry.toRoute<RecipeDetailDestination>()
                RecipeDetailRoute(
                    recipeId = route.recipeId,
                    title = route.title,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        if (showBottomBar) {
            FoodHubBottomBar(
                items = TopLevelDestination.entries.map { destination ->
                    BottomBarItem(
                        label = stringResource(destination.labelRes),
                        icon = destination.icon,
                        selected = currentDestination.isOn(destination),
                        onClick = { navController.navigateToTab(destination) },
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )
        }
    }
}

private fun NavHostController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true
