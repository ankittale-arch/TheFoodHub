import SwiftUI
@preconcurrency import Shared

struct ErrorView: View {
    let error: DataError
    let onRetry: () -> Void

    var body: some View {
        ContentUnavailableView {
            Label("Something’s not right", systemImage: "exclamationmark.triangle")
        } description: {
            Text(error.message)
        } actions: {
            Button("Retry", action: onRetry).buttonStyle(.borderedProminent)
        }
    }
}

struct RemoteImage: View {
    let url: String?

    var body: some View {
        AsyncImage(url: url.flatMap(URL.init(string:))) { phase in
            if let image = phase.image {
                image.resizable().scaledToFill()
            } else {
                Rectangle().fill(.quaternary)
            }
        }
    }
}

struct FavoriteButton: View {
    let isFavorite: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: isFavorite ? "heart.fill" : "heart")
                .foregroundStyle(isFavorite ? Color.orange : Color.primary)
                .padding(8)
                .background(.thinMaterial, in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isFavorite ? "Remove from favorites" : "Add to favorites")
    }
}

/// Opens the "Save to cookbook" sheet for a favorite.
struct CookbookButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "book.closed")
                .foregroundStyle(Color.primary)
                .padding(8)
                .background(.thinMaterial, in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Add to cookbook")
    }
}

struct RecipeCard: View {
    let recipe: RecipeSummary
    let isFavorite: Bool
    /// Extra line under the title, e.g. why a search result matched.
    var footer: String?
    let onToggleFavorite: () -> Void
    /// Shows the "Add to cookbook" button when set (favorites only).
    var onAddToCookbook: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Color.clear
                .aspectRatio(4 / 3, contentMode: .fit)
                .overlay { RemoteImage(url: recipe.imageUrl) }
                .clipped()
                .overlay(alignment: .topTrailing) {
                    FavoriteButton(isFavorite: isFavorite, action: onToggleFavorite).padding(6)
                }
                .overlay(alignment: .topLeading) {
                    if let onAddToCookbook {
                        CookbookButton(action: onAddToCookbook).padding(6)
                    }
                }
            VStack(alignment: .leading, spacing: 4) {
                Text(recipe.title)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(2, reservesSpace: true)
                    .multilineTextAlignment(.leading)
                if let footer {
                    Text(footer)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(Color.accentColor)
                        .lineLimit(2)
                }
            }
            .padding(10)
        }
        .clipShape(RoundedRectangle(cornerRadius: 12))
        // Behind the clipped card, so the clip does not cut off the shadow.
        .background {
            RoundedRectangle(cornerRadius: 12).fill(.background.secondary).elevation(.card)
        }
    }
}

/// A short message at the bottom of the screen, with an optional action such as Undo.
struct Toast: View {
    let message: String
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        HStack(spacing: 16) {
            Text(message).font(.subheadline).lineLimit(2)
            if let actionTitle, let action {
                Button(actionTitle, action: action).font(.subheadline.bold())
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(.regularMaterial, in: Capsule())
        .padding(.bottom, 12)
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}

let recipeGridColumns = [GridItem(.adaptive(minimum: 160), spacing: 12)]
