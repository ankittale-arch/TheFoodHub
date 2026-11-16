import SwiftUI
@preconcurrency import Shared

struct HomeView: View {
    /// Switches to the Search tab with the search field active.
    let onSearchTap: () -> Void

    @StateObject private var screen = SharedViewModel { $0.homeViewModel() }

    var body: some View {
        Observing(screen.instance.state) { state in
            HomeContent(state: state, onIntent: { screen.instance.onIntent(intent: $0) }, onSearchTap: onSearchTap)
        }
    }
}

/// Stateless home screen: renders a HomeState and reports intents (usable in previews).
struct HomeContent: View {
    let state: HomeState
    let onIntent: (any HomeIntent) -> Void
    let onSearchTap: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                headline.padding(.horizontal, 20)
                searchBar.padding(.horizontal, 20).padding(.top, 22)
                chips(state).padding(.top, 20)
                Text(sectionTitle(state))
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(HomePalette.ink)
                    .padding(.horizontal, 20)
                    .padding(.top, 24)
                carouselArea(state).padding(.top, 16)
                WinePairingSection(state: state.winePairing, onRetry: { send(HomeIntentRetryWinePairing.shared) })
                    .padding(.horizontal, 20)
                    .padding(.top, 28)
            }
            .padding(.top, 16)
            .padding(.bottom, 16)
        }
        .scrollIndicators(.hidden)
        .background(
            LinearGradient(
                colors: [HomePalette.backgroundTop, HomePalette.backgroundBottom],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
        )
        .toolbar(.hidden, for: .navigationBar)
    }

    private var headline: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Let’s plan").foregroundStyle(HomePalette.inkMuted).font(.system(size: 38, weight: .semibold))
            Text("your next feast!").foregroundStyle(HomePalette.ink).font(.system(size: 38, weight: .bold))
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    /// Looks like a field; tapping it opens the Search tab.
    private var searchBar: some View {
        Button(action: onSearchTap) {
            HStack(spacing: 12) {
                Image(systemName: "frying.pan").font(.system(size: 18)).foregroundStyle(HomePalette.ink)
                Text("Let’s cook something…").foregroundStyle(HomePalette.inkMuted).font(.system(size: 16))
                Spacer()
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(HomePalette.ink)
                    .frame(width: 48, height: 48)
                    .background(Circle().fill(HomePalette.fieldButton))
            }
            .padding(.leading, 20)
            .padding(.trailing, 6)
            .frame(height: 60)
            .background { Capsule().fill(HomePalette.field).elevation(.field) }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Let’s cook something…")
    }

    private func chips(_ state: HomeState) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                chip("All", selected: state.category == .all) { send(HomeIntentSelectCategory(category: .all)) }
                chip("Popular", selected: state.category == .popular) { send(HomeIntentSelectCategory(category: .popular)) }
                chip("Quick & Easy", selected: state.category == .quickAndEasy) {
                    send(HomeIntentSelectCategory(category: .quickAndEasy))
                }
                dietsChip(state)
            }
            .padding(.horizontal, 20)
        }
        // Lets the chips' shadows draw past the scroll view's edges.
        .scrollClipDisabled()
    }

    private func chip(_ title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) { chipLabel(title, selected: selected) }
            .buttonStyle(.plain)
            .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
    }

    private func chipLabel(_ title: String, selected: Bool) -> some View {
        Text(title)
            .font(.system(size: 16, weight: .medium))
            .foregroundStyle(selected ? .white : HomePalette.ink)
            .padding(.horizontal, 22)
            .frame(minHeight: 52)
            .background(Capsule().fill(selected ? HomePalette.ink : HomePalette.chip).elevation(.chip))
    }

    private func dietsChip(_ state: HomeState) -> some View {
        let selected = state.category == .diets
        return Menu {
            ForEach(Self.diets, id: \.self) { diet in
                Button {
                    send(HomeIntentSelectDiet(diet: diet))
                } label: {
                    if selected && diet == state.diet {
                        Label(diet.title, systemImage: "checkmark")
                    } else {
                        Text(diet.title)
                    }
                }
            }
        } label: {
            chipLabel(selected ? state.diet.title : "Diets", selected: selected)
        }
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    @ViewBuilder
    private func carouselArea(_ state: HomeState) -> some View {
        if !state.recipes.isEmpty {
            RecipeCarousel(recipes: state.recipes, onRecipeFocused: { send(HomeIntentFocusRecipe(recipeId: $0.id)) })
                // A new category starts again at its first card.
                .id("\(state.category)-\(state.diet)")
        } else if state.isLoading {
            FoodLoader(label: "Finding recipes…", background: .white, foreground: HomePalette.olive, labelColor: HomePalette.inkMuted)
                .frame(maxWidth: .infinity)
                .frame(height: Self.carouselHeight)
        } else {
            VStack(spacing: 16) {
                Text(state.error?.message ?? "No recipes here right now. Try another category.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(HomePalette.ink)
                Button("Retry") { send(HomeIntentRetry.shared) }
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(HomePalette.ink, in: Capsule())
            }
            .padding(.horizontal, 32)
            .frame(maxWidth: .infinity)
            .frame(height: Self.carouselHeight)
        }
    }

    private func sectionTitle(_ state: HomeState) -> String {
        switch state.category {
        case .all, .popular: "Most popular recipes"
        case .quickAndEasy: "Quick & easy recipes"
        case .diets: "\(state.diet.title) recipes"
        }
    }

    private func send(_ intent: any HomeIntent) {
        onIntent(intent)
    }

    static let carouselHeight: CGFloat = 340
    private static let diets: [Diet] = [.vegetarian, .vegan, .glutenFree, .ketogenic, .paleo]
}

extension Diet {
    var title: String {
        switch self {
        case .vegetarian: "Vegetarian"
        case .vegan: "Vegan"
        case .glutenFree: "Gluten free"
        case .ketogenic: "Ketogenic"
        case .paleo: "Paleo"
        }
    }
}

#Preview {
    NavigationStack {
        HomeContent(
            state: HomeState(
                category: .all,
                diet: .vegetarian,
                recipes: [
                    PopularRecipe(id: 716429, title: "Pasta with Garlic, Scallions, Cauliflower & Breadcrumbs",
                                  imageUrl: "https://img.spoonacular.com/recipes/716429-556x370.jpg",
                                  readyInMinutes: KotlinInt(int: 45), likes: KotlinInt(int: 209), rating: KotlinDouble(double: 4.2),
                                  winePairingFood: "pasta"),
                    PopularRecipe(id: 715538, title: "Bruschetta Style Pork & Pasta",
                                  imageUrl: "https://img.spoonacular.com/recipes/715538-556x370.jpg",
                                  readyInMinutes: KotlinInt(int: 35), likes: KotlinInt(int: 385), rating: KotlinDouble(double: 4.8),
                                  winePairingFood: "pork"),
                ],
                isLoading: false,
                error: nil,
                winePairing: WinePairingStateReady(pairing: WinePairing(
                    food: "pasta",
                    wines: ["Chianti", "Pinot Grigio"],
                    explanation: "Pasta pairs with wine that matches its sauce: a bright Chianti for tomato, Pinot Grigio for cream.",
                    suggestedBottle: WineBottle(title: "Castello di Ama Chianti Classico", price: "$24.99", imageUrl: nil,
                                                rating: KotlinDouble(double: 4.6), ratingCount: KotlinInt(int: 31),
                                                link: "https://example.com")
                ))
            ),
            onIntent: { _ in },
            onSearchTap: {}
        )
    }
}
