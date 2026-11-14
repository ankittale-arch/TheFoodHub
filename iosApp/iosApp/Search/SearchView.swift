import SwiftUI
@preconcurrency import Shared

struct SearchView: View {
    /// True activates the search field (used when arriving from the home screen's search bar).
    @Binding var isSearchActive: Bool
    @StateObject private var screen = SharedViewModel { $0.searchViewModel() }
    @State private var loadMoreError: String?

    private var viewModel: SearchViewModel { screen.instance }

    var body: some View {
        Observing(viewModel.state) { state in
            content(state)
                .searchable(
                    text: Binding(
                        get: { state.query },
                        set: { viewModel.onIntent(intent: SearchIntentQueryChanged(query: $0)) }
                    ),
                    isPresented: $isSearchActive,
                    prompt: "Search recipes, e.g. pasta"
                )
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
        } else if state.hasSearched {
            ContentUnavailableView.search(text: state.query.trimmingCharacters(in: .whitespaces))
        } else {
            ContentUnavailableView(
                "What are you cooking today?",
                systemImage: "fork.knife",
                description: Text("Search by dish, ingredient or cuisine.")
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
        .overlay(alignment: .top) {
            if state.isLoading { ProgressView().padding(8).background(.regularMaterial, in: Capsule()) }
        }
    }
}
