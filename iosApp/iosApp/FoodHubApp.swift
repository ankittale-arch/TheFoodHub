import SwiftUI
@preconcurrency import Shared

@main
struct FoodHubApp: App {
    init() {
        // Starts Koin. The Spoonacular key is generated into Shared from local.properties.
        #if DEBUG
        FoodHub.shared.start(isDebug: true)
        #else
        FoodHub.shared.start(isDebug: false)
        #endif
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}

struct RootView: View {
    @State private var tab: AppTab = .home
    @State private var homePath = NavigationPath()
    @State private var searchPath = NavigationPath()
    @State private var favoritesPath = NavigationPath()
    @State private var calculatorPath = NavigationPath()
    @State private var isSearchActive = false

    var body: some View {
        ZStack(alignment: .bottom) {
            TabView(selection: $tab) {
                NavigationStack(path: $homePath) {
                    HomeView(onSearchTap: {
                        tab = .search
                        isSearchActive = true
                    })
                    .reservingTabBarSpace()
                    .navigationDestination(for: RecipeRoute.self) { RecipeDetailView(route: $0) }
                }
                .tag(AppTab.home)
                .toolbar(.hidden, for: .tabBar)

                NavigationStack(path: $searchPath) {
                    SearchView(isSearchActive: $isSearchActive)
                        .reservingTabBarSpace()
                        .navigationDestination(for: RecipeRoute.self) { RecipeDetailView(route: $0) }
                }
                .tag(AppTab.search)
                .toolbar(.hidden, for: .tabBar)

                NavigationStack(path: $favoritesPath) {
                    FavoritesView()
                        .reservingTabBarSpace()
                        .navigationDestination(for: RecipeRoute.self) { RecipeDetailView(route: $0) }
                }
                .tag(AppTab.favorites)
                .toolbar(.hidden, for: .tabBar)

                NavigationStack(path: $calculatorPath) {
                    CalculatorView()
                        .reservingTabBarSpace()
                }
                .tag(AppTab.calculator)
                .toolbar(.hidden, for: .tabBar)
            }
            .tint(.orange)

            // Like Android, the bar shows on the top-level screens only, not on recipe details.
            if isOnTopLevelScreen {
                FoodHubTabBar(selection: $tab)
                    .padding(.bottom, 8)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.default, value: isOnTopLevelScreen)
    }

    private var isOnTopLevelScreen: Bool {
        switch tab {
        case .home: homePath.isEmpty
        case .search: searchPath.isEmpty
        case .favorites: favoritesPath.isEmpty
        case .calculator: calculatorPath.isEmpty
        }
    }
}

private extension View {
    /// Keeps scrollable content clear of the floating tab bar: its icons, 6 pt padding above and
    /// below, its 8 pt bottom margin, and a little air.
    func reservingTabBarSpace() -> some View {
        safeAreaInset(edge: .bottom, spacing: 0) { Color.clear.frame(height: barIconSize + 12 + 8 + 4) }
    }
}

/// Navigation value for the detail screen. The title is shown while details load.
struct RecipeRoute: Hashable {
    let id: Int32
    let title: String

    init(id: Int32, title: String) {
        self.id = id
        self.title = title
    }

    init(_ recipe: RecipeSummary) {
        self.init(id: recipe.id, title: recipe.title)
    }
}
