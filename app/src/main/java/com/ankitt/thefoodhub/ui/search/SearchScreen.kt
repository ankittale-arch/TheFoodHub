package com.ankitt.thefoodhub.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ankitt.thefoodhub.R
import com.ankitt.thefoodhub.domain.model.Nutrient
import com.ankitt.thefoodhub.domain.model.NutrientAmount
import com.ankitt.thefoodhub.domain.model.NutrientFilter
import com.ankitt.thefoodhub.domain.model.NutrientRange
import com.ankitt.thefoodhub.domain.model.RecipeMatch
import com.ankitt.thefoodhub.domain.model.RecipeSummary
import com.ankitt.thefoodhub.feature.search.SearchEffect
import com.ankitt.thefoodhub.feature.search.SearchIntent
import com.ankitt.thefoodhub.feature.search.SearchState
import com.ankitt.thefoodhub.feature.search.SearchViewModel
import com.ankitt.thefoodhub.ui.components.ErrorState
import com.ankitt.thefoodhub.ui.components.LoadingState
import com.ankitt.thefoodhub.ui.components.MessageState
import com.ankitt.thefoodhub.ui.components.ObserveEffects
import com.ankitt.thefoodhub.ui.components.RecipeCard
import com.ankitt.thefoodhub.ui.components.messageRes
import com.ankitt.thefoodhub.ui.theme.TheFoodHubTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.koin.androidx.compose.koinViewModel

/** Start loading the next page when this close to the end of the list. */
private const val LOAD_MORE_THRESHOLD = 6

@Composable
fun SearchRoute(
    onRecipeClick: (RecipeSummary) -> Unit,
    modifier: Modifier = Modifier,
    focusSearchField: Boolean = false,
    onSearchFieldFocused: () -> Unit = {},
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is SearchEffect.ShowError -> snackbarHostState.showSnackbar(context.getString(effect.error.messageRes()))
        }
    }

    SearchScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onRecipeClick = onRecipeClick,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        focusSearchField = focusSearchField,
        onSearchFieldFocused = onSearchFieldFocused,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchState,
    onIntent: (SearchIntent) -> Unit,
    onRecipeClick: (RecipeSummary) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier,
    /** True when the user arrived from the home screen's search bar: focus the field and show the keyboard. */
    focusSearchField: Boolean = false,
    onSearchFieldFocused: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    var openSheet by rememberSaveable { mutableStateOf<SearchSheet?>(null) }
    LaunchedEffect(focusSearchField) {
        if (focusSearchField) {
            focusRequester.requestFocus()
            onSearchFieldFocused()
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            SearchField(
                initialQuery = state.query,
                onQueryChange = { onIntent(SearchIntent.QueryChanged(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
            )
            SearchFilterBar(
                state = state,
                onIntent = onIntent,
                onOpenSheet = { openSheet = it },
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Box(Modifier.fillMaxSize()) {
                SearchContent(state, onIntent, onRecipeClick)
                if (state.isLoading && state.recipes.isNotEmpty()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
        }
    }

    when (openSheet) {
        SearchSheet.INGREDIENTS -> IngredientsSheet(
            initial = state.ingredients,
            onApply = { onIntent(SearchIntent.ApplyIngredients(it)) },
            onDismiss = { openSheet = null },
        )
        SearchSheet.NUTRITION -> NutritionSheet(
            initial = state.nutrients,
            onApply = { onIntent(SearchIntent.ApplyNutrients(it)) },
            onDismiss = { openSheet = null },
        )
        null -> Unit
    }
}

@Composable
private fun SearchField(initialQuery: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    // The field owns its text and reports each change. Feeding it `state.query` back from the
    // ViewModel's StateFlow would lag a frame behind the keyboard, which can drop fast keystrokes.
    // [initialQuery] only seeds it, e.g. when the screen is recreated.
    var text by rememberSaveable { mutableStateOf(initialQuery) }
    fun update(value: String) {
        text = value
        onQueryChange(value)
    }
    OutlinedTextField(
        value = text,
        onValueChange = ::update,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { update("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear_search))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
    )
}

@Composable
private fun SearchContent(
    state: SearchState,
    onIntent: (SearchIntent) -> Unit,
    onRecipeClick: (RecipeSummary) -> Unit,
) {
    when {
        state.recipes.isNotEmpty() -> RecipeGrid(state, onIntent, onRecipeClick)
        state.isLoading -> LoadingState()
        state.error != null -> ErrorState(state.error!!, onRetry = { onIntent(SearchIntent.Retry) })
        state.hasSearched && state.hasFilters -> MessageState(
            title = stringResource(R.string.search_no_results_filters),
            body = stringResource(R.string.search_no_results_filters_body),
        )
        state.hasSearched -> MessageState(title = stringResource(R.string.search_no_results, state.query.trim()))
        else -> MessageState(
            title = stringResource(R.string.search_empty_title),
            body = stringResource(R.string.search_empty_body),
        )
    }
}

@Composable
private fun RecipeGrid(
    state: SearchState,
    onIntent: (SearchIntent) -> Unit,
    onRecipeClick: (RecipeSummary) -> Unit,
) {
    val gridState = rememberLazyGridState()
    LoadMoreWhenNearEnd(gridState, onLoadMore = { onIntent(SearchIntent.LoadNextPage) })

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        state = gridState,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.recipes, key = { it.id }) { recipe ->
            val match = state.matchFor(recipe.id)
            RecipeCard(
                recipe = recipe,
                isFavorite = state.isFavorite(recipe.id),
                onClick = { onRecipeClick(recipe) },
                onToggleFavorite = { onIntent(SearchIntent.ToggleFavorite(recipe)) },
                footer = match?.let { { RecipeMatchText(it) } },
            )
        }
        if (state.isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun LoadMoreWhenNearEnd(gridState: LazyGridState, onLoadMore: () -> Unit) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    // Item count while the user is near the end, else -1. Keying on the count means a page that
    // still leaves the user near the end triggers the next one.
    val nearEndItemCount by remember(gridState) {
        derivedStateOf {
            val layout = gridState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = layout.totalItemsCount
            if (total > 0 && lastVisible >= total - LOAD_MORE_THRESHOLD) total else -1
        }
    }
    // The ViewModel ignores the intent while a page is loading or the end is reached.
    LaunchedEffect(gridState) {
        snapshotFlow { nearEndItemCount }
            .distinctUntilChanged()
            .filter { it >= 0 }
            .collect { currentOnLoadMore() }
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchScreenPreview() {
    TheFoodHubTheme {
        SearchScreen(
            state = SearchState(
                query = "pasta",
                ingredients = listOf("chicken", "garlic"),
                nutrients = NutrientFilter(calories = NutrientRange(max = 600)),
                recipes = (1..6).map { RecipeSummary(it, "Pasta recipe number $it", null) },
                matches = (1..6).associateWith {
                    RecipeMatch(
                        usedIngredientCount = 2,
                        missedIngredientCount = it,
                        nutrients = listOf(NutrientAmount(Nutrient.CALORIES, 480.0 + it * 10)),
                    )
                },
                favoriteIds = setOf(2),
                hasSearched = true,
            ),
            onIntent = {},
            onRecipeClick = {},
        )
    }
}
