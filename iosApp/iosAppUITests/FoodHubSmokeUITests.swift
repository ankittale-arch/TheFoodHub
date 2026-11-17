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

    /// Favorites a search result (needs the API key), then creates a cookbook, files the recipe in it, and deletes the cookbook.
    @MainActor
    func testCookbooksOrganizeFavorites() throws {
        let app = XCUIApplication()
        app.launch()

        app.buttons["Search"].tap()
        let searchField = app.searchFields.firstMatch
        XCTAssertTrue(searchField.waitForExistence(timeout: 5))
        searchField.tap()
        searchField.typeText("brownies\n")

        let addFavorite = app.scrollViews["searchResults"].buttons["Add to favorites"].firstMatch
        let removeFavorite = app.scrollViews["searchResults"].buttons["Remove from favorites"].firstMatch
        XCTAssertTrue(waitForAny([addFavorite, removeFavorite, app.staticTexts[keyError]], timeout: 20))
        if app.staticTexts[keyError].exists { throw XCTSkip("Needs a Spoonacular API key to save a favorite") }
        if addFavorite.exists { addFavorite.tap() }

        app.buttons["Favorites"].tap()
        // A unique name, so runs on the same simulator don't clash with an earlier cookbook.
        let name = "Desserts \(Int(Date().timeIntervalSince1970) % 100_000)"

        app.buttons["New cookbook"].tap()
        let nameField = app.textFields["Name"]
        XCTAssertTrue(nameField.waitForExistence(timeout: 5))
        nameField.typeText(name)
        app.buttons["Create"].tap()

        // The new cookbook opens straight away, empty.
        XCTAssertTrue(app.buttons["\(name) · 0"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Nothing in \(name) yet"].exists)

        app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'All · '")).firstMatch.tap()
        app.buttons["Add to cookbook"].firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Save to cookbook"].waitForExistence(timeout: 5))
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", name)).firstMatch.tap()
        attachScreenshot(app, name: "Cookbooks: picker")
        app.buttons["Done"].tap()

        let filledChip = app.buttons["\(name) · 1"]
        XCTAssertTrue(filledChip.waitForExistence(timeout: 5))
        filledChip.tap()
        XCTAssertFalse(app.staticTexts["Nothing in \(name) yet"].exists)
        attachScreenshot(app, name: "Cookbooks: filtered")

        app.buttons["Cookbook options"].tap()
        app.buttons["Delete cookbook"].tap()
        app.alerts.buttons["Delete"].tap()
        XCTAssertTrue(filledChip.waitForNonExistence(timeout: 5), "The deleted cookbook's chip should go away")
    }

    /// Saves a recipe (needs the API key), then writes a note and an ingredient tweak on it and removes both.
    @MainActor
    func testRecipeNotesAndIngredientTweaks() throws {
        let app = XCUIApplication()
        app.launch()

        app.buttons["Search"].tap()
        let searchField = app.searchFields.firstMatch
        XCTAssertTrue(searchField.waitForExistence(timeout: 5))
        searchField.tap()
        searchField.typeText("brownies\n")
        let addFavorite = app.scrollViews["searchResults"].buttons["Add to favorites"].firstMatch
        let removeFavorite = app.scrollViews["searchResults"].buttons["Remove from favorites"].firstMatch
        XCTAssertTrue(waitForAny([addFavorite, removeFavorite, app.staticTexts[keyError]], timeout: 20))
        if app.staticTexts[keyError].exists { throw XCTSkip("Needs a Spoonacular API key to save a favorite") }
        if addFavorite.exists { addFavorite.tap() }

        app.buttons["Favorites"].tap()
        app.buttons["favoriteRecipe"].firstMatch.tap()

        let notesCard = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'My notes'")).firstMatch
        XCTAssertTrue(notesCard.waitForExistence(timeout: 20), "A saved recipe shows the notes card")
        // A failed earlier run may have left a note on this simulator.
        if notesCard.label.contains("Edit note") {
            notesCard.tap()
            app.buttons["Remove"].tap()
        }

        notesCard.tap()
        XCTAssertTrue(app.navigationBars["Your note"].waitForExistence(timeout: 5))
        app.typeText("Used half the sugar, baked 5 mins less")
        app.navigationBars["Your note"].buttons["Save"].tap()
        XCTAssertTrue(app.staticTexts["Used half the sugar, baked 5 mins less"].waitForExistence(timeout: 5))

        let ingredient = app.buttons["ingredient"].firstMatch
        ingredient.swipeUpUntilHittable(in: app)
        ingredient.tap()
        XCTAssertTrue(app.navigationBars["Ingredient tweak"].waitForExistence(timeout: 5))
        if app.buttons["Remove"].exists {
            app.buttons["Remove"].tap()
            ingredient.tap()
        }
        app.typeText("Used half")
        app.navigationBars["Ingredient tweak"].buttons["Save"].tap()
        XCTAssertTrue(app.staticTexts["Used half"].waitForExistence(timeout: 5))
        attachScreenshot(app, name: "Recipe notes: tweak")

        // Clean up: Remove clears both.
        ingredient.tap()
        app.buttons["Remove"].tap()
        XCTAssertTrue(app.staticTexts["Used half"].waitForNonExistence(timeout: 5))
        app.swipeDown()
        notesCard.tap()
        app.buttons["Remove"].tap()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS 'Add a note'")).firstMatch.waitForExistence(timeout: 5))
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

private extension XCUIElement {
    /// Scrolls the app up until this element can be tapped (it may start below the fold).
    @MainActor
    func swipeUpUntilHittable(in app: XCUIApplication, maxSwipes: Int = 6) {
        var swipes = 0
        while !(exists && isHittable) && swipes < maxSwipes {
            app.swipeUp()
            swipes += 1
        }
    }
}
