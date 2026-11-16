package com.ankitt.thefoodhub.ui.calculator

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ElevatedSuggestionChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ankitt.thefoodhub.R
import com.ankitt.thefoodhub.domain.model.IngredientSubstitutes
import com.ankitt.thefoodhub.domain.model.MeasureDimension
import com.ankitt.thefoodhub.domain.model.MeasureUnit
import com.ankitt.thefoodhub.feature.calculator.CalculatorIntent
import com.ankitt.thefoodhub.feature.calculator.CalculatorState
import com.ankitt.thefoodhub.feature.calculator.CalculatorTool
import com.ankitt.thefoodhub.feature.calculator.CalculatorViewModel
import com.ankitt.thefoodhub.feature.calculator.ConversionIntent
import com.ankitt.thefoodhub.feature.calculator.ConversionResult
import com.ankitt.thefoodhub.feature.calculator.ConversionState
import com.ankitt.thefoodhub.feature.calculator.DensityBasis
import com.ankitt.thefoodhub.feature.calculator.SubstitutesIntent
import com.ankitt.thefoodhub.feature.calculator.SubstitutesResult
import com.ankitt.thefoodhub.feature.calculator.SubstitutesState
import com.ankitt.thefoodhub.ui.components.messageRes
import com.ankitt.thefoodhub.ui.theme.FoodHubElevation
import com.ankitt.thefoodhub.ui.theme.TheFoodHubTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun CalculatorRoute(
    modifier: Modifier = Modifier,
    viewModel: CalculatorViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CalculatorScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    state: CalculatorState,
    onIntent: (CalculatorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.calculator_title)) }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ToolSelector(state.tool, onSelect = { onIntent(CalculatorIntent.SelectTool(it)) })
            when (state.tool) {
                CalculatorTool.Convert -> ConvertTool(state.conversion, onIntent)
                CalculatorTool.Substitute -> SubstituteTool(state.substitutes, onIntent)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolSelector(selected: CalculatorTool, onSelect: (CalculatorTool) -> Unit) {
    val tools = CalculatorTool.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        tools.forEachIndexed { index, tool ->
            SegmentedButton(
                selected = tool == selected,
                onClick = { onSelect(tool) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tools.size),
            ) {
                Text(
                    stringResource(
                        when (tool) {
                            CalculatorTool.Convert -> R.string.calculator_tool_convert
                            CalculatorTool.Substitute -> R.string.calculator_tool_substitute
                        },
                    ),
                )
            }
        }
    }
}

// Convert

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConvertTool(state: ConversionState, onIntent: (CalculatorIntent) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    // The fields keep their own text, so typing never waits for a state round trip.
    var amount by rememberSaveable { mutableStateOf(state.amountText) }
    var ingredient by rememberSaveable { mutableStateOf(state.ingredient) }
    val convert = {
        keyboard?.hide()
        onIntent(ConversionIntent.Convert)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = amount,
            onValueChange = {
                amount = it
                onIntent(ConversionIntent.AmountChanged(it))
            },
            label = { Text(stringResource(R.string.calculator_amount)) },
            placeholder = { Text(stringResource(R.string.calculator_amount_hint)) },
            singleLine = true,
            // Text rather than Decimal, so fractions such as "1 1/2" can be typed.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            UnitPicker(
                label = stringResource(R.string.calculator_from),
                unit = state.from,
                onSelect = { onIntent(ConversionIntent.FromUnitChanged(it)) },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onIntent(ConversionIntent.SwapUnits) }) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.calculator_swap_units))
            }
            UnitPicker(
                label = stringResource(R.string.calculator_to),
                unit = state.to,
                onSelect = { onIntent(ConversionIntent.ToUnitChanged(it)) },
                modifier = Modifier.weight(1f),
            )
        }

        AnimatedVisibility(visible = state.needsIngredient) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ingredient,
                    onValueChange = {
                        ingredient = it
                        onIntent(ConversionIntent.IngredientChanged(it))
                    },
                    label = { Text(stringResource(R.string.calculator_ingredient)) },
                    placeholder = { Text(stringResource(R.string.calculator_ingredient_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { convert() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                SuggestionChips(state.suggestions) { suggestion ->
                    keyboard?.hide()
                    ingredient = suggestion
                    onIntent(ConversionIntent.SelectIngredient(suggestion))
                }
            }
        }

        ConversionResultCard(result = state.result, ingredient = ingredient.trim(), onConvert = convert)
    }
}

@Composable
private fun UnitPicker(label: String, unit: MeasureUnit, onSelect: (MeasureUnit) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(unit.nameRes()), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MeasureDimension.entries.forEachIndexed { index, dimension ->
                if (index > 0) HorizontalDivider()
                MeasureUnit.entries.filter { it.dimension == dimension }.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.nameRes())) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                        trailingIcon = if (option == unit) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversionResultCard(result: ConversionResult, ingredient: String, onConvert: () -> Unit) {
    ResultCard {
        when (result) {
            is ConversionResult.Ready -> {
                Text(
                    pluralStringResource(result.unit.amountRes(), if (result.isSingular) 1 else 2, result.amount),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Caption(result.basis.caption())
            }
            ConversionResult.InvalidAmount -> Caption(stringResource(R.string.calculator_invalid_amount))
            ConversionResult.NeedsIngredient -> Caption(stringResource(R.string.calculator_needs_ingredient))
            ConversionResult.NeedsLookup -> {
                Caption(stringResource(R.string.calculator_needs_lookup, ingredient))
                FilledTonalButton(onClick = onConvert) { Text(stringResource(R.string.calculator_convert)) }
            }
            ConversionResult.Loading -> LoadingRow(stringResource(R.string.calculator_loading, ingredient))
            is ConversionResult.Unconvertible -> Caption(stringResource(R.string.calculator_unconvertible, result.ingredient))
            is ConversionResult.Failed -> {
                Caption(stringResource(result.error.messageRes()))
                FilledTonalButton(onClick = onConvert) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

@Composable
private fun DensityBasis?.caption(): String =
    if (this == null) {
        stringResource(R.string.calculator_result_standard)
    } else {
        stringResource(R.string.calculator_result_density, ingredient, gramsPerCup)
    }

// Substitute

@Composable
private fun SubstituteTool(state: SubstitutesState, onIntent: (CalculatorIntent) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf(state.query) }
    val find = {
        keyboard?.hide()
        onIntent(SubstitutesIntent.Find)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onIntent(SubstitutesIntent.QueryChanged(it))
            },
            placeholder = { Text(stringResource(R.string.calculator_substitute_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (query.isNotBlank()) {
                {
                    IconButton(onClick = find) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.calculator_find))
                    }
                }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { find() }),
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
        )
        SuggestionChips(state.suggestions) { suggestion ->
            keyboard?.hide()
            query = suggestion
            onIntent(SubstitutesIntent.SelectIngredient(suggestion))
        }
        SubstitutesResultContent(state.result, onRetry = find)
    }
}

@Composable
private fun SubstitutesResultContent(result: SubstitutesResult, onRetry: () -> Unit) {
    when (result) {
        SubstitutesResult.Idle -> ResultCard {
            Text(stringResource(R.string.calculator_substitute_idle_title), style = MaterialTheme.typography.titleMedium)
            Caption(stringResource(R.string.calculator_substitute_idle_body))
        }
        is SubstitutesResult.Loading -> ResultCard { LoadingRow(stringResource(R.string.calculator_loading, result.ingredient)) }
        is SubstitutesResult.Ready -> SubstitutesList(result.substitutes)
        is SubstitutesResult.NotFound -> ResultCard {
            Caption(stringResource(R.string.calculator_substitutes_none, result.ingredient))
        }
        is SubstitutesResult.Failed -> ResultCard {
            Caption(stringResource(result.error.messageRes()))
            FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@Composable
private fun SubstitutesList(substitutes: IngredientSubstitutes) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.calculator_substitutes_title, substitutes.ingredient),
            style = MaterialTheme.typography.titleMedium,
        )
        substitutes.substitutes.forEach { substitute ->
            ElevatedCard(
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = FoodHubElevation.Card),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(substitute, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// Shared pieces

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionChips(suggestions: List<String>, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { suggestion ->
            ElevatedSuggestionChip(
                onClick = { onSelect(suggestion) },
                label = { Text(suggestion) },
                shape = CircleShape,
                elevation = SuggestionChipDefaults.elevatedSuggestionChipElevation(elevation = FoodHubElevation.Chip),
            )
        }
    }
}

@Composable
private fun ResultCard(content: @Composable () -> Unit) {
    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = FoodHubElevation.Card),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(
            Modifier
                .heightIn(min = 96.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            content()
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LoadingRow(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Caption(text)
    }
}

@StringRes
private fun MeasureUnit.nameRes(): Int = when (this) {
    MeasureUnit.Grams -> R.string.unit_grams
    MeasureUnit.Kilograms -> R.string.unit_kilograms
    MeasureUnit.Ounces -> R.string.unit_ounces
    MeasureUnit.Pounds -> R.string.unit_pounds
    MeasureUnit.Teaspoons -> R.string.unit_teaspoons
    MeasureUnit.Tablespoons -> R.string.unit_tablespoons
    MeasureUnit.Cups -> R.string.unit_cups
    MeasureUnit.FluidOunces -> R.string.unit_fluid_ounces
    MeasureUnit.Milliliters -> R.string.unit_milliliters
    MeasureUnit.Liters -> R.string.unit_liters
}

@PluralsRes
private fun MeasureUnit.amountRes(): Int = when (this) {
    MeasureUnit.Grams -> R.plurals.amount_grams
    MeasureUnit.Kilograms -> R.plurals.amount_kilograms
    MeasureUnit.Ounces -> R.plurals.amount_ounces
    MeasureUnit.Pounds -> R.plurals.amount_pounds
    MeasureUnit.Teaspoons -> R.plurals.amount_teaspoons
    MeasureUnit.Tablespoons -> R.plurals.amount_tablespoons
    MeasureUnit.Cups -> R.plurals.amount_cups
    MeasureUnit.FluidOunces -> R.plurals.amount_fluid_ounces
    MeasureUnit.Milliliters -> R.plurals.amount_milliliters
    MeasureUnit.Liters -> R.plurals.amount_liters
}

@Preview(showBackground = true)
@Composable
private fun CalculatorScreenPreview() {
    TheFoodHubTheme {
        CalculatorScreen(
            state = CalculatorState(
                conversion = ConversionState(
                    amountText = "2.5",
                    result = ConversionResult.Ready("313", MeasureUnit.Grams, isSingular = false, basis = DensityBasis("flour", "125")),
                ),
            ),
            onIntent = {},
        )
    }
}
