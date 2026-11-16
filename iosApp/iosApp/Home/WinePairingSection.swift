import SwiftUI
@preconcurrency import Shared

/// "Pairs well with": the wine pairing for the recipe centred in the carousel. Mirrors Android's `WinePairingSection`.
struct WinePairingSection: View {
    let state: any WinePairingState
    let onRetry: () -> Void

    var body: some View {
        if !(state is WinePairingStateHidden) {
            VStack(alignment: .leading, spacing: 12) {
                Text("Pairs well with")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(HomePalette.ink)
                card
                    .padding(18)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background {
                        RoundedRectangle(cornerRadius: 26, style: .continuous).fill(.white).elevation(.card)
                    }
                    .animation(.default, value: contentKey)
            }
        }
    }

    @ViewBuilder
    private var card: some View {
        switch onEnum(of: state) {
        case .hidden:
            EmptyView()
        case .loading:
            // The app's food loader in wine colours, laid out like the no-pairing state so the card keeps its shape.
            HStack(spacing: 14) {
                FoodLoaderBadge(
                    size: FoodLoaderBadge.compactSize,
                    background: HomePalette.wine.opacity(0.12),
                    foreground: HomePalette.wine
                )
                Text("Finding a wine…")
                    .font(.system(size: 15))
                    .foregroundStyle(HomePalette.inkMuted)
            }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.updatesFrequently)
        case .noPairing:
            HStack(spacing: 14) {
                NoWineBadge()
                VStack(alignment: .leading, spacing: 4) {
                    Text("No wine pairing for this one")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(HomePalette.ink)
                    Text("It shines on its own. Try sparkling water with a squeeze of lemon, or swipe for another recipe.")
                        .font(.system(size: 14))
                        .lineSpacing(3)
                        .foregroundStyle(HomePalette.inkMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .accessibilityElement(children: .combine)
        case .failed(let failed):
            VStack(alignment: .leading, spacing: 8) {
                Text(failed.error.message).font(.system(size: 15)).foregroundStyle(HomePalette.ink)
                Button("Retry", action: onRetry)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(HomePalette.wine)
            }
        case .ready(let ready):
            PairingContent(pairing: ready.pairing)
                // Fresh expanded/collapsed state for each food.
                .id(ready.pairing.food)
        }
    }

    /// Changes whenever the card shows something different, to animate the resize.
    private var contentKey: String {
        switch onEnum(of: state) {
        case .hidden: "hidden"
        case .loading: "loading"
        case .noPairing: "none"
        case .failed: "failed"
        case .ready(let ready): "ready-\(ready.pairing.food)"
        }
    }
}

/// A wine glass struck through, on a soft wine-coloured circle. SF Symbols has no crossed-out
/// wine glass, and Android draws the same badge.
private struct NoWineBadge: View {
    var body: some View {
        let badge = HomePalette.wine.opacity(0.12)
        ZStack {
            Circle().fill(badge)
            Image(systemName: "wineglass")
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(HomePalette.wine)
            // A gap in the badge colour first, so the slash reads as cutting through the glass.
            Capsule()
                .fill(.white)
                .overlay(Capsule().fill(badge))
                .frame(width: 5, height: 30)
                .rotationEffect(.degrees(-45))
            Capsule()
                .fill(HomePalette.wine)
                .frame(width: 2, height: 28)
                .rotationEffect(.degrees(-45))
        }
        .frame(width: 48, height: 48)
        .accessibilityHidden(true)
    }
}

private struct PairingContent: View {
    let pairing: WinePairing
    @State private var isExpanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if !pairing.wines.isEmpty {
                HStack(spacing: 10) {
                    Image(systemName: "wineglass").foregroundStyle(HomePalette.wine)
                    Text(pairing.wines.joined(separator: " · "))
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(HomePalette.ink)
                }
            }
            if !pairing.explanation.isEmpty {
                // Long explanations open on tap.
                Text(pairing.explanation)
                    .font(.system(size: 14))
                    .lineSpacing(3)
                    .foregroundStyle(HomePalette.inkMuted)
                    .lineLimit(isExpanded ? nil : 3)
                    .padding(.top, 8)
                    .onTapGesture { isExpanded.toggle() }
            }
            if let bottle = pairing.suggestedBottle {
                BottleRow(bottle: bottle).padding(.top, 14)
            }
        }
    }
}

private struct BottleRow: View {
    let bottle: WineBottle

    private var ratingText: String? {
        guard let rating = bottle.rating?.doubleValue else { return nil }
        if let count = bottle.ratingCount?.intValue { return "★ \(rating) (\(count))" }
        return "★ \(rating)"
    }

    var body: some View {
        if let link = bottle.link, let url = URL(string: link) {
            Link(destination: url) { row(showsLinkIcon: true) }
                .buttonStyle(.plain)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(accessibilityText(opening: true))
                .accessibilityAddTraits(.isLink)
        } else {
            row(showsLinkIcon: false)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(accessibilityText(opening: false))
        }
    }

    private func row(showsLinkIcon: Bool) -> some View {
        HStack(spacing: 12) {
            AsyncImage(url: bottle.imageUrl.flatMap(URL.init(string:))) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFit()
                } else {
                    Color.clear
                }
            }
            .frame(width: 44, height: 60)
            .background(.white, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(bottle.title)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(HomePalette.ink)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                let details = [bottle.price, ratingText].compactMap { $0 }.joined(separator: " · ")
                if !details.isEmpty {
                    Text(details).font(.system(size: 13)).foregroundStyle(HomePalette.inkMuted)
                }
            }
            Spacer(minLength: 0)
            if showsLinkIcon {
                Image(systemName: "arrow.up.right.square").foregroundStyle(HomePalette.inkMuted)
            }
        }
        .padding(10)
        .background(HomePalette.pill, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    /// Same wording as Android: "Open Castello Chianti in the shop, $24.99, Rated 4.6 out of 5".
    private func accessibilityText(opening: Bool) -> String {
        let name = opening ? "Open \(bottle.title) in the shop" : bottle.title
        let rating = bottle.rating.map { "Rated \($0.doubleValue) out of 5" }
        return [name, bottle.price, rating].compactMap { $0 }.joined(separator: ", ")
    }
}
