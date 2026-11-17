import SwiftUI
@preconcurrency import Shared

struct RecipeDetailView: View {
    let route: RecipeRoute
    @StateObject private var screen: SharedViewModel<RecipeDetailViewModel>
    @State private var toast: String?

    init(route: RecipeRoute) {
        self.route = route
        _screen = StateObject(wrappedValue: SharedViewModel { $0.recipeDetailViewModel(recipeId: route.id) })
    }

    private var viewModel: RecipeDetailViewModel { screen.instance }

    var body: some View {
        Observing(viewModel.state) { state in
            Group {
                if let recipe = state.recipe {
                    RecipeContent(recipe: recipe, state: state, onIntent: send)
                } else if let error = state.error {
                    ErrorView(error: error) { send(RecipeDetailIntentRetry.shared) }
                } else {
                    ProgressView()
                }
            }
            .navigationTitle(state.recipe?.title ?? route.title)
            .toolbar {
                if state.recipe != nil {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            send(RecipeDetailIntentToggleFavorite.shared)
                        } label: {
                            Image(systemName: state.isFavorite ? "heart.fill" : "heart")
                        }
                        .accessibilityLabel(state.isFavorite ? "Remove from favorites" : "Add to favorites")
                    }
                }
            }
            .sheet(isPresented: Binding(
                get: { state.noteEditor != nil },
                set: { if !$0 { send(RecipeDetailIntentCancelNoteEdit.shared) } }
            )) {
                if let editor = state.noteEditor {
                    NoteEditorSheet(editor: editor, onIntent: send)
                }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .task {
            for await effect in viewModel.effects {
                switch onEnum(of: effect) {
                case .favoriteChanged(let effect):
                    toast = effect.isFavorite ? "Saved to favorites" : "Removed from favorites"
                }
            }
        }
        .overlay(alignment: .bottom) {
            if let toast { Toast(message: toast) }
        }
        .animation(.default, value: toast)
        .task(id: toast) {
            guard toast != nil else { return }
            try? await Task.sleep(for: .seconds(2))
            toast = nil
        }
    }

    private func send(_ intent: any RecipeDetailIntent) {
        viewModel.onIntent(intent: intent)
    }
}

private struct RecipeContent: View {
    let recipe: RecipeDetail
    let state: RecipeDetailState
    let onIntent: (any RecipeDetailIntent) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Color.clear
                    .aspectRatio(16 / 10, contentMode: .fit)
                    .overlay { RemoteImage(url: recipe.imageUrl) }
                    .clipped()

                VStack(alignment: .leading, spacing: 12) {
                    Text(recipe.title).font(.title2.bold())
                    if !metaLine.isEmpty {
                        Text(metaLine).font(.subheadline).foregroundStyle(.secondary)
                    }
                    if !tags.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack {
                                ForEach(tags, id: \.self) { tag in
                                    Text(tag)
                                        .font(.caption)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(.orange.opacity(0.15), in: Capsule())
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal)

                Group {
                    if state.canTakeNotes {
                        MyNotesCard(notes: state.notes, hasIngredients: !recipe.ingredients.isEmpty) {
                            onIntent(RecipeDetailIntentEditNote.shared)
                        }
                    } else {
                        NotesLockedHint()
                    }
                }
                .padding(.horizontal)

                // The user's notes come before Spoonacular's summary, which can run long.
                if !summary.isEmpty {
                    Text(summary).font(.body).padding(.horizontal)
                }

                ingredients
                instructions

                if let source = recipe.sourceUrl.flatMap(URL.init(string:)) {
                    Link(recipe.sourceName.map { "View original recipe from \($0)" } ?? "View original recipe", destination: source)
                        .padding()
                }
            }
        }
    }

    @ViewBuilder
    private var ingredients: some View {
        if !recipe.ingredients.isEmpty {
            section("Ingredients") {
                // Spoonacular can list the same ingredient twice, so identify rows by position.
                ForEach(Array(recipe.ingredients.enumerated()), id: \.offset) { _, ingredient in
                    if state.canTakeNotes {
                        let tweak = state.notes.tweakFor(ingredientLine: ingredient.original)
                        Button {
                            onIntent(RecipeDetailIntentEditIngredientTweak(ingredientLine: ingredient.original))
                        } label: {
                            ingredientRow(ingredient, tweak: tweak).contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityHint(tweak == nil ? "Add a tweak" : "Edit tweak")
                        .accessibilityIdentifier("ingredient")
                    } else {
                        ingredientRow(ingredient, tweak: nil)
                    }
                }
            }
        }
    }

    private func ingredientRow(_ ingredient: Ingredient, tweak: String?) -> some View {
        HStack(spacing: 12) {
            RemoteImage(url: ingredient.imageUrl)
                .frame(width: 40, height: 40)
                .clipShape(Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(ingredient.original)
                if let tweak { IngredientTweakLine(tweak: tweak) }
            }
            Spacer(minLength: 0)
        }
    }

    private var instructions: some View {
        section("Instructions") {
            if recipe.steps.isEmpty {
                Text("No instructions available for this recipe.").foregroundStyle(.secondary)
            } else {
                ForEach(recipe.steps, id: \.number) { step in
                    HStack(alignment: .firstTextBaseline, spacing: 12) {
                        Text("\(step.number).").font(.headline).foregroundStyle(.orange)
                        Text(step.text)
                    }
                }
            }
        }
    }

    private func section(_ title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Divider()
            Text(title).font(.headline)
            content()
        }
        .padding(.horizontal)
    }

    private var metaLine: String {
        var parts: [String] = []
        if let minutes = recipe.readyInMinutes?.intValue { parts.append("\(minutes) min") }
        if let servings = recipe.servings?.intValue { parts.append(servings == 1 ? "1 serving" : "\(servings) servings") }
        if let score = recipe.healthScore?.intValue { parts.append("Health score \(score)") }
        return parts.joined(separator: "  ·  ")
    }

    private var tags: [String] {
        var seen = Set<String>()
        return (recipe.diets + recipe.cuisines + recipe.dishTypes).filter { seen.insert($0).inserted }
    }

    /// Spoonacular summaries are HTML; plain text is enough here.
    private var summary: String {
        recipe.summaryHtml
            .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
