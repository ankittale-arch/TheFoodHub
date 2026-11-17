import SwiftUI
@preconcurrency import Shared

/// "All", one chip per cookbook, and "New cookbook". Selecting a chip filters the grid.
struct CookbookRow: View {
    let state: FavoritesState
    let onIntent: (any FavoritesIntent) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ChipButton(label: "All · \(state.favorites.count)", isSelected: state.selectedCookbookId == nil) {
                    onIntent(FavoritesIntentSelectCookbook(cookbookId: nil))
                }
                ForEach(state.cookbooks, id: \.id) { cookbook in
                    ChipButton(
                        label: "\(cookbook.name) · \(cookbook.recipeCount)",
                        isSelected: state.selectedCookbookId?.int64Value == cookbook.id
                    ) {
                        onIntent(FavoritesIntentSelectCookbook(cookbookId: KotlinLong(value: cookbook.id)))
                    }
                }
                ChipButton(label: "New cookbook", systemImage: "plus") {
                    onIntent(FavoritesIntentStartNewCookbook(recipeToAdd: nil))
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
        }
        .scrollClipDisabled()
    }
}

/// "Save to cookbook": a checkmark per cookbook. Each tap is saved straight away.
struct CookbookPickerSheet: View {
    let picker: CookbookPicker
    let cookbooks: [Cookbook]
    /// The name prompt opens on top of this sheet when "New cookbook" is tapped here.
    let nameEditor: CookbookNameEditor?
    let onIntent: (any FavoritesIntent) -> Void

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(cookbooks, id: \.id) { cookbook in
                        row(cookbook, isOn: picker.contains(cookbookId: cookbook.id))
                    }
                    Button {
                        onIntent(FavoritesIntentStartNewCookbook(recipeToAdd: picker.recipe))
                    } label: {
                        Label("New cookbook", systemImage: "plus")
                    }
                } header: {
                    Text(picker.recipe.title)
                }
            }
            .navigationTitle("Save to cookbook")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { onIntent(FavoritesIntentCloseCookbookPicker.shared) }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .sheet(isPresented: Binding(
            get: { nameEditor != nil },
            set: { if !$0 { onIntent(FavoritesIntentCancelCookbookName.shared) } }
        )) {
            if let nameEditor {
                CookbookNamePrompt(editor: nameEditor, onIntent: onIntent)
            }
        }
    }

    private func row(_ cookbook: Cookbook, isOn: Bool) -> some View {
        Button {
            onIntent(FavoritesIntentToggleCookbook(cookbookId: cookbook.id))
        } label: {
            HStack(spacing: 12) {
                Image(systemName: isOn ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(isOn ? Color.orange : Color.secondary)
                VStack(alignment: .leading, spacing: 2) {
                    Text(cookbook.name).foregroundStyle(Color.primary)
                    Text(recipeCount(cookbook.recipeCount)).font(.caption).foregroundStyle(Color.secondary)
                }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }
}

/// Names a new cookbook or renames one. The field owns its text; errors come back from the ViewModel.
struct CookbookNamePrompt: View {
    let editor: CookbookNameEditor
    let onIntent: (any FavoritesIntent) -> Void

    @State private var name: String
    @FocusState private var isFocused: Bool

    init(editor: CookbookNameEditor, onIntent: @escaping (any FavoritesIntent) -> Void) {
        self.editor = editor
        self.onIntent = onIntent
        _name = State(initialValue: editor.initialName)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("e.g. Quick Weeknight", text: $name)
                        .textInputAutocapitalization(.words)
                        .submitLabel(.done)
                        .focused($isFocused)
                        .onSubmit(save)
                        .accessibilityLabel("Name")
                } header: {
                    Text("Name")
                } footer: {
                    if let error = editor.error {
                        Text(error.message).foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle(editor.isRename ? "Rename cookbook" : "New cookbook")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { onIntent(FavoritesIntentCancelCookbookName.shared) }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(editor.isRename ? "Save" : "Create", action: save)
                }
            }
        }
        .presentationDetents([.medium])
        .onAppear { isFocused = true }
    }

    private func save() {
        onIntent(FavoritesIntentSaveCookbookName(name: name))
    }
}

private func recipeCount(_ count: Int32) -> String {
    count == 1 ? "1 recipe" : "\(count) recipes"
}

extension CookbookNameError {
    /// Same wording as the Android app's strings.xml.
    var message: String {
        switch self {
        case .blank: "Give your cookbook a name."
        case .tooLong: "Keep it to \(Cookbook.companion.MAX_NAME_LENGTH) characters or fewer."
        case .taken: "You already have a cookbook with this name."
        }
    }
}
