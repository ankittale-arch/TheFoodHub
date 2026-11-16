package com.ankitt.thefoodhub.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.WineBar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ankitt.thefoodhub.R
import com.ankitt.thefoodhub.domain.model.WineBottle
import com.ankitt.thefoodhub.domain.model.WinePairing
import com.ankitt.thefoodhub.feature.home.WinePairingState
import com.ankitt.thefoodhub.ui.components.FoodLoaderBadge
import com.ankitt.thefoodhub.ui.components.FoodLoaderColors
import com.ankitt.thefoodhub.ui.components.FoodLoaderDefaults
import com.ankitt.thefoodhub.ui.components.messageRes
import com.ankitt.thefoodhub.ui.theme.FoodHubElevation
import com.ankitt.thefoodhub.ui.theme.HomePalette

private val CardShape = RoundedCornerShape(26.dp)

/** "Pairs well with": the wine pairing for the recipe centred in the carousel. */
@Composable
fun WinePairingSection(state: WinePairingState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    if (state == WinePairingState.Hidden) return
    Column(modifier) {
        Text(
            text = stringResource(R.string.home_wine_title),
            color = HomePalette.Ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(FoodHubElevation.Card, CardShape)
                .background(HomePalette.Card)
                .animateContentSize()
                .padding(18.dp),
        ) {
            when (state) {
                WinePairingState.Hidden -> Unit
                WinePairingState.Loading -> LoadingContent()
                WinePairingState.NoPairing -> NoPairingContent()
                is WinePairingState.Failed -> Column {
                    Text(stringResource(state.error.messageRes()), color = HomePalette.Ink, fontSize = 15.sp)
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.action_retry), color = HomePalette.Wine, fontWeight = FontWeight.Medium)
                    }
                }
                is WinePairingState.Ready -> PairingContent(state.pairing)
            }
        }
    }
}

/** The app's food loader in wine colours, laid out like [NoPairingContent] so the card keeps its shape. */
@Composable
private fun LoadingContent() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        FoodLoaderBadge(
            size = FoodLoaderDefaults.CompactSize,
            colors = FoodLoaderColors(container = HomePalette.Wine.copy(alpha = 0.12f), content = HomePalette.Wine),
        )
        Text(stringResource(R.string.home_wine_loading), color = HomePalette.InkMuted, fontSize = 15.sp)
    }
}

/** A crossed-out wine glass and a suggestion for what to drink instead. */
@Composable
private fun NoPairingContent() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        NoWineBadge()
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.home_wine_none_title),
                color = HomePalette.Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.home_wine_none_body),
                color = HomePalette.InkMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

/**
 * A wine glass struck through, on a soft wine-coloured circle. Drawn rather than taken from an
 * icon set, because neither Material nor SF Symbols has a crossed-out wine glass and iOS draws
 * the same badge.
 */
@Composable
private fun NoWineBadge() {
    val wine = HomePalette.Wine
    val badge = wine.copy(alpha = 0.12f)
    // The badge as it looks on the card, for the gap around the slash.
    val gap = badge.compositeOver(HomePalette.Card)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(badge),
    ) {
        Icon(Icons.Outlined.WineBar, contentDescription = null, tint = wine, modifier = Modifier.size(26.dp))
        Canvas(Modifier.size(28.dp)) {
            val start = Offset(size.width * 0.12f, size.height * 0.12f)
            val end = Offset(size.width * 0.88f, size.height * 0.88f)
            // A gap in the badge colour first, so the slash reads as cutting through the glass.
            drawLine(gap, start, end, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
            drawLine(wine, start, end, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun PairingContent(pairing: WinePairing) {
    // Long explanations open on tap; the state resets when the carousel moves to another food.
    var expanded by rememberSaveable(pairing.food) { mutableStateOf(false) }
    if (pairing.wines.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WineBar, contentDescription = null, tint = HomePalette.Wine)
            Spacer(Modifier.width(10.dp))
            Text(
                pairing.wines.joinToString(" · "),
                color = HomePalette.Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
    if (pairing.explanation.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            pairing.explanation,
            color = HomePalette.InkMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { expanded = !expanded },
        )
    }
    pairing.suggestedBottle?.let { bottle ->
        Spacer(Modifier.height(14.dp))
        BottleRow(bottle)
    }
}

@Composable
private fun BottleRow(bottle: WineBottle) {
    val uriHandler = LocalUriHandler.current
    val link = bottle.link
    val openDescription = stringResource(R.string.home_wine_open_bottle, bottle.title)
    val ratingText = bottle.rating?.let { rating ->
        bottle.ratingCount?.let { stringResource(R.string.home_wine_rating_count, rating.toString(), it) }
            ?: stringResource(R.string.home_wine_rating, rating.toString())
    }
    val ratingDescription = bottle.rating?.let { stringResource(R.string.home_rating, it.toString()) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HomePalette.Pill)
            .then(if (link != null) Modifier.clickable { uriHandler.openUri(link) } else Modifier)
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(
                    if (link != null) openDescription else bottle.title,
                    bottle.price,
                    ratingDescription,
                ).joinToString(", ")
                if (link != null) role = Role.Button
            }
            .padding(10.dp),
    ) {
        AsyncImage(
            model = bottle.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(width = 44.dp, height = 60.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(HomePalette.Card),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                bottle.title,
                color = HomePalette.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(bottle.price, ratingText).joinToString(" · ")
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(details, color = HomePalette.InkMuted, fontSize = 13.sp)
            }
        }
        if (link != null) {
            Icon(
                Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = HomePalette.InkMuted,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp),
            )
        }
    }
}
