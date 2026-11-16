import SwiftUI
@preconcurrency import Shared

private let carouselSideInset: CGFloat = 52
private let neighbourScale: CGFloat = 0.86
private let neighbourPeek: CGFloat = 18

/// Stacked carousel from the design: the centred card is full size and in front; its neighbours
/// shrink and slide in behind it so only a small edge of each shows.
struct RecipeCarousel: View {
    let recipes: [PopularRecipe]
    /// The card the carousel moved to. The ViewModel waits for it to settle before fetching anything.
    var onRecipeFocused: (PopularRecipe) -> Void = { _ in }
    @State private var currentID: Int32?

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(spacing: 0) {
                ForEach(Array(recipes.enumerated()), id: \.element.id) { index, recipe in
                    NavigationLink(value: RecipeRoute(id: recipe.id, title: recipe.title)) {
                        RecipeSlide(recipe: recipe)
                    }
                    .buttonStyle(.plain)
                    // The container size already excludes the content margins, so this is the visible width minus both insets.
                    .containerRelativeFrame(.horizontal)
                    .zIndex(-Double(abs(index - currentIndex)))
                    .visualEffect { content, proxy in
                        let frame = proxy.frame(in: .scrollView(axis: .horizontal))
                        let visibleWidth = proxy.bounds(of: .scrollView(axis: .horizontal))?.width ?? frame.width
                        let offset = (frame.midX - visibleWidth / 2) / max(frame.width, 1)
                        let fraction = min(abs(offset), 1)
                        let scale = 1 - (1 - neighbourScale) * fraction
                        let pull = frame.width * (1 + neighbourScale) / 2 - neighbourPeek
                        return content
                            .scaleEffect(scale)
                            .offset(x: -offset * pull)
                            .opacity(abs(offset) <= 1 ? 1 : max(0, 2 - abs(offset)))
                    }
                    .id(recipe.id)
                }
            }
            .scrollTargetLayout()
        }
        .contentMargins(.horizontal, carouselSideInset, for: .scrollContent)
        .scrollTargetBehavior(.viewAligned)
        .scrollPosition(id: $currentID)
        .onChange(of: currentID) { _, id in
            if let recipe = recipes.first(where: { $0.id == id }) { onRecipeFocused(recipe) }
        }
        .scrollClipDisabled()
        .frame(height: HomeContent.carouselHeight)
    }

    private var currentIndex: Int {
        guard let currentID, let index = recipes.firstIndex(where: { $0.id == currentID }) else { return 0 }
        return index
    }
}

private struct RecipeSlide: View {
    let recipe: PopularRecipe

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 36, style: .continuous)
        Color.clear
            .overlay { RemoteImage(url: recipe.imageUrl) }
            .overlay(alignment: .topLeading) {
                if let minutes = recipe.readyInMinutes?.intValue {
                    StatPill(systemImage: "clock", text: "\(minutes) min", background: HomePalette.olive, foreground: .white)
                        .padding(14)
                }
            }
            .overlay(alignment: .topTrailing) {
                if let likes = recipe.likes?.intValue {
                    StatPill(systemImage: "heart", text: "\(likes)", background: .white, foreground: HomePalette.ink)
                        .accessibilityLabel("\(likes) likes")
                        .padding(14)
                }
            }
            .overlay(alignment: .bottom) { titleCard.padding(14) }
            .clipShape(shape)
            .overlay(shape.strokeBorder(.white.opacity(0.9), lineWidth: 3))
            .background(shape.fill(HomePalette.pill).elevation(.featuredCard))
            .contentShape(shape)
    }

    private var titleCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(recipe.title)
                .font(.system(size: 19, weight: .semibold))
                .foregroundStyle(HomePalette.ink)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
            if let rating = recipe.rating?.doubleValue {
                let text = String(format: "%.1f", rating)
                StatPill(systemImage: "star", text: text, background: HomePalette.pill, foreground: HomePalette.ink, small: true)
                    .accessibilityLabel("Rated \(text) out of 5")
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background { RoundedRectangle(cornerRadius: 26, style: .continuous).fill(.white).elevation(.card) }
    }
}

private struct StatPill: View {
    let systemImage: String
    let text: String
    let background: Color
    let foreground: Color
    var small = false

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: systemImage).font(.system(size: small ? 12 : 15, weight: .medium))
            Text(text).font(.system(size: small ? 12 : 15, weight: .medium))
        }
        .foregroundStyle(foreground)
        .padding(.horizontal, small ? 12 : 14)
        .padding(.vertical, small ? 6 : 9)
        .background(background, in: Capsule())
        .accessibilityElement(children: .combine)
    }
}
