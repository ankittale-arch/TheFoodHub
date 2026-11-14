import SwiftUI
@preconcurrency import Shared

struct FavoritesView: View {
    @StateObject private var screen = SharedViewModel { $0.favoritesViewModel() }
    @State private var removed: RecipeSummary?

    private var viewModel: FavoritesViewModel { screen.instance }

    var body: some View {
        Observing(viewModel.state) { state in
            if state.isLoading {
                ProgressView()
            } else if state.favorites.isEmpty {
                ContentUnavailableView(
                    "No favorites yet",
                    systemImage: "heart",
                    description: Text("Tap the heart on any recipe to save it here.")
                )
            } else {
                ScrollView {
                    LazyVGrid(columns: recipeGridColumns, spacing: 12) {
                        ForEach(state.favorites, id: \.id) { recipe in
                            NavigationLink(value: RecipeRoute(recipe)) {
                                RecipeCard(recipe: recipe, isFavorite: true) {
                                    viewModel.onIntent(intent: FavoritesIntentRemove(recipe: recipe))
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding()
                }
            }
        }
        .navigationTitle("Favorites")
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
                    viewModel.onIntent(intent: FavoritesIntentUndoRemove(recipe: removed))
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
}
