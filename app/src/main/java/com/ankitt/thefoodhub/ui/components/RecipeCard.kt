package com.ankitt.thefoodhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ankitt.thefoodhub.R
import com.ankitt.thefoodhub.domain.model.RecipeSummary
import com.ankitt.thefoodhub.ui.theme.FoodHubElevation

@Composable
fun RecipeCard(
    recipe: RecipeSummary,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    /** Extra line under the title, e.g. why a search result matched. */
    footer: (@Composable () -> Unit)? = null,
    /** Shows the "Add to cookbook" button when set (favorites only). */
    onAddToCookbook: (() -> Unit)? = null,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = FoodHubElevation.Card),
    ) {
        Box {
            val buttonBackdrop = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            AsyncImage(
                model = recipe.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (onAddToCookbook != null) {
                IconButton(
                    onClick = onAddToCookbook,
                    modifier = Modifier.align(Alignment.TopStart).overImage(buttonBackdrop),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = stringResource(R.string.cookbook_add_recipe),
                    )
                }
            }
            FavoriteButton(
                isFavorite = isFavorite,
                onClick = onToggleFavorite,
                modifier = Modifier.align(Alignment.TopEnd).overImage(buttonBackdrop),
            )
        }
        Column(Modifier.padding(12.dp)) {
            Text(
                text = recipe.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (footer != null) {
                Spacer(Modifier.height(4.dp))
                footer()
            }
        }
    }
}

/** A circular [backdrop] that keeps an icon button readable over the photo. */
private fun Modifier.overImage(backdrop: Color): Modifier =
    padding(4.dp)
        .clip(CircleShape)
        .background(backdrop)

@Composable
fun FavoriteButton(isFavorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(
                if (isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite,
            ),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
