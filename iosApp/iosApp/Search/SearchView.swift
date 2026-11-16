import SwiftUI
@preconcurrency import Shared

struct SearchView: View {
    /// True activates the search field (used when arriving from the home screen's search bar).
    @Binding var isSearchActive: Bool
    @StateObject private var screen = SharedViewModel { $0.searchViewModel() }
    @State private var loadMoreError: String?
    @State private var openSheet: SearchSheet?
    /// The search field owns its text. Binding it to `state.query` instead drops keystrokes:
    /// `Observing` delivers each new state asynchronously, so a fast second keystroke would be
    /// applied to the stale query and overwrite the first.
    @State private var queryText = ""

    private var viewModel: SearchViewModel { screen.instance }

    /// Lets UI tests find the results grid without matching other scroll views, such as the keyboard's suggestion bar.
    static let resultsIdentifier = "searchResults"

    var body: some View {
        Observing(viewModel.state) { state in
            VStack(spacing: 0) {
                SearchFilterBar(
                    state: state,
                    onIntent: { viewModel.onIntent(intent: $0) },
                    onOpenSheet: { openSheet = $0 }
                )
                content(state).frame(maxHeight: .infinity)
            }
            .sheet(item: $openSheet) { sheet in
                switch sheet {
                case .ingredients:
                    IngredientsSheet(initial: state.ingredients) {
                        viewModel.onIntent(intent: SearchIntentApplyIngredients(ingredients: $0))
                    }
                case .nutrition:
                    NutritionSheet(initial: state.nutrients) {
                        viewModel.onIntent(intent: SearchIntentApplyNutrients(filter: $0))
                    }
                }
            }
        }
        .searchable(text: $queryText, isPresented: $isSearchActive, prompt: "Search recipes, e.g. pasta")
        .onChange(of: queryText) { _, query in
            viewModel.onIntent(intent: SearchIntentQueryChanged(query: query))
        }
        .navigationTitle("The FoodHub")
        .task {
            for await effect in viewModel.effects {
                switch onEnum(of: effect) {
                case .showError(let effect):
                    loadMoreError = effect.error.message
                }
            }
        }
        .overlay(alignment: .bottom) {
            if let loadMoreError { Toast(message: loadMoreError) }
        }
        .animation(.default, value: loadMoreError)
        .task(id: loadMoreError) {
            guard loadMoreError != nil else { return }
            try? await Task.sleep(for: .seconds(3))
            loadMoreError = nil
        }
    }

    @ViewBuilder
    private func content(_ state: SearchState) -> some View {
        if !state.recipes.isEmpty {
            results(state)
        } else if state.isLoading {
            ProgressView()
        } else if let error = state.error {
            ErrorView(error: error) { viewModel.onIntent(intent: SearchIntentRetry.shared) }
        } else if state.hasSearched && state.hasFilters {
            ContentUnavailableView(
                "No recipes match these filters.",
                systemImage: "line.3.horizontal.decrease.circle",
                description: Text("Try removing an ingredient or loosening a nutrition limit.")
            )
        } else if state.hasSearched {
            ContentUnavailableView.search(text: state.query.trimmingCharacters(in: .whitespaces))
        } else {
            ContentUnavailableView(
                "What are you cooking today?",
                systemImage: "fork.knife",
                description: Text("Search by dish, or filter by what’s in your kitchen and by nutrition.")
            )
        }
    }

    private func results(_ state: SearchState) -> some View {
        let recipes = state.recipes
        return ScrollView {
            LazyVGrid(columns: recipeGridColumns, spacing: 12) {
                ForEach(Array(recipes.enumerated()), id: \.element.id) { index, recipe in
                    NavigationLink(value: RecipeRoute(recipe)) {
                        RecipeCard(
                            recipe: recipe,
                            isFavorite: state.isFavorite(recipeId: recipe.id),
                            footer: state.matchFor(recipeId: recipe.id)?.summary,
                            onToggleFavorite: { viewModel.onIntent(intent: SearchIntentToggleFavorite(recipe: recipe)) }
                        )
                    }
                    .buttonStyle(.plain)
                    .onAppear {
                        // The ViewModel ignores this while a page is loading or the end is reached.
                        if index >= recipes.count - 6 {
                            viewModel.onIntent(intent: SearchIntentLoadNextPage.shared)
                        }
                    }
                }
            }
            .padding()

            if state.isLoadingMore {
                ProgressView().padding()
            }
        }
        .accessibilityIdentifier(SearchView.resultsIdentifier)
        .overlay(alignment: .top) {
            if state.isLoading { ProgressView().padding(8).background(.regularMaterial, in: Capsule()) }
        }
    }
}
