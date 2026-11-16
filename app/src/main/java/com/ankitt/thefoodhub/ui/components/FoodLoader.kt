package com.ankitt.thefoodhub.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Flatware
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.SetMeal
import androidx.compose.material.icons.outlined.WineBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ankitt.thefoodhub.ui.theme.TheFoodHubTheme

/**
 * The icons the loader cycles through: cutlery, a meal, wine, fish, cake, coffee.
 * iOS shows the same sequence with SF Symbols (`FoodLoader.swift`).
 */
private val loaderIcons = listOf(
    Icons.Outlined.Flatware,
    Icons.Outlined.LunchDining,
    Icons.Outlined.WineBar,
    Icons.Outlined.SetMeal,
    Icons.Outlined.Cake,
    Icons.Outlined.Coffee,
)

/** How long each icon shows: short enough that even a one-second load shows a change. iOS uses the same value. */
private const val ICON_MILLIS = 500

/** Colours of a [FoodLoaderBadge]: the circle and the icons on it. */
@Immutable
data class FoodLoaderColors(val container: Color, val content: Color)

object FoodLoaderDefaults {
    /** Full size, as in full-screen loading states. */
    val Size: Dp = 88.dp

    /** For a loader inside a card, next to a line of text. */
    val CompactSize: Dp = 48.dp

    @Composable
    fun colors(
        container: Color = MaterialTheme.colorScheme.primaryContainer,
        content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    ): FoodLoaderColors = FoodLoaderColors(container, content)
}

/**
 * The animated part of the loader: a soft circle in which food icons pop in one after another.
 * It has no semantics of its own; whatever shows it says what is loading (see [FoodLoader]).
 *
 * Both animations are infinite transitions, which Compose pauses in UI tests and skips when the
 * system's "Remove animations" setting is on (the icon then simply changes in place).
 */
@Composable
fun FoodLoaderBadge(
    modifier: Modifier = Modifier,
    size: Dp = FoodLoaderDefaults.Size,
    colors: FoodLoaderColors = FoodLoaderDefaults.colors(),
) {
    val transition = rememberInfiniteTransition(label = "foodLoader")
    // Counts 0, 1, … up to the icon count once per cycle, spending ICON_MILLIS on each value.
    val step by transition.animateValue(
        initialValue = 0,
        targetValue = loaderIcons.size,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(tween(ICON_MILLIS * loaderIcons.size, easing = LinearEasing)),
        label = "icon",
    )
    val breath by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(ICON_MILLIS), RepeatMode.Reverse),
        label = "breath",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = breath
                scaleY = breath
            }
            .clip(CircleShape)
            .background(colors.container)
            .clearAndSetSemantics { },
    ) {
        AnimatedContent(
            targetState = step % loaderIcons.size,
            transitionSpec = {
                // The new icon pops in with a little bounce while the old one grows and fades.
                (fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), initialScale = 0.3f))
                    .togetherWith(fadeOut(tween(160)) + scaleOut(tween(220), targetScale = 1.4f))
            },
            label = "icon",
        ) { index ->
            Icon(loaderIcons[index], contentDescription = null, tint = colors.content, modifier = Modifier.size(size * 0.45f))
        }
    }
}

/** A [FoodLoaderBadge] with [label] underneath, announced to screen readers. */
@Composable
fun FoodLoader(
    label: String,
    modifier: Modifier = Modifier,
    colors: FoodLoaderColors = FoodLoaderDefaults.colors(),
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = label
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        FoodLoaderBadge(colors = colors)
        Spacer(Modifier.height(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = labelColor)
    }
}

/** [FoodLoader] centred in the available space, for full-screen loading states. */
@Composable
fun FoodLoadingState(label: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        FoodLoader(label)
    }
}

@Preview(showBackground = true)
@Composable
private fun FoodLoaderPreview() {
    TheFoodHubTheme {
        FoodLoadingState("Finding recipes…")
    }
}
