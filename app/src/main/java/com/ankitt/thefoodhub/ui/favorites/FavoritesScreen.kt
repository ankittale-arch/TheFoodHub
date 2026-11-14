package com.ankitt.thefoodhub.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ankitt.thefoodhub.R
import com.ankitt.thefoodhub.domain.model.RecipeSummary
import com.ankitt.thefoodhub.feature.favorites.FavoritesEffect
import com.ankitt.thefoodhub.feature.favorites.FavoritesIntent
import com.ankitt.thefoodhub.feature.favorites.FavoritesState
import com.ankitt.thefoodhub.feature.favorites.FavoritesViewModel
import com.ankitt.thefoodhub.ui.components.LoadingState
import com.ankitt.thefoodhub.ui.components.MessageState
import com.ankitt.thefoodhub.ui.components.ObserveEffects
import com.ankitt.thefoodhub.ui.components.RecipeCard
import org.koin.androidx.compose.koinViewModel

@Composable
fun FavoritesRoute(
    onRecipeClick: (RecipeSummary) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is FavoritesEffect.Removed -> {
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.favorites_removed, effect.recipe.title),
                    actionLabel = context.getString(R.string.action_undo),
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.onIntent(FavoritesIntent.UndoRemove(effect.recipe))
                }
            }
        }
    }

    FavoritesScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onRecipeClick = onRecipeClick,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    state: FavoritesState,
    onIntent: (FavoritesIntent) -> Unit,
    onRecipeClick: (RecipeSummary) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_favorites)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            state.isLoading -> LoadingState(modifier)
            state.favorites.isEmpty() -> MessageState(
                title = stringResource(R.string.favorites_empty_title),
                body = stringResource(R.string.favorites_empty_body),
                modifier = modifier,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier.fillMaxSize(),
            ) {
                items(state.favorites, key = { it.id }) { recipe ->
                    RecipeCard(
                        recipe = recipe,
                        isFavorite = true,
                        onClick = { onRecipeClick(recipe) },
                        onToggleFavorite = { onIntent(FavoritesIntent.Remove(recipe)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}
