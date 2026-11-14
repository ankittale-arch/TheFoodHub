import SwiftUI

/// Colours of the home design (sampled from the mockup); same values as Android's HomePalette.
/// The design is light-only, so these do not change with the system appearance.
enum HomePalette {
    static let backgroundTop = Color(hex: 0xDDE3E8)
    static let backgroundBottom = Color(hex: 0xB9CBD8)
    static let ink = Color(hex: 0x111111)
    static let inkMuted = Color(hex: 0x6C7075)
    static let field = Color(hex: 0xEDF0F3)
    static let fieldButton = Color(hex: 0xDCE2E8)
    static let chip = Color(hex: 0xDCE3E9)
    static let olive = Color(hex: 0x6E8360)
    static let pill = Color(hex: 0xF2F3F4)
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}
