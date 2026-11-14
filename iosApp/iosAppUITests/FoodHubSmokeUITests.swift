import XCTest

/// End-to-end smoke tests: SwiftUI → shared Kotlin ViewModels (SKIE) → Ktor/Room on iOS.
/// These call the real Spoonacular API. Without a key they expect the "key missing" error,
/// which still proves the whole request path works.
final class FoodHubSmokeUITests: XCTestCase {

    private let keyError = "The Spoonacular API key is missing or invalid. Add spoonacular.apiKey to local.properties."

    override func setUp() {
        continueAfterFailure = false
    }

    @MainActor
    func testHomeLoadsRandomRecipesOrShowsTheOutcome() {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.staticTexts["Most popular recipes"].waitForExistence(timeout: 10))
        for chip in ["All", "Popular", "Quick & Easy", "Diets"] {
            XCTAssertTrue(app.buttons[chip].exists, "missing chip \(chip)")
        }

        let sawOutcome = app.staticTexts[keyError].waitForExistence(timeout: 20)
            || app.scrollViews.buttons.matching(NSPredicate(format: "label CONTAINS 'min'")).firstMatch.exists
        XCTAssertTrue(sawOutcome, "Expected recipe cards or the API key error")
    }

    @MainActor
    func testHomeSearchBarOpensSearchAndSearchReachesSpoonacular() {
        let app = XCUIApplication()
        app.launch()

        let homeSearch = app.buttons["Let’s cook something…"]
        XCTAssertTrue(homeSearch.waitForExistence(timeout: 10))
        homeSearch.tap()

        let searchField = app.searchFields.firstMatch
        XCTAssertTrue(searchField.waitForExistence(timeout: 5))
        searchField.typeText("pasta")

        // Only look inside the results grid; the keyboard's suggestion bar can also say "pasta".
        let firstResult = app.scrollViews.firstMatch.buttons.firstMatch
        let sawOutcome = app.staticTexts[keyError].waitForExistence(timeout: 20) || firstResult.waitForExistence(timeout: 1)
        XCTAssertTrue(sawOutcome, "Expected search results or the API key error")
    }

    @MainActor
    func testBottomBarSwitchesToFavorites() {
        let app = XCUIApplication()
        app.launch()

        app.buttons["Favorites"].tap()

        XCTAssertTrue(app.navigationBars["Favorites"].waitForExistence(timeout: 10))
    }
}
