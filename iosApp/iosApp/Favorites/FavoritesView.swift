import SwiftUI
@preconcurrency import Shared

struct FavoritesView: View {
    @StateObject private var screen = SharedViewModel { $0.favoritesViewModel() }
    @State private var removed: RecipeSummary?
    @State private var cookbookToDelete: Cookbook?

    private var viewModel: FavoritesViewModel { screen.instance }

    var body: some View {
        Observing(viewModel.state) { state in
            content(state)
                .toolbar {
                    if let cookbook = state.selectedCookbook {
                        ToolbarItem(placement: .topBarTrailing) { cookbookMenu(cookbook) }
                    }
                }
                .sheet(isPresented: Binding(
                    get: { state.cookbookPicker != nil },
                    set: { if !$0 { send(FavoritesIntentCloseCookbookPicker.shared) } }
                )) {
                    if let picker = state.cookbookPicker {
                        CookbookPickerSheet(picker: picker, cookbooks: state.cookbooks, nameEditor: state.nameEditor, onIntent: send)
                    }
                }
                // While the picker is open, it presents the name prompt itself (a sheet on a sheet).
                .sheet(isPresented: Binding(
                    get: { state.nameEditor != nil && state.cookbookPicker == nil },
                    set: { if !$0 { send(FavoritesIntentCancelCookbookName.shared) } }
                )) {
                    if let editor = state.nameEditor {
                        CookbookNamePrompt(editor: editor, onIntent: send)
                    }
                }
        }
        .navigationTitle("Favorites")
        .alert(
            "Delete “\(cookbookToDelete?.name ?? "")”?",
            isPresented: Binding(get: { cookbookToDelete != nil }, set: { if !$0 { cookbookToDelete = nil } }),
            presenting: cookbookToDelete
        ) { cookbook in
            Button("Delete", role: .destructive) { send(FavoritesIntentDeleteCookbook(cookbookId: cookbook.id)) }
            Button("Cancel", role: .cancel) {}
        } message: { _ in
            Text("Its recipes stay in your favorites.")
        }
        .task {
            for await effect in viewModel.effects {
                switch onEnum(of: effect) {
                case .removed(let effect):
                    removed = effect.recipe
                }
            }
        }
        .overlay(alignment: .bottom) {
            if let removed {
                Toast(message: "Removed “\(removed.title)”", actionTitle: "Undo") {
                    send(FavoritesIntentUndoRemove(recipe: removed))
                    self.removed = nil
                }
            }
        }
        .animation(.default, value: removed?.id)
        .task(id: removed?.id) {
            guard removed != nil else { return }
            try? await Task.sleep(for: .seconds(4))
            removed = nil
        }
    }

    @ViewBuilder
    private func content(_ state: FavoritesState) -> some View {
        if state.isLoading {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if state.favorites.isEmpty {
            ContentUnavailableView(
                "No favorites yet",
                systemImage: "heart",
                description: Text("Tap the heart on any recipe to save it here.")
            )
        } else {
            VStack(spacing: 0) {
                CookbookRow(state: state, onIntent: send)
                if let cookbook = state.selectedCookbook, state.visibleRecipes.isEmpty {
                    ContentUnavailableView(
                        "Nothing in \(cookbook.name) yet",
                        systemImage: "book.closed",
                        description: Text("Tap the book icon on any favorite to add it here.")
                    )
                    .frame(maxHeight: .infinity)
                } else {
                    grid(state.visibleRecipes)
                }
            }
        }
    }

    private func grid(_ recipes: [RecipeSummary]) -> some View {
        ScrollView {
            LazyVGrid(columns: recipeGridColumns, spacing: 12) {
                ForEach(recipes, id: \.id) { recipe in
                    NavigationLink(value: RecipeRoute(recipe)) {
                        RecipeCard(
                            recipe: recipe,
                            isFavorite: true,
                            onToggleFavorite: { send(FavoritesIntentRemove(recipe: recipe)) },
                            onAddToCookbook: { send(FavoritesIntentOpenCookbookPicker(recipe: recipe)) }
                        )
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("favoriteRecipe")
                }
            }
            .padding()
        }
    }

    /// Rename and delete for the selected cookbook.
    private func cookbookMenu(_ cookbook: Cookbook) -> some View {
        Menu {
            Button("Rename", systemImage: "pencil") {
                send(FavoritesIntentStartRenameCookbook(cookbookId: cookbook.id))
            }
            Button("Delete cookbook", systemImage: "trash", role: .destructive) {
                cookbookToDelete = cookbook
            }
        } label: {
            Image(systemName: "ellipsis.circle")
        }
        .accessibilityLabel("Cookbook options")
    }

    private func send(_ intent: any FavoritesIntent) {
        viewModel.onIntent(intent: intent)
    }
}
