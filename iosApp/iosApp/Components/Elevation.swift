import SwiftUI

/// How far each kind of surface is raised, named for the surface rather than a number, so a chip
/// or card looks the same wherever it appears.
///
/// Mirrors Android's `FoodHubElevation`; change both together so the platforms keep matching.
enum Elevation {
    /// Filter, category, ingredient and suggestion chips.
    case chip
    /// Pill-shaped fields, such as the home search bar.
    case field
    /// Recipe cards in grids, and panels laid over an image.
    case card
    /// The featured recipe cards in the home carousel, which sit above everything around them.
    case featuredCard

    /// The soft ambient shadow, which grows with height. These match Android's 1 dp and 3 dp.
    fileprivate var ambientShadow: (radius: CGFloat, y: CGFloat) {
        switch self {
        case .chip, .field, .card: (radius: 1.5, y: 1)
        case .featuredCard: (radius: 3, y: 2)
        }
    }
}

extension View {
    /// Casts the shadow of a surface raised to `elevation`.
    ///
    /// Material draws a raised surface with two shadows: a tight key shadow and a soft ambient one
    /// that grows with height. SwiftUI's radius is about half of Material's blur.
    ///
    /// Apply it to a background shape, not to a view that contains text: SwiftUI shadows every
    /// opaque pixel, so a shadowed label would get a shadow behind each letter.
    func elevation(_ elevation: Elevation) -> some View {
        let ambient = elevation.ambientShadow
        return self
            .shadow(color: .black.opacity(0.3), radius: 1, x: 0, y: 1)
            .shadow(color: .black.opacity(0.15), radius: ambient.radius, x: 0, y: ambient.y)
    }
}

/// The background of a chip: borderless, raised like Material's elevated chips, and tinted with
/// the accent colour when selected.
struct ChipSurface: View {
    var isSelected = false

    var body: some View {
        Capsule()
            .fill(Color(.secondarySystemBackground))
            .overlay(Capsule().fill(isSelected ? Color.accentColor.opacity(0.18) : .clear))
            .elevation(.chip)
    }
}
