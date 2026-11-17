package com.ankitt.thefoodhub.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    var cookbookIdToDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_favorites)) },
                actions = {
                    state.selectedCookbook?.let { cookbook ->
                        CookbookMenu(
                            onRename = { onIntent(FavoritesIntent.StartRenameCookbook(cookbook.id)) },
                            onDelete = { cookbookIdToDelete = cookbook.id },
                        )
                    }
                },
            )
        },
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
            else -> Column(modifier.fillMaxSize()) {
                CookbookRow(state = state, onIntent = onIntent, modifier = Modifier.padding(vertical = 4.dp))
                val selectedCookbook = state.selectedCookbook
                if (selectedCookbook != null && state.visibleRecipes.isEmpty()) {
                    MessageState(
                        title = stringResource(R.string.cookbook_empty_title, selectedCookbook.name),
                        body = stringResource(R.string.cookbook_empty_body),
                    )
                } else {
                    FavoritesGrid(state.visibleRecipes, onIntent, onRecipeClick)
                }
            }
        }
    }

    state.cookbookPicker?.let { picker ->
        CookbookPickerSheet(picker = picker, cookbooks = state.cookbooks, onIntent = onIntent)
    }
    state.nameEditor?.let { editor -> CookbookNamePrompt(editor = editor, onIntent = onIntent) }
    state.cookbooks.firstOrNull { it.id == cookbookIdToDelete }?.let { cookbook ->
        DeleteCookbookDialog(
            cookbook = cookbook,
            onConfirm = {
                onIntent(FavoritesIntent.DeleteCookbook(cookbook.id))
                cookbookIdToDelete = null
            },
            onDismiss = { cookbookIdToDelete = null },
        )
    }
}

@Composable
private fun FavoritesGrid(
    recipes: List<RecipeSummary>,
    onIntent: (FavoritesIntent) -> Unit,
    onRecipeClick: (RecipeSummary) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(recipes, key = { it.id }) { recipe ->
            RecipeCard(
                recipe = recipe,
                isFavorite = true,
                onClick = { onRecipeClick(recipe) },
                onToggleFavorite = { onIntent(FavoritesIntent.Remove(recipe)) },
                onAddToCookbook = { onIntent(FavoritesIntent.OpenCookbookPicker(recipe)) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/** Rename and delete for the selected cookbook. */
@Composable
private fun CookbookMenu(onRename: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cookbook_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.cookbook_rename)) },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.cookbook_delete)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}
