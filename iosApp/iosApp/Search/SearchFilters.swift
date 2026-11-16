import SwiftUI
@preconcurrency import Shared

enum SearchSheet: String, Identifiable {
    case ingredients, nutrition
    var id: String { rawValue }
}

/// The two filter buttons, and below them the active filters as removable chips.
struct SearchFilterBar: View {
    let state: SearchState
    let onIntent: (any SearchIntent) -> Void
    let onOpenSheet: (SearchSheet) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                FilterButton(title: "Ingredients", systemImage: "refrigerator", count: state.ingredients.count) {
                    onOpenSheet(.ingredients)
                }
                FilterButton(title: "Nutrition", systemImage: "flame", count: state.nutrients.activeNutrients.count) {
                    onOpenSheet(.nutrition)
                }
                Spacer()
                if state.hasFilters {
                    Button("Clear all") { onIntent(SearchIntentClearFilters.shared) }.font(.subheadline)
                }
            }
            .padding(.horizontal)

            if state.hasFilters {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(state.ingredients, id: \.self) { ingredient in
                            RemovableChip(label: ingredient) {
                                onIntent(SearchIntentRemoveIngredient(ingredient: ingredient))
                            }
                        }
                        ForEach(state.nutrients.activeNutrients, id: \.self) { nutrient in
                            RemovableChip(label: "\(nutrient.title) \(state.nutrients.range(nutrient: nutrient).text(for: nutrient))") {
                                onIntent(SearchIntentRemoveNutrient(nutrient: nutrient))
                            }
                        }
                    }
                    .padding(.horizontal)
                }
                .scrollClipDisabled()
            }
        }
        .padding(.vertical, 8)
    }
}

private struct FilterButton: View {
    let title: String
    let systemImage: String
    let count: Int
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label(count > 0 ? "\(title) · \(count)" : title, systemImage: systemImage)
                .font(.subheadline.weight(.medium))
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background { ChipSurface(isSelected: count > 0) }
        }
        .buttonStyle(.plain)
    }
}

private struct RemovableChip: View {
    let label: String
    let onRemove: () -> Void

    var body: some View {
        Button(action: onRemove) {
            HStack(spacing: 4) {
                Text(label)
                Image(systemName: "xmark").font(.caption2.weight(.bold))
            }
            .font(.subheadline)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background { ChipSurface(isSelected: true) }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Remove \(label)")
    }
}

/// A tappable chip, such as an ingredient suggestion or a nutrition preset that shows when it is selected.
private struct ChipButton: View {
    let label: String
    var systemImage: String?
    var isSelected = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if let systemImage { Image(systemName: systemImage).font(.caption.weight(.semibold)) }
                Text(label)
            }
            .font(.subheadline)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background { ChipSurface(isSelected: isSelected) }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

// MARK: - Ingredients sheet

struct IngredientsSheet: View {
    let onApply: ([String]) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var draft: [String]
    @State private var input = ""

    init(initial: [String], onApply: @escaping ([String]) -> Void) {
        self.onApply = onApply
        _draft = State(initialValue: initial)
    }

    private var catalog: IngredientCatalog { IngredientCatalog.shared }
    private var maxIngredients: Int { Int(catalog.MAX_INGREDIENTS) }
    private var isFull: Bool { draft.count >= maxIngredients }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack {
                        TextField("Add an ingredient, e.g. chicken", text: $input)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .submitLabel(.done)
                            .onSubmit { add(input) }
                        if !input.trimmingCharacters(in: .whitespaces).isEmpty {
                            Button { add(input) } label: { Image(systemName: "plus.circle.fill") }
                                .accessibilityLabel("Add ingredient")
                        }
                    }
                    .disabled(isFull)
                } footer: {
                    Text("\(draft.count) of \(maxIngredients) ingredients")
                }

                if !draft.isEmpty {
                    Section("Your ingredients") {
                        FlowLayout(spacing: 8) {
                            ForEach(draft, id: \.self) { ingredient in
                                RemovableChip(label: ingredient) { draft.removeAll { $0 == ingredient } }
                            }
                        }
                    }
                }

                if !isFull {
                    Section("Suggestions") {
                        FlowLayout(spacing: 8) {
                            ForEach(catalog.suggestions(input: input, exclude: draft, limit: 8), id: \.self) { suggestion in
                                ChipButton(label: suggestion, systemImage: "plus") { add(suggestion) }
                            }
                        }
                    }
                }

                Section {
                    Button("Reset", role: .destructive) {
                        draft = []
                        input = ""
                    }
                    .disabled(draft.isEmpty && input.isEmpty)
                }
            }
            .navigationTitle("What’s in your kitchen?")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Show recipes") {
                        // Count a typed but not yet added ingredient, rather than silently dropping it.
                        onApply(catalog.add(current: draft, name: input))
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func add(_ name: String) {
        draft = catalog.add(current: draft, name: name)
        input = ""
    }
}

// MARK: - Nutrition sheet

struct NutritionSheet: View {
    let onApply: (NutrientFilter) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var draft: NutrientFilter

    init(initial: NutrientFilter, onApply: @escaping (NutrientFilter) -> Void) {
        self.onApply = onApply
        _draft = State(initialValue: initial)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Quick filters") {
                    FlowLayout(spacing: 8) {
                        ForEach(NutrientPreset.allCases, id: \.self) { preset in
                            let isSelected = draft.hasPreset(preset: preset)
                            ChipButton(label: preset.title, systemImage: isSelected ? "checkmark" : nil, isSelected: isSelected) {
                                draft = isSelected ? draft.removing(preset: preset) : draft.applying(preset: preset)
                            }
                        }
                    }
                }

                ForEach(Nutrient.allCases, id: \.self) { nutrient in
                    Section {
                        boundStepper("At least", nutrient: nutrient, isMin: true)
                        boundStepper("At most", nutrient: nutrient, isMin: false)
                    } header: {
                        HStack {
                            Text(nutrient.title)
                            Spacer()
                            Text(draft.range(nutrient: nutrient).text(for: nutrient))
                        }
                    }
                }

                Section {
                    Button("Reset", role: .destructive) { draft = NutrientFilter.companion.NONE }
                        .disabled(draft.isEmpty)
                }
            }
            .navigationTitle("Nutrition per serving")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Show recipes") {
                        onApply(draft)
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.large])
    }

    /// 0 for the minimum and the nutrient's `filterCeiling` for the maximum mean "no limit".
    private func boundStepper(_ title: String, nutrient: Nutrient, isMin: Bool) -> some View {
        let range = draft.range(nutrient: nutrient)
        let ceiling = Int(nutrient.filterCeiling)
        let step = Int(nutrient.filterStep)
        let current = isMin ? (range.min?.intValue ?? 0) : (range.max?.intValue ?? ceiling)
        let binding = Binding<Int>(
            get: { current },
            set: { newValue in
                var min = range.min?.intValue
                var max = range.max?.intValue
                if isMin {
                    min = newValue > 0 ? newValue : nil
                    if let lower = min, let upper = max, lower > upper { max = lower }
                } else {
                    max = newValue < ceiling ? newValue : nil
                    if let lower = min, let upper = max, upper < lower { min = upper }
                }
                draft = draft.with(nutrient: nutrient, range: NutrientRange(min: min.map { KotlinInt(int: Int32($0)) }, max: max.map { KotlinInt(int: Int32($0)) }))
            }
        )
        let isUnlimited = isMin ? current == 0 : current == ceiling
        return Stepper(value: binding, in: 0...ceiling, step: step) {
            HStack {
                Text(title)
                Spacer()
                Text(isUnlimited ? "Any" : "\(current) \(nutrient.unit)").foregroundStyle(.secondary)
            }
        }
    }
}

// MARK: - Labels

extension Nutrient {
    var title: String {
        switch self {
        case .calories: "Calories"
        case .protein: "Protein"
        case .carbs: "Carbs"
        case .fat: "Fat"
        }
    }
}

extension NutrientPreset {
    var title: String {
        switch self {
        case .highProtein: "High protein"
        case .lowCarb: "Low carb"
        case .under500Kcal: "Under 500 kcal"
        case .lowFat: "Low fat"
        }
    }
}

extension NutrientRange {
    /// "Any", "≥ 30 g", "≤ 500 kcal" or "20–40 g".
    func text(for nutrient: Nutrient) -> String {
        switch (min?.intValue, max?.intValue) {
        case let (min?, max?): "\(min)–\(max) \(nutrient.unit)"
        case let (min?, nil): "≥ \(min) \(nutrient.unit)"
        case let (nil, max?): "≤ \(max) \(nutrient.unit)"
        case (nil, nil): "Any"
        }
    }
}

extension RecipeMatch {
    /// "2 used · 3 missing · 512 kcal", or nil when there is nothing to show.
    var summary: String? {
        var parts: [String] = []
        if let used = usedIngredientCount?.intValue, let missed = missedIngredientCount?.intValue {
            parts.append("\(used) used · \(missed) missing")
        }
        for amount in nutrients {
            let value = Int(amount.amount.rounded())
            parts.append(amount.nutrient == .calories ? "\(value) kcal" : "\(value) g \(amount.nutrient.title.lowercased())")
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
}

// MARK: - Layout

/// Places views left to right, wrapping to a new line when the width runs out.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = rows(for: subviews, width: proposal.width ?? .infinity)
        let height = rows.map(\.height).reduce(0, +) + spacing * CGFloat(max(rows.count - 1, 0))
        let width = rows.map(\.width).max() ?? 0
        return CGSize(width: proposal.width ?? width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in rows(for: subviews, width: bounds.width) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row {
        var indices: [Int] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func rows(for subviews: Subviews, width: CGFloat) -> [Row] {
        var rows: [Row] = [Row()]
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let needed = rows[rows.count - 1].indices.isEmpty ? size.width : rows[rows.count - 1].width + spacing + size.width
            if needed > width, !rows[rows.count - 1].indices.isEmpty {
                rows.append(Row())
            }
            var row = rows[rows.count - 1]
            row.width = row.indices.isEmpty ? size.width : row.width + spacing + size.width
            row.height = max(row.height, size.height)
            row.indices.append(index)
            rows[rows.count - 1] = row
        }
        return rows.filter { !$0.indices.isEmpty }
    }
}
