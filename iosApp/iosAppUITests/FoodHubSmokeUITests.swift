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

        let recipeCard = app.scrollViews.buttons.matching(NSPredicate(format: "label CONTAINS 'min'")).firstMatch
        XCTAssertTrue(waitForAny([recipeCard, app.staticTexts[keyError]], timeout: 20), "Expected recipe cards or the API key error")

        // With recipes on screen, the wine pairing for the centred one appears below the carousel.
        if recipeCard.exists {
            XCTAssertTrue(app.staticTexts["Pairs well with"].waitForExistence(timeout: 5), "Expected the wine pairing section")
        }
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

        XCTAssertEqual(searchField.value as? String, "pasta", "The search field dropped keystrokes")

        // Look up the grid by identifier: `scrollViews.firstMatch` can be the keyboard's suggestion bar.
        let firstResult = app.scrollViews["searchResults"].buttons.firstMatch
        XCTAssertTrue(waitForAny([firstResult, app.staticTexts[keyError]], timeout: 20), "Expected search results or the API key error")
    }

    @MainActor
    func testBottomBarSwitchesToFavorites() {
        let app = XCUIApplication()
        app.launch()

        app.buttons["Favorites"].tap()

        XCTAssertTrue(app.navigationBars["Favorites"].waitForExistence(timeout: 10))
    }

    /// Same-dimension conversions are worked out on the device, so this needs no API key or network.
    @MainActor
    func testKitchenCalculatorConvertsOfflineAndOpensSubstitutes() {
        let app = XCUIApplication()
        app.launch()

        app.buttons["Kitchen"].tap()
        XCTAssertTrue(app.navigationBars["Kitchen calculator"].waitForExistence(timeout: 10))

        // 1 cup → teaspoons.
        app.buttons["To: Grams"].tap()
        app.buttons["Teaspoons"].tap()
        XCTAssertTrue(app.staticTexts["48 teaspoons"].waitForExistence(timeout: 5))
        attachScreenshot(app, name: "Kitchen calculator: convert")

        app.buttons["Substitute"].tap()
        XCTAssertTrue(app.staticTexts["Out of an ingredient?"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["butter"].exists, "Expected the suggestion chips")
        attachScreenshot(app, name: "Kitchen calculator: substitute")
    }

    @MainActor
    private func attachScreenshot(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    /// Waits until any of `elements` exists. Unlike chaining `waitForExistence` calls, this returns
    /// as soon as one outcome appears instead of sitting through the timeout of the one that never comes.
    @MainActor
    private func waitForAny(_ elements: [XCUIElement], timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            if elements.contains(where: { $0.exists }) { return true }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        } while Date() < deadline
        return false
    }
}
