package com.ankitt.thefoodhub.ui.favorites

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ankitt.thefoodhub.R
import com.ankitt.thefoodhub.domain.model.Cookbook
import com.ankitt.thefoodhub.domain.model.CookbookNameError
import com.ankitt.thefoodhub.feature.favorites.CookbookNameEditor
import com.ankitt.thefoodhub.feature.favorites.CookbookPicker
import com.ankitt.thefoodhub.feature.favorites.FavoritesIntent
import com.ankitt.thefoodhub.feature.favorites.FavoritesState
import com.ankitt.thefoodhub.ui.theme.FoodHubElevation

/** "All", one chip per cookbook, and "New cookbook". Selecting a chip filters the grid. */
@Composable
fun CookbookRow(
    state: FavoritesState,
    onIntent: (FavoritesIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        CookbookChip(
            label = stringResource(R.string.cookbook_all, state.favorites.size),
            selected = state.selectedCookbookId == null,
            onClick = { onIntent(FavoritesIntent.SelectCookbook(null)) },
        )
        state.cookbooks.forEach { cookbook ->
            CookbookChip(
                label = stringResource(R.string.cookbook_chip, cookbook.name, cookbook.recipeCount),
                selected = state.selectedCookbookId == cookbook.id,
                onClick = { onIntent(FavoritesIntent.SelectCookbook(cookbook.id)) },
            )
        }
        CookbookChip(
            label = stringResource(R.string.cookbook_new),
            selected = false,
            onClick = { onIntent(FavoritesIntent.StartNewCookbook()) },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, Modifier.padding(start = 4.dp)) },
        )
    }
}

@Composable
private fun CookbookChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    ElevatedFilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        shape = CircleShape,
        elevation = FilterChipDefaults.elevatedFilterChipElevation(elevation = FoodHubElevation.Chip),
    )
}

/** "Save to cookbook": a checkbox per cookbook. Each tick is saved straight away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookbookPickerSheet(
    picker: CookbookPicker,
    cookbooks: List<Cookbook>,
    onIntent: (FavoritesIntent) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onIntent(FavoritesIntent.CloseCookbookPicker) },
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.cookbook_picker_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    picker.recipe.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            cookbooks.forEach { cookbook ->
                val checked = picker.contains(cookbook.id)
                ListItem(
                    headlineContent = { Text(cookbook.name) },
                    supportingContent = {
                        Text(pluralStringResource(R.plurals.cookbook_recipe_count, cookbook.recipeCount, cookbook.recipeCount))
                    },
                    leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                    // Rows sit on the sheet's own surface, like the iOS list.
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.toggleable(
                        value = checked,
                        role = Role.Checkbox,
                        onValueChange = { onIntent(FavoritesIntent.ToggleCookbook(cookbook.id)) },
                    ),
                )
            }
            TextButton(
                onClick = { onIntent(FavoritesIntent.StartNewCookbook(recipeToAdd = picker.recipe)) },
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.cookbook_new), Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Names a new cookbook or renames one. The field owns its text; errors come back from the ViewModel. */
@Composable
fun CookbookNamePrompt(
    editor: CookbookNameEditor,
    onIntent: (FavoritesIntent) -> Unit,
) {
    var name by rememberSaveable(editor.cookbookId) { mutableStateOf(editor.initialName) }
    val focusRequester = remember { FocusRequester() }
    val save = { onIntent(FavoritesIntent.SaveCookbookName(name)) }

    AlertDialog(
        onDismissRequest = { onIntent(FavoritesIntent.CancelCookbookName) },
        title = {
            Text(stringResource(if (editor.isRename) R.string.cookbook_name_title_rename else R.string.cookbook_name_title_new))
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.cookbook_name_label)) },
                placeholder = { Text(stringResource(R.string.cookbook_name_hint)) },
                singleLine = true,
                isError = editor.error != null,
                supportingText = editor.error?.let { error -> { Text(error.message()) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = save) {
                Text(stringResource(if (editor.isRename) R.string.action_save else R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(FavoritesIntent.CancelCookbookName) }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
}

@Composable
fun DeleteCookbookDialog(
    cookbook: Cookbook,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cookbook_delete_title, cookbook.name)) },
        text = { Text(stringResource(R.string.cookbook_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun CookbookNameError.message(): String = when (this) {
    CookbookNameError.Blank -> stringResource(R.string.cookbook_name_error_blank)
    CookbookNameError.TooLong -> stringResource(R.string.cookbook_name_error_too_long, Cookbook.MAX_NAME_LENGTH)
    CookbookNameError.Taken -> stringResource(R.string.cookbook_name_error_taken)
}
