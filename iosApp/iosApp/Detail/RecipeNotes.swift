import SwiftUI
@preconcurrency import Shared

/// The user's note on a saved recipe, or a prompt to write one.
struct MyNotesCard: View {
    let notes: RecipeNotes
    let hasIngredients: Bool
    let onEditNote: () -> Void

    var body: some View {
        Button(action: onEditNote) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Label("My notes", systemImage: "note.text").font(.headline)
                    Spacer()
                    Text(notes.note.isEmpty ? "Add a note" : "Edit note")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.orange)
                }
                if !notes.note.isEmpty {
                    Text(notes.note).multilineTextAlignment(.leading)
                }
                if hasIngredients {
                    Text("Tap an ingredient to note a tweak.").font(.caption).foregroundStyle(Color.secondary)
                }
            }
            .foregroundStyle(Color.primary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background {
                RoundedRectangle(cornerRadius: 12).fill(Color.orange.opacity(0.12)).elevation(.card)
            }
        }
        .buttonStyle(.plain)
    }
}

/// Shown in place of `MyNotesCard` while the recipe is not saved.
struct NotesLockedHint: View {
    var body: some View {
        Label("Save this recipe to add your own notes and ingredient tweaks.", systemImage: "note.text")
            .font(.caption)
            .foregroundStyle(.secondary)
    }
}

/// The user's tweak under an ingredient line, e.g. "✎ Used half".
struct IngredientTweakLine: View {
    let tweak: String

    var body: some View {
        Label(tweak, systemImage: "pencil")
            .font(.subheadline)
            .foregroundStyle(Color.orange)
            .labelStyle(.titleAndIcon)
    }
}

/// Writes the note or an ingredient tweak. The field owns its text until Save.
struct NoteEditorSheet: View {
    let editor: NoteEditor
    let onIntent: (any RecipeDetailIntent) -> Void

    @State private var text: String
    @FocusState private var isFocused: Bool

    init(editor: NoteEditor, onIntent: @escaping (any RecipeDetailIntent) -> Void) {
        self.editor = editor
        self.onIntent = onIntent
        _text = State(initialValue: editor.initialText)
    }

    private var maxLength: Int { Int(editor.maxLength) }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(
                        editor.isTweak ? "e.g. Used half" : "e.g. Used half the sugar, baked 5 mins less",
                        text: $text,
                        axis: .vertical
                    )
                    .lineLimit(editor.isTweak ? 1...2 : 4...10)
                    .textInputAutocapitalization(.sentences)
                    .focused($isFocused)
                    .onChange(of: text) { _, newValue in
                        if newValue.count > maxLength { text = String(newValue.prefix(maxLength)) }
                    }
                    .accessibilityLabel(editor.isTweak ? "Ingredient tweak" : "Your note")
                } header: {
                    if let line = editor.ingredientLine { Text(line) }
                } footer: {
                    Text("\(text.count) / \(maxLength)")
                }
                if editor.canRemove {
                    Section {
                        Button("Remove", role: .destructive) { onIntent(RecipeDetailIntentSaveNoteEdit(text: "")) }
                    }
                }
            }
            .navigationTitle(editor.isTweak ? "Ingredient tweak" : "Your note")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { onIntent(RecipeDetailIntentCancelNoteEdit.shared) }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { onIntent(RecipeDetailIntentSaveNoteEdit(text: text)) }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .onAppear { isFocused = true }
    }
}
