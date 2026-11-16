package com.ankitt.thefoodhub.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ElevatedSuggestionChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ankitt.thefoodhub.R
import com.ankitt.thefoodhub.domain.model.IngredientCatalog
import com.ankitt.thefoodhub.domain.model.Nutrient
import com.ankitt.thefoodhub.domain.model.NutrientAmount
import com.ankitt.thefoodhub.domain.model.NutrientFilter
import com.ankitt.thefoodhub.domain.model.NutrientPreset
import com.ankitt.thefoodhub.domain.model.NutrientRange
import com.ankitt.thefoodhub.domain.model.RecipeMatch
import com.ankitt.thefoodhub.feature.search.SearchIntent
import com.ankitt.thefoodhub.feature.search.SearchState
import com.ankitt.thefoodhub.ui.theme.FoodHubElevation
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SearchSheet { INGREDIENTS, NUTRITION }

/** Pill-shaped chips, matching the capsules on iOS. */
private val ChipShape = CircleShape

/** The two filter buttons, and below them the active filters as removable chips. */
@Composable
fun SearchFilterBar(
    state: SearchState,
    onIntent: (SearchIntent) -> Unit,
    onOpenSheet: (SearchSheet) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            FilterButton(
                label = stringResource(R.string.search_filter_ingredients),
                count = state.ingredients.size,
                icon = { Icon(Icons.Filled.Kitchen, contentDescription = null, Modifier.padding(start = 4.dp)) },
                onClick = { onOpenSheet(SearchSheet.INGREDIENTS) },
            )
            FilterButton(
                label = stringResource(R.string.search_filter_nutrition),
                count = state.nutrients.activeNutrients.size,
                icon = { Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, Modifier.padding(start = 4.dp)) },
                onClick = { onOpenSheet(SearchSheet.NUTRITION) },
            )
            Spacer(Modifier.weight(1f))
            if (state.hasFilters) {
                TextButton(onClick = { onIntent(SearchIntent.ClearFilters) }) {
                    Text(stringResource(R.string.search_clear_filters))
                }
            }
        }
        if (state.hasFilters) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                state.ingredients.forEach { ingredient ->
                    RemovableChip(label = ingredient, onRemove = { onIntent(SearchIntent.RemoveIngredient(ingredient)) })
                }
                state.nutrients.activeNutrients.forEach { nutrient ->
                    RemovableChip(
                        label = "${nutrient.label()} ${rangeText(nutrient, state.nutrients.range(nutrient))}",
                        onRemove = { onIntent(SearchIntent.RemoveNutrient(nutrient)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterButton(label: String, count: Int, icon: @Composable () -> Unit, onClick: () -> Unit) {
    ElevatedFilterChip(
        selected = count > 0,
        onClick = onClick,
        label = { Text(if (count > 0) stringResource(R.string.search_filter_count, label, count) else label) },
        leadingIcon = icon,
        shape = ChipShape,
        elevation = FilterChipDefaults.elevatedFilterChipElevation(elevation = FoodHubElevation.Chip),
    )
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    val removeDescription = stringResource(R.string.search_remove_filter, label)
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, Modifier.padding(end = 4.dp)) },
        shape = ChipShape,
        // Material 3 has no elevated input chip, so the elevation is set here.
        elevation = InputChipDefaults.inputChipElevation(elevation = FoodHubElevation.Chip),
        // `selected` only gives the chip its tint. Screen readers should hear the action
        // ("Remove chicken"), not "chicken, selected".
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = removeDescription
            role = Role.Button
        },
    )
}

/** "512 kcal · 43 g protein" or "2 used · 3 missing", shown under a result's title. */
@Composable
fun RecipeMatchText(match: RecipeMatch) {
    val parts = buildList {
        val used = match.usedIngredientCount
        val missed = match.missedIngredientCount
        if (used != null && missed != null) add(stringResource(R.string.search_match_ingredients, used, missed))
        match.nutrients.forEach { add(it.label()) }
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

// Ingredients sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientsSheet(initial: List<String>, onApply: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(stateSaver = IngredientListSaver) { mutableStateOf(initial) }
    var input by rememberSaveable { mutableStateOf("") }

    fun addInput(name: String = input) {
        draft = IngredientCatalog.add(draft, name)
        input = ""
    }

    fun apply() {
        // Count a typed but not yet added ingredient, rather than silently dropping it.
        val final = IngredientCatalog.add(draft, input)
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onApply(final)
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(stringResource(R.string.search_ingredients_title), style = MaterialTheme.typography.titleLarge)
            val isFull = draft.size >= IngredientCatalog.MAX_INGREDIENTS
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                enabled = !isFull,
                placeholder = { Text(stringResource(R.string.search_ingredients_hint)) },
                trailingIcon = {
                    if (input.isNotBlank()) {
                        IconButton(onClick = { addInput() }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.search_ingredients_add))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addInput() }),
                supportingText = {
                    Text(stringResource(R.string.search_ingredients_count, draft.size, IngredientCatalog.MAX_INGREDIENTS))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (draft.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    draft.forEach { ingredient ->
                        RemovableChip(label = ingredient, onRemove = { draft = draft - ingredient })
                    }
                }
            }
            if (!isFull) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IngredientCatalog.suggestions(input, exclude = draft).forEach { suggestion ->
                        ElevatedSuggestionChip(
                            onClick = { addInput(suggestion) },
                            label = { Text(suggestion) },
                            icon = { Icon(Icons.Filled.Add, contentDescription = null, Modifier.padding(start = 4.dp)) },
                            shape = ChipShape,
                            elevation = SuggestionChipDefaults.elevatedSuggestionChipElevation(elevation = FoodHubElevation.Chip),
                        )
                    }
                }
            }
            SheetActions(onReset = { draft = emptyList(); input = "" }, onApply = ::apply)
        }
    }
}

// Nutrition sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionSheet(initial: NutrientFilter, onApply: (NutrientFilter) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(stateSaver = NutrientFilterSaver) { mutableStateOf(initial) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(stringResource(R.string.search_nutrition_title), style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NutrientPreset.entries.forEach { preset ->
                    val selected = draft.hasPreset(preset)
                    ElevatedFilterChip(
                        selected = selected,
                        onClick = { draft = if (selected) draft.removing(preset) else draft.applying(preset) },
                        label = { Text(preset.label()) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.padding(start = 4.dp)) }
                        } else {
                            null
                        },
                        shape = ChipShape,
                        elevation = FilterChipDefaults.elevatedFilterChipElevation(elevation = FoodHubElevation.Chip),
                    )
                }
            }
            Nutrient.entries.forEach { nutrient ->
                NutrientSlider(
                    nutrient = nutrient,
                    range = draft.range(nutrient),
                    onRangeChange = { draft = draft.with(nutrient, it) },
                )
            }
            SheetActions(
                onReset = { draft = NutrientFilter() },
                onApply = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onApply(draft)
                        onDismiss()
                    }
                },
            )
        }
    }
}

@Composable
private fun NutrientSlider(nutrient: Nutrient, range: NutrientRange, onRangeChange: (NutrientRange) -> Unit) {
    val ceiling = nutrient.filterCeiling
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(nutrient.label(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(rangeText(nutrient, range), style = MaterialTheme.typography.bodyMedium)
        }
        RangeSlider(
            value = (range.min ?: 0).toFloat()..(range.max ?: ceiling).toFloat(),
            onValueChange = { values ->
                fun snap(value: Float) = (value / nutrient.filterStep).roundToInt() * nutrient.filterStep
                // The slider's ends mean "no limit", so they map back to null.
                onRangeChange(
                    NutrientRange(
                        min = snap(values.start).takeIf { it > 0 },
                        max = snap(values.endInclusive).takeIf { it < ceiling },
                    ),
                )
            },
            valueRange = 0f..ceiling.toFloat(),
            steps = ceiling / nutrient.filterStep - 1,
        )
    }
}

@Composable
private fun SheetActions(onReset: () -> Unit, onApply: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        TextButton(onClick = onReset) { Text(stringResource(R.string.search_reset)) }
        Button(onClick = onApply) { Text(stringResource(R.string.search_apply)) }
    }
}

// Labels

@Composable
private fun Nutrient.label(): String = stringResource(
    when (this) {
        Nutrient.CALORIES -> R.string.nutrient_calories
        Nutrient.PROTEIN -> R.string.nutrient_protein
        Nutrient.CARBS -> R.string.nutrient_carbs
        Nutrient.FAT -> R.string.nutrient_fat
    },
)

@Composable
private fun NutrientAmount.label(): String = stringResource(
    when (nutrient) {
        Nutrient.CALORIES -> R.string.nutrient_amount_calories
        Nutrient.PROTEIN -> R.string.nutrient_amount_protein
        Nutrient.CARBS -> R.string.nutrient_amount_carbs
        Nutrient.FAT -> R.string.nutrient_amount_fat
    },
    amount.roundToInt(),
)

@Composable
private fun NutrientPreset.label(): String = stringResource(
    when (this) {
        NutrientPreset.HIGH_PROTEIN -> R.string.preset_high_protein
        NutrientPreset.LOW_CARB -> R.string.preset_low_carb
        NutrientPreset.UNDER_500_KCAL -> R.string.preset_under_500_kcal
        NutrientPreset.LOW_FAT -> R.string.preset_low_fat
    },
)

/** "Any", "≥ 30 g", "≤ 500 kcal" or "20–40 g". */
@Composable
private fun rangeText(nutrient: Nutrient, range: NutrientRange): String {
    val min = range.min
    val max = range.max
    return when {
        min != null && max != null -> stringResource(R.string.nutrient_range_between, min, max, nutrient.unit)
        min != null -> stringResource(R.string.nutrient_range_at_least, min, nutrient.unit)
        max != null -> stringResource(R.string.nutrient_range_at_most, max, nutrient.unit)
        else -> stringResource(R.string.nutrient_range_any)
    }
}

private val IngredientListSaver = Saver<List<String>, ArrayList<String>>(
    save = { ArrayList(it) },
    restore = { it },
)

/** Survives rotation as a flat list of bounds, with -1 for "no limit". */
private val NutrientFilterSaver = Saver<NutrientFilter, IntArray>(
    save = { filter ->
        Nutrient.entries.flatMap { listOf(filter.range(it).min ?: -1, filter.range(it).max ?: -1) }.toIntArray()
    },
    restore = { values ->
        Nutrient.entries.foldIndexed(NutrientFilter()) { index, filter, nutrient ->
            filter.with(
                nutrient,
                NutrientRange(
                    min = values[index * 2].takeIf { it >= 0 },
                    max = values[index * 2 + 1].takeIf { it >= 0 },
                ),
            )
        }
    },
)
