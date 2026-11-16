import SwiftUI
@preconcurrency import Shared

/// Kitchen calculator: unit conversions and ingredient substitutes. Mirrors Android's `CalculatorScreen`.
struct CalculatorView: View {
    @StateObject private var screen = SharedViewModel { $0.calculatorViewModel() }

    private var viewModel: CalculatorViewModel { screen.instance }

    var body: some View {
        Observing(viewModel.state) { state in
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Picker("Tool", selection: Binding(
                        get: { state.tool },
                        set: { send(CalculatorIntentSelectTool(tool: $0)) }
                    )) {
                        Text("Convert").tag(CalculatorTool.convert)
                        Text("Substitute").tag(CalculatorTool.substitute)
                    }
                    .pickerStyle(.segmented)

                    switch state.tool {
                    case .convert: ConvertTool(state: state.conversion, send: send)
                    case .substitute: SubstituteTool(state: state.substitutes, send: send)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .navigationTitle("Kitchen calculator")
    }

    private func send(_ intent: CalculatorIntent) {
        viewModel.onIntent(intent: intent)
    }
}

// MARK: - Convert

private struct ConvertTool: View {
    let state: ConversionState
    let send: (CalculatorIntent) -> Void

    // The fields keep their own text, so typing never waits for a state round trip.
    @State private var amount: String
    @State private var ingredient: String
    @FocusState private var focused: Bool

    init(state: ConversionState, send: @escaping (CalculatorIntent) -> Void) {
        self.state = state
        self.send = send
        _amount = State(initialValue: state.amountText)
        _ingredient = State(initialValue: state.ingredient)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            LabeledField(label: "Amount") {
                TextField("e.g. 2.5 or 1 1/2", text: $amount)
                    // Numbers and punctuation, so fractions such as "1 1/2" can be typed.
                    .keyboardType(.numbersAndPunctuation)
                    .submitLabel(.done)
                    .focused($focused)
                    .onChange(of: amount) { _, text in send(ConversionIntentAmountChanged(text: text)) }
            }

            HStack(spacing: 4) {
                UnitPicker(label: "From", unit: state.from) { send(ConversionIntentFromUnitChanged(unit: $0)) }
                Button {
                    send(ConversionIntentSwapUnits.shared)
                } label: {
                    Image(systemName: "arrow.left.arrow.right")
                        .font(.body.weight(.medium))
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Swap units")
                UnitPicker(label: "To", unit: state.to) { send(ConversionIntentToUnitChanged(unit: $0)) }
            }

            if state.needsIngredient {
                VStack(alignment: .leading, spacing: 8) {
                    LabeledField(label: "Ingredient") {
                        TextField("e.g. flour", text: $ingredient)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .submitLabel(.search)
                            .focused($focused)
                            .onChange(of: ingredient) { _, text in send(ConversionIntentIngredientChanged(text: text)) }
                            .onSubmit(convert)
                    }
                    SuggestionChips(suggestions: state.suggestions) { suggestion in
                        focused = false
                        ingredient = suggestion
                        send(ConversionIntentSelectIngredient(ingredient: suggestion))
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }

            ResultCard { result }
        }
        .animation(.default, value: state.needsIngredient)
    }

    @ViewBuilder
    private var result: some View {
        let name = ingredient.trimmingCharacters(in: .whitespaces)
        switch onEnum(of: state.result) {
        case .ready(let ready):
            Text(ready.unit.amount(ready.amount, isSingular: ready.isSingular))
                .font(.title.weight(.semibold))
                .foregroundStyle(Color.accentColor)
            Caption(ready.basis.map { "Based on 1 cup of \($0.ingredient) ≈ \($0.gramsPerCup) g · saved for offline use" }
                ?? "Standard US kitchen measures · works offline")
        case .invalidAmount:
            Caption("Enter an amount, such as 2.5, 1/2 or 1 1/2.")
        case .needsIngredient:
            Caption("Weight ↔ volume depends on the ingredient: a cup of flour weighs less than a cup of sugar.")
        case .needsLookup:
            Caption("Look up how much a cup of \(name) weighs. This uses 1 Spoonacular point, then works offline.")
            Button("Convert", action: convert).buttonStyle(.bordered)
        case .loading:
            LoadingRow(text: "Looking up \(name)…")
        case .unconvertible(let unconvertible):
            Caption("Spoonacular can’t convert \(unconvertible.ingredient) between weight and volume.")
        case .failed(let failed):
            Caption(failed.error.message)
            Button("Retry", action: convert).buttonStyle(.bordered)
        }
    }

    private func convert() {
        focused = false
        send(ConversionIntentConvert.shared)
    }
}

private struct UnitPicker: View {
    let label: String
    let unit: MeasureUnit
    let onSelect: (MeasureUnit) -> Void

    var body: some View {
        Menu {
            Picker(label, selection: Binding(get: { unit }, set: onSelect)) {
                ForEach(MeasureDimension.allCases, id: \.self) { dimension in
                    Section {
                        ForEach(MeasureUnit.allCases.filter { $0.dimension == dimension }, id: \.self) { option in
                            Text(option.title).tag(option)
                        }
                    }
                }
            }
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(label).font(.caption2).foregroundStyle(.secondary)
                    Text(unit.title).font(.body).foregroundStyle(.primary).lineLimit(1)
                }
                Spacer(minLength: 4)
                Image(systemName: "chevron.up.chevron.down").font(.caption).foregroundStyle(.secondary)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity)
            .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color(.separator)))
            .contentShape(Rectangle())
        }
        .accessibilityLabel("\(label): \(unit.title)")
    }
}

// MARK: - Substitute

private struct SubstituteTool: View {
    let state: SubstitutesState
    let send: (CalculatorIntent) -> Void

    @State private var query: String
    @FocusState private var focused: Bool

    init(state: SubstitutesState, send: @escaping (CalculatorIntent) -> Void) {
        self.state = state
        self.send = send
        _query = State(initialValue: state.query)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField("What are you out of? e.g. butter", text: $query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.search)
                    .focused($focused)
                    .onChange(of: query) { _, text in send(SubstitutesIntentQueryChanged(text: text)) }
                    .onSubmit(find)
                if !query.trimmingCharacters(in: .whitespaces).isEmpty {
                    Button(action: find) {
                        Image(systemName: "arrow.right.circle.fill").font(.title3)
                    }
                    .accessibilityLabel("Find substitutes")
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(Capsule().strokeBorder(Color(.separator)))

            SuggestionChips(suggestions: state.suggestions) { suggestion in
                focused = false
                query = suggestion
                send(SubstitutesIntentSelectIngredient(ingredient: suggestion))
            }

            result
        }
    }

    @ViewBuilder
    private var result: some View {
        switch onEnum(of: state.result) {
        case .idle:
            ResultCard {
                Text("Out of an ingredient?").font(.headline)
                Caption("Find what to use instead. Each new ingredient uses 1 Spoonacular point; answers are saved for offline use.")
            }
        case .loading(let loading):
            ResultCard { LoadingRow(text: "Looking up \(loading.ingredient)…") }
        case .ready(let ready):
            VStack(alignment: .leading, spacing: 8) {
                Text("Instead of \(ready.substitutes.ingredient)").font(.headline)
                ForEach(ready.substitutes.substitutes, id: \.self) { substitute in
                    HStack(spacing: 12) {
                        Image(systemName: "arrow.left.arrow.right").foregroundStyle(Color.accentColor)
                        Text(substitute).font(.subheadline)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background {
                        RoundedRectangle(cornerRadius: 12).fill(.background.secondary).elevation(.card)
                    }
                }
            }
        case .notFound(let notFound):
            ResultCard { Caption("No substitutes found for \(notFound.ingredient).") }
        case .failed(let failed):
            ResultCard {
                Caption(failed.error.message)
                Button("Retry", action: find).buttonStyle(.bordered)
            }
        }
    }

    private func find() {
        focused = false
        send(SubstitutesIntentFind.shared)
    }
}

// MARK: - Shared pieces

private struct LabeledField<Field: View>: View {
    let label: String
    @ViewBuilder let field: Field

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            field
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color(.separator)))
        }
    }
}

private struct SuggestionChips: View {
    let suggestions: [String]
    let onSelect: (String) -> Void

    var body: some View {
        FlowLayout(spacing: 8) {
            ForEach(suggestions, id: \.self) { suggestion in
                ChipButton(label: suggestion) { onSelect(suggestion) }
            }
        }
    }
}

private struct ResultCard<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            content
        }
        .frame(maxWidth: .infinity, minHeight: 96, alignment: .leading)
        .padding(16)
        .background {
            RoundedRectangle(cornerRadius: 12).fill(.background.secondary).elevation(.card)
        }
    }
}

private struct Caption: View {
    let text: String

    init(_ text: String) {
        self.text = text
    }

    var body: some View {
        Text(text).font(.subheadline).foregroundStyle(.secondary)
    }
}

private struct LoadingRow: View {
    let text: String

    var body: some View {
        HStack(spacing: 12) {
            ProgressView()
            Caption(text)
        }
    }
}

// MARK: - Labels

extension MeasureUnit {
    /// Same wording as Android's `unit_*` strings.
    var title: String {
        switch self {
        case .grams: "Grams"
        case .kilograms: "Kilograms"
        case .ounces: "Ounces"
        case .pounds: "Pounds"
        case .teaspoons: "Teaspoons"
        case .tablespoons: "Tablespoons"
        case .cups: "Cups"
        case .fluidOunces: "Fluid ounces"
        case .milliliters: "Milliliters"
        case .liters: "Liters"
        }
    }

    /// "313 grams", "1 cup": same wording as Android's `amount_*` plurals.
    func amount(_ amount: String, isSingular: Bool) -> String {
        let name: String = switch self {
        case .grams: isSingular ? "gram" : "grams"
        case .kilograms: isSingular ? "kilogram" : "kilograms"
        case .ounces: isSingular ? "ounce" : "ounces"
        case .pounds: isSingular ? "pound" : "pounds"
        case .teaspoons: isSingular ? "teaspoon" : "teaspoons"
        case .tablespoons: isSingular ? "tablespoon" : "tablespoons"
        case .cups: isSingular ? "cup" : "cups"
        case .fluidOunces: isSingular ? "fluid ounce" : "fluid ounces"
        case .milliliters: isSingular ? "milliliter" : "milliliters"
        case .liters: isSingular ? "liter" : "liters"
        }
        return "\(amount) \(name)"
    }
}
