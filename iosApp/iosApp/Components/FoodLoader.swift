import SwiftUI

/// The animated part of the loader: a soft circle in which food icons pop in one after another.
/// Mirrors Android's `FoodLoaderBadge`: same icon order and timing. It is hidden from VoiceOver;
/// whatever shows it says what is loading (see `FoodLoader`).
///
/// With Reduce Motion on, icons crossfade in place and the circle stays still.
struct FoodLoaderBadge: View {
    /// Full size, as in full-screen loading states.
    static let size: CGFloat = 88
    /// For a loader inside a card, next to a line of text.
    static let compactSize: CGFloat = 48

    var size: CGFloat = FoodLoaderBadge.size
    var background: Color = .accentColor.opacity(0.15)
    var foreground: Color = .accentColor

    /// Cutlery, a meal, wine, fish, cake, coffee.
    private static let symbols = [
        "fork.knife", "takeoutbag.and.cup.and.straw", "wineglass", "fish", "birthday.cake", "cup.and.saucer",
    ]
    /// How long each icon shows: short enough that even a one-second load shows a change. Android uses the same value.
    private static let iconDuration: Duration = .milliseconds(500)

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var index = 0
    @State private var isBreathing = false

    var body: some View {
        ZStack {
            Circle().fill(background)
            Image(systemName: Self.symbols[index])
                .font(.system(size: size * 0.39, weight: .medium))
                .foregroundStyle(foreground)
                // A new identity per icon, so SwiftUI runs the transition on each change.
                .id(index)
                .transition(iconTransition)
        }
        .frame(width: size, height: size)
        .scaleEffect(isBreathing ? 1.06 : 1)
        .accessibilityHidden(true)
        .task {
            if !reduceMotion {
                withAnimation(.easeInOut(duration: 0.5).repeatForever(autoreverses: true)) { isBreathing = true }
            }
            while !Task.isCancelled {
                try? await Task.sleep(for: Self.iconDuration)
                guard !Task.isCancelled else { break }
                // The new icon pops in with a little bounce while the old one grows and fades.
                withAnimation(reduceMotion ? .easeInOut(duration: 0.2) : .spring(response: 0.35, dampingFraction: 0.5)) {
                    index = (index + 1) % Self.symbols.count
                }
            }
        }
    }

    private var iconTransition: AnyTransition {
        reduceMotion
            ? .opacity
            // Fades are short and fixed, as on Android, so the circle never looks empty while the
            // scale still bounces on the spring.
            : .asymmetric(
                insertion: .scale(scale: 0.3).combined(with: .opacity.animation(.easeOut(duration: 0.12))),
                removal: .scale(scale: 1.4).combined(with: .opacity.animation(.easeIn(duration: 0.12)))
            )
    }
}

/// A `FoodLoaderBadge` with `label` underneath, read out by VoiceOver. Mirrors Android's `FoodLoader`.
struct FoodLoader: View {
    let label: String
    var background: Color = .accentColor.opacity(0.15)
    var foreground: Color = .accentColor
    var labelColor: Color = .secondary

    var body: some View {
        VStack(spacing: 16) {
            FoodLoaderBadge(background: background, foreground: foreground)
            Text(label)
                .font(.headline)
                .foregroundStyle(labelColor)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityAddTraits(.updatesFrequently)
    }
}

#Preview {
    VStack(spacing: 40) {
        FoodLoader(label: "Finding recipes…")
        HStack(spacing: 14) {
            FoodLoaderBadge(size: FoodLoaderBadge.compactSize, background: HomePalette.wine.opacity(0.12), foreground: HomePalette.wine)
            Text("Finding a wine…")
        }
    }
}
