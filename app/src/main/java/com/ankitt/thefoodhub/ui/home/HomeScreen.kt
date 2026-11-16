package com.ankitt.thefoodhub.ui.home

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SoupKitchen
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ankitt.thefoodhub.R
import com.ankitt.thefoodhub.domain.model.PopularRecipe
import com.ankitt.thefoodhub.feature.home.Diet
import com.ankitt.thefoodhub.feature.home.HomeCategory
import com.ankitt.thefoodhub.feature.home.HomeIntent
import com.ankitt.thefoodhub.feature.home.HomeState
import com.ankitt.thefoodhub.feature.home.HomeViewModel
import com.ankitt.thefoodhub.ui.components.messageRes
import com.ankitt.thefoodhub.ui.theme.FoodHubElevation
import com.ankitt.thefoodhub.ui.theme.HomePalette
import com.ankitt.thefoodhub.ui.theme.TheFoodHubTheme
import org.koin.androidx.compose.koinViewModel
import kotlin.math.absoluteValue

@Composable
fun HomeRoute(
    onSearchClick: () -> Unit,
    onRecipeClick: (PopularRecipe) -> Unit,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onSearchClick = onSearchClick,
        onRecipeClick = onRecipeClick,
        contentPadding = contentPadding,
    )
}

@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onSearchClick: () -> Unit,
    onRecipeClick: (PopularRecipe) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(HomePalette.BackgroundTop, HomePalette.BackgroundBottom))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(contentPadding)
                .padding(top = 24.dp, bottom = 16.dp),
        ) {
            Headline(Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(22.dp))
            HomeSearchBar(onClick = onSearchClick, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(20.dp))
            CategoryChips(state = state, onIntent = onIntent)
            Spacer(Modifier.height(24.dp))
            Text(
                text = sectionTitle(state),
                color = HomePalette.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(16.dp))
            CarouselArea(state = state, onIntent = onIntent, onRecipeClick = onRecipeClick)
        }
    }
}

@Composable
private fun Headline(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            stringResource(R.string.home_headline_muted),
            color = HomePalette.InkMuted,
            fontSize = 38.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.home_headline),
            color = HomePalette.Ink,
            fontSize = 38.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Looks like a field; tapping it opens the Search tab with the keyboard up. */
@Composable
private fun HomeSearchBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hint = stringResource(R.string.home_search_hint)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .shadow(FoodHubElevation.Field, CircleShape)
            .background(HomePalette.Field)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = hint
            }
            .padding(start = 20.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.SoupKitchen, contentDescription = null, tint = HomePalette.Ink)
        Spacer(Modifier.width(12.dp))
        Text(hint, color = HomePalette.InkMuted, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(HomePalette.FieldButton),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = HomePalette.Ink)
        }
    }
}

@Composable
private fun CategoryChips(state: HomeState, onIntent: (HomeIntent) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CategoryChip(R.string.home_chip_all, state.category == HomeCategory.All) {
            onIntent(HomeIntent.SelectCategory(HomeCategory.All))
        }
        CategoryChip(R.string.home_chip_popular, state.category == HomeCategory.Popular) {
            onIntent(HomeIntent.SelectCategory(HomeCategory.Popular))
        }
        CategoryChip(R.string.home_chip_quick, state.category == HomeCategory.QuickAndEasy) {
            onIntent(HomeIntent.SelectCategory(HomeCategory.QuickAndEasy))
        }
        DietsChip(state, onIntent)
    }
}

@Composable
private fun DietsChip(state: HomeState, onIntent: (HomeIntent) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val selected = state.category == HomeCategory.Diets
    Box {
        CategoryChip(
            labelRes = if (selected) state.diet.labelRes else R.string.home_chip_diets,
            selected = selected,
            onClick = { menuOpen = true },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            Diet.entries.forEach { diet ->
                DropdownMenuItem(
                    text = { Text(stringResource(diet.labelRes)) },
                    trailingIcon = {
                        if (selected && diet == state.diet) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        onIntent(HomeIntent.SelectDiet(diet))
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(@StringRes labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 52.dp)
            .shadow(FoodHubElevation.Chip, CircleShape)
            .background(if (selected) HomePalette.Ink else HomePalette.Chip)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(labelRes),
            color = if (selected) HomePalette.OnInk else HomePalette.Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CarouselArea(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onRecipeClick: (PopularRecipe) -> Unit,
) {
    val areaModifier = Modifier
        .fillMaxWidth()
        .height(CAROUSEL_HEIGHT)
    when {
        state.recipes.isNotEmpty() ->
            // A new category starts again at its first card.
            key(state.category, state.diet) {
                RecipeCarousel(state.recipes, onRecipeClick)
            }
        state.isLoading -> Box(areaModifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = HomePalette.Olive)
        }
        else -> Box(areaModifier.padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(state.error?.messageRes() ?: R.string.home_empty),
                    color = HomePalette.Ink,
                    fontSize = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(HomePalette.Ink)
                        .clickable { onIntent(HomeIntent.Retry) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.action_retry), color = HomePalette.OnInk, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun RecipeCarousel(recipes: List<PopularRecipe>, onRecipeClick: (PopularRecipe) -> Unit) {
    val pagerState = rememberPagerState { recipes.size }
    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = 52.dp),
        key = { recipes[it].id },
        modifier = Modifier
            .fillMaxWidth()
            .height(CAROUSEL_HEIGHT),
    ) { page ->
        RecipeSlide(
            recipe = recipes[page],
            onClick = { onRecipeClick(recipes[page]) },
            modifier = Modifier.carouselTransform(pagerState, page),
        )
    }
}

/**
 * Stacked look from the design: the centred card is full size and in front, and its neighbours
 * shrink and slide in behind it so that only [NEIGHBOUR_PEEK] of each shows past its edges.
 */
private fun Modifier.carouselTransform(pagerState: PagerState, page: Int): Modifier {
    val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
    val distance = offset.absoluteValue
    val fraction = distance.coerceIn(0f, 1f)
    return zIndex(-distance).graphicsLayer {
        val scale = lerp(1f, NEIGHBOUR_SCALE, fraction)
        scaleX = scale
        scaleY = scale
        // A neighbour would sit one page width away; pull it back so its outer edge ends up
        // NEIGHBOUR_PEEK beyond the centre card's edge.
        translationX = offset * (size.width * (1f + NEIGHBOUR_SCALE) / 2f - NEIGHBOUR_PEEK.toPx())
        // Cards two or more away hide completely behind the neighbours.
        alpha = if (distance <= 1f) 1f else (2f - distance).coerceIn(0f, 1f)
    }
}

private const val NEIGHBOUR_SCALE = 0.86f
private val NEIGHBOUR_PEEK = 18.dp

@Composable
private fun RecipeSlide(recipe: PopularRecipe, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(36.dp)
    Box(
        modifier
            .fillMaxSize()
            .shadow(FoodHubElevation.FeaturedCard, shape)
            .border(3.dp, HomePalette.SlideBorder, shape)
            .background(HomePalette.Pill)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = recipe.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        recipe.readyInMinutes?.let { minutes ->
            StatPill(
                icon = Icons.Outlined.Schedule,
                text = stringResource(R.string.home_minutes, minutes),
                background = HomePalette.Olive,
                content = HomePalette.OnOlive,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp),
            )
        }
        recipe.likes?.let { likes ->
            StatPill(
                icon = Icons.Outlined.FavoriteBorder,
                text = likes.toString(),
                description = stringResource(R.string.home_likes, likes),
                background = HomePalette.Card,
                content = HomePalette.Ink,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(14.dp)
                .fillMaxWidth()
                .shadow(FoodHubElevation.Card, RoundedCornerShape(26.dp))
                .background(HomePalette.Card)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(
                recipe.title,
                color = HomePalette.Ink,
                fontSize = 19.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            recipe.rating?.let { rating ->
                Spacer(Modifier.height(10.dp))
                StatPill(
                    icon = Icons.Outlined.StarBorder,
                    text = rating.toString(),
                    description = stringResource(R.string.home_rating, rating.toString()),
                    background = HomePalette.Pill,
                    content = HomePalette.Ink,
                    small = true,
                )
            }
        }
    }
}

@Composable
private fun StatPill(
    icon: ImageVector,
    text: String,
    background: Color,
    content: Color,
    modifier: Modifier = Modifier,
    description: String = text,
    small: Boolean = false,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(background)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = if (small) 12.dp else 14.dp, vertical = if (small) 6.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(if (small) 16.dp else 20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = content, fontSize = if (small) 12.sp else 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun sectionTitle(state: HomeState): String = when (state.category) {
    HomeCategory.All, HomeCategory.Popular -> stringResource(R.string.home_section_popular)
    HomeCategory.QuickAndEasy -> stringResource(R.string.home_section_quick)
    HomeCategory.Diets -> stringResource(R.string.home_section_diet, stringResource(state.diet.labelRes))
}

private val Diet.labelRes: Int
    @StringRes get() = when (this) {
        Diet.Vegetarian -> R.string.home_diet_vegetarian
        Diet.Vegan -> R.string.home_diet_vegan
        Diet.GlutenFree -> R.string.home_diet_gluten_free
        Diet.Ketogenic -> R.string.home_diet_ketogenic
        Diet.Paleo -> R.string.home_diet_paleo
    }

private val CAROUSEL_HEIGHT = 340.dp

@Preview(showBackground = true, heightDp = 860)
@Preview(name = "Dark", showBackground = true, heightDp = 860, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenPreview() {
    TheFoodHubTheme {
        HomeScreen(
            state = HomeState(
                isLoading = false,
                recipes = listOf(
                    PopularRecipe(1, "Leek pasta with miso sauce", null, readyInMinutes = 25, likes = 385, rating = 4.8),
                    PopularRecipe(2, "Roasted tomato soup", null, readyInMinutes = 40, likes = 120, rating = 4.2),
                ),
            ),
            onIntent = {},
            onSearchClick = {},
            onRecipeClick = {},
        )
    }
}
