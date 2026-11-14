package com.ankitt.thefoodhub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankitt.thefoodhub.ui.theme.HomePalette

data class BottomBarItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/** Floating pill bar from the home design: the selected item expands into a green pill with its label. */
@Composable
fun FoodHubBottomBar(items: List<BottomBarItem>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(HomePalette.Bar)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item -> BarItem(item) }
    }
}

@Composable
private fun BarItem(item: BottomBarItem) {
    val background by animateColorAsState(if (item.selected) HomePalette.Olive else Color.Transparent, label = "tab")
    Row(
        Modifier
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = item.onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = item.selected
                contentDescription = item.label
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(HomePalette.BarIconBackground.let { if (item.selected) it.copy(alpha = 0.92f) else it }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = HomePalette.BarIcon)
        }
        AnimatedVisibility(
            visible = item.selected,
            enter = expandHorizontally(),
            exit = shrinkHorizontally(),
        ) {
            Row {
                Spacer(Modifier.width(14.dp))
                Text(item.label, color = HomePalette.OnOlive, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(22.dp))
            }
        }
    }
}
