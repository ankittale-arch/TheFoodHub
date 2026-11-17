package com.ankitt.thefoodhub.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ankitt.thefoodhub.R
import com.ankitt.thefoodhub.domain.model.RecipeNotes
import com.ankitt.thefoodhub.feature.detail.NoteEditor
import com.ankitt.thefoodhub.feature.detail.RecipeDetailIntent
import com.ankitt.thefoodhub.ui.theme.FoodHubElevation

/** The user's note on a saved recipe, or a prompt to write one. */
@Composable
fun MyNotesCard(
    notes: RecipeNotes,
    hasIngredients: Boolean,
    onEditNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onEditNote,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = FoodHubElevation.Card),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.StickyNote2, contentDescription = null, Modifier.size(20.dp))
                Text(
                    stringResource(R.string.notes_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                Text(
                    stringResource(if (notes.note.isEmpty()) R.string.notes_add else R.string.notes_edit),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (notes.note.isNotEmpty()) {
                Text(notes.note, style = MaterialTheme.typography.bodyLarge)
            }
            if (hasIngredients) {
                Text(
                    stringResource(R.string.notes_tweak_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/** Shown in place of [MyNotesCard] while the recipe is not saved. */
@Composable
fun NotesLockedHint(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.AutoMirrored.Outlined.StickyNote2,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            stringResource(R.string.notes_locked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** The user's tweak under an ingredient line, e.g. "✎ Used half". */
@Composable
fun IngredientTweakLine(tweak: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            tweak,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** Writes the note or an ingredient tweak. The field owns its text until Save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorSheet(editor: NoteEditor, onIntent: (RecipeDetailIntent) -> Unit) {
    var text by rememberSaveable(editor.ingredientLine) { mutableStateOf(editor.initialText) }
    val focusRequester = remember { FocusRequester() }
    val save = { onIntent(RecipeDetailIntent.SaveNoteEdit(text)) }

    ModalBottomSheet(
        onDismissRequest = { onIntent(RecipeDetailIntent.CancelNoteEdit) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    stringResource(if (editor.isTweak) R.string.notes_editor_title_tweak else R.string.notes_editor_title_note),
                    style = MaterialTheme.typography.titleLarge,
                )
                editor.ingredientLine?.let { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(editor.maxLength) },
                placeholder = {
                    Text(stringResource(if (editor.isTweak) R.string.notes_editor_hint_tweak else R.string.notes_editor_hint_note))
                },
                singleLine = editor.isTweak,
                minLines = if (editor.isTweak) 1 else 4,
                maxLines = if (editor.isTweak) 1 else 10,
                supportingText = { Text(stringResource(R.string.notes_editor_counter, text.length, editor.maxLength)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = if (editor.isTweak) ImeAction.Done else ImeAction.Default,
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (editor.canRemove) {
                    TextButton(onClick = { onIntent(RecipeDetailIntent.SaveNoteEdit("")) }) {
                        Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onIntent(RecipeDetailIntent.CancelNoteEdit) }) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(onClick = save, modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
}
