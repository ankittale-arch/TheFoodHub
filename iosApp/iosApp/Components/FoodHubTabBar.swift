import SwiftUI

enum AppTab: CaseIterable, Hashable {
    case home, search, favorites, calculator

    var title: String {
        switch self {
        case .home: "Home"
        case .search: "Search"
        case .favorites: "Favorites"
        case .calculator: "Kitchen"
        }
    }

    var systemImage: String {
        switch self {
        case .home: "house"
        case .search: "magnifyingglass"
        case .favorites: "heart"
        // SF Symbols has no calculator glyph; this is its standard stand-in.
        case .calculator: "plus.forwardslash.minus"
        }
    }
}

/// The white circle behind each icon. 48 pt keeps four tabs plus the selected label within the
/// narrowest iPhones. Android uses the same size (`BarIconSize` in FoodHubBottomBar.kt).
let barIconSize: CGFloat = 48

/// Floating pill bar from the home design: the selected tab expands into a green pill with its label.
struct FoodHubTabBar: View {
    @Binding var selection: AppTab

    var body: some View {
        HStack(spacing: 8) {
            ForEach(AppTab.allCases, id: \.self) { tab in
                item(tab)
            }
        }
        .padding(6)
        .background(HomePalette.ink, in: Capsule())
        .animation(.snappy, value: selection)
    }

    private func item(_ tab: AppTab) -> some View {
        let isSelected = tab == selection
        return Button {
            selection = tab
        } label: {
            HStack(spacing: 14) {
                Image(systemName: tab.systemImage)
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(HomePalette.ink)
                    .frame(width: barIconSize, height: barIconSize)
                    .background(Circle().fill(.white))
                if isSelected {
                    Text(tab.title)
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(.white)
                        .padding(.trailing, 22)
                        .transition(.opacity.combined(with: .move(edge: .leading)))
                }
            }
            .background(Capsule().fill(isSelected ? HomePalette.olive : .clear))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(tab.title)
        .accessibilityAddTraits(isSelected ? [.isSelected, .isButton] : .isButton)
    }
}
