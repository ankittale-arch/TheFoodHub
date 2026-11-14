package com.ankitt.thefoodhub.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ankitt.thefoodhub.R
import com.ankitt.thefoodhub.domain.model.Ingredient
import com.ankitt.thefoodhub.domain.model.InstructionStep
import com.ankitt.thefoodhub.domain.model.RecipeDetail
import com.ankitt.thefoodhub.feature.detail.RecipeDetailEffect
import com.ankitt.thefoodhub.feature.detail.RecipeDetailIntent
import com.ankitt.thefoodhub.feature.detail.RecipeDetailState
import com.ankitt.thefoodhub.feature.detail.RecipeDetailViewModel
import com.ankitt.thefoodhub.ui.components.ErrorState
import com.ankitt.thefoodhub.ui.components.FavoriteButton
import com.ankitt.thefoodhub.ui.components.LoadingState
import com.ankitt.thefoodhub.ui.components.ObserveEffects
import com.ankitt.thefoodhub.ui.theme.TheFoodHubTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RecipeDetailRoute(
    recipeId: Int,
    title: String?,
    onBack: () -> Unit,
    viewModel: RecipeDetailViewModel = koinViewModel(key = "recipe-$recipeId") { parametersOf(recipeId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is RecipeDetailEffect.FavoriteChanged -> {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    context.getString(if (effect.isFavorite) R.string.detail_saved else R.string.detail_unsaved),
                )
            }
        }
    }

    RecipeDetailScreen(
        state = state,
        placeholderTitle = title,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    state: RecipeDetailState,
    onIntent: (RecipeDetailIntent) -> Unit,
    onBack: () -> Unit,
    placeholderTitle: String? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val recipe = state.recipe
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(recipe?.title ?: placeholderTitle.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (recipe != null) {
                        FavoriteButton(
                            isFavorite = state.isFavorite,
                            onClick = { onIntent(RecipeDetailIntent.ToggleFavorite) },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when {
            recipe != null -> RecipeContent(recipe, contentModifier)
            state.error != null -> ErrorState(
                error = state.error!!,
                onRetry = { onIntent(RecipeDetailIntent.Retry) },
                modifier = contentModifier,
            )
            else -> LoadingState(contentModifier)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeContent(recipe: RecipeDetail, modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val summary = remember(recipe.summaryHtml, linkColor) {
        AnnotatedString.fromHtml(
            recipe.summaryHtml,
            linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
        )
    }
    val tags = remember(recipe) { (recipe.diets + recipe.cuisines + recipe.dishTypes).distinct() }

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            AsyncImage(
                model = recipe.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(recipe.title, style = MaterialTheme.typography.headlineSmall)
                MetaLine(recipe)
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { tag -> SuggestionChip(onClick = {}, label = { Text(tag) }) }
                    }
                }
                if (summary.isNotEmpty()) {
                    Text(summary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        ingredientsSection(recipe.ingredients)
        instructionsSection(recipe.steps)
        item { SourceLink(recipe) }
    }
}

@Composable
private fun MetaLine(recipe: RecipeDetail) {
    val parts = buildList {
        recipe.readyInMinutes?.let { add(stringResource(R.string.detail_ready_in, it)) }
        recipe.servings?.let { add(pluralStringResource(R.plurals.detail_servings, it, it)) }
        recipe.healthScore?.let { add(stringResource(R.string.detail_health_score, it)) }
    }
    if (parts.isNotEmpty()) {
        Text(
            parts.joinToString("  ·  "),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun LazyListScope.sectionHeader(titleRes: Int) {
    item {
        Column(Modifier.padding(horizontal = 16.dp)) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

private fun LazyListScope.ingredientsSection(ingredients: List<Ingredient>) {
    if (ingredients.isEmpty()) return
    sectionHeader(R.string.detail_ingredients)
    // Spoonacular can list the same ingredient twice, so key by position, not ingredient id.
    itemsIndexed(ingredients, key = { index, _ -> "ingredient-$index" }) { _, ingredient ->
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = ingredient.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Box(Modifier.width(12.dp))
            Text(ingredient.original, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun LazyListScope.instructionsSection(steps: List<InstructionStep>) {
    sectionHeader(R.string.detail_instructions)
    if (steps.isEmpty()) {
        item {
            Text(
                stringResource(R.string.detail_no_instructions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        return
    }
    itemsIndexed(steps, key = { _, step -> "step-${step.number}" }) { _, step ->
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "${step.number}.",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(32.dp),
            )
            Text(step.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SourceLink(recipe: RecipeDetail) {
    val url = recipe.sourceUrl ?: return
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = { runCatching { uriHandler.openUri(url) } },
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
    ) {
        Text(
            recipe.sourceName?.let { stringResource(R.string.detail_source, it) }
                ?: stringResource(R.string.detail_source_unknown),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RecipeDetailPreview() {
    TheFoodHubTheme {
        RecipeDetailScreen(
            state = RecipeDetailState(
                recipeId = 1,
                isFavorite = true,
                recipe = RecipeDetail(
                    id = 1,
                    title = "Pasta with Garlic, Scallions and Broccoli",
                    imageUrl = null,
                    readyInMinutes = 45,
                    servings = 2,
                    healthScore = 19,
                    sourceName = "Full Belly Sisters",
                    sourceUrl = "https://example.com",
                    summaryHtml = "A <b>quick</b> weeknight pasta.",
                    diets = listOf("lacto ovo vegetarian"),
                    cuisines = listOf("Italian"),
                    dishTypes = listOf("main course"),
                    ingredients = listOf(Ingredient("butter", "1 tbsp butter", null)),
                    steps = listOf(InstructionStep(1, "Boil the pasta."), InstructionStep(2, "Toss with sauce.")),
                ),
            ),
            onIntent = {},
            onBack = {},
        )
    }
}
