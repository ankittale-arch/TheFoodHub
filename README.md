# The FoodHub

A recipe app built on the [Spoonacular API](https://spoonacular.com/food-api), using Kotlin Multiplatform for shared logic and native UI on each platform: Jetpack Compose on Android, SwiftUI on iOS.

## Setup

1. Get a free API key at <https://spoonacular.com/food-api/console#Profile>.
2. Add it to `local.properties` in the project root. This file is git-ignored:
   ```properties
   spoonacular.apiKey=YOUR_KEY
   ```
   On CI, set the `SPOONACULAR_API_KEY` environment variable instead. Gradle generates the key into
   the shared module (`:shared:generateSecrets`), so **both apps use it with no per-platform setup**.
3. **Android:** run the `app` configuration in Android Studio.
4. **iOS:** open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` scheme. A build phase
   compiles `:shared` with Gradle. Xcode does not inherit your shell's `JAVA_HOME`, so the script uses
   Android Studio's bundled JDK. For a device build, set your team under Signing & Capabilities.

The free plan allows **150 points/day**. A search costs about 1.2 points and opening a recipe costs 1 point. Recipe details are cached in Room, so opening the same recipe again is free. If the key is missing or the quota is used up, the app says so instead of crashing.

## Stack

| Concern | Choice |
|---|---|
| DI | Koin 4.2 (`koin-core-viewmodel` in shared, `koin-androidx-compose` on Android) |
| Networking | Ktor 3.6 (OkHttp engine on Android, Darwin on iOS) + kotlinx.serialization |
| Database | Room 3.0 (`androidx.room3`, the multiplatform-first successor to Room 2.x) with bundled SQLite |
| State | MVI on `StateFlow`, using **androidx `ViewModel` in shared code** (multiplatform since lifecycle 2.8) |
| iOS bridge | SKIE 0.10.16 (Flows → Swift `AsyncSequence`, sealed classes → Swift enums, SwiftUI `Observing`) |
| Android UI | Compose Material 3, type-safe Navigation Compose, Coil 3 |
| iOS UI | SwiftUI (iOS 17+, Swift 6 language mode), `Observing` from SKIE |

**Version coupling:**
- SKIE supports only specific Kotlin versions. Kotlin `2.4.21` needs SKIE `≥ 0.10.16`. Check the [SKIE changelog](https://skie.touchlab.co/changelog) before upgrading Kotlin.
- AGP stays at 9.3.x because that is the newest version Android Studio 2026.1 supports. `:shared` uses AGP 9's `com.android.kotlin.multiplatform.library` plugin.
- KSP 2.3.x is versioned independently of Kotlin.

## Modules

```
app/      Android app: Compose screens, navigation, Koin startup (FoodHubApplication)
iosApp/   iOS app: SwiftUI screens, Xcode project, UI smoke tests (iosAppUITests)
shared/   KMP library (Android + iosArm64 + iosSimulatorArm64) → Shared.framework
  commonMain/
    core/               AppConfig, DataError, Outcome, MviViewModel base class
    domain/             models + repository interfaces
    data/remote/        Ktor client, SpoonacularApi, DTOs, error mapping   (internal)
    data/local/         Room database, entities, DAOs
    data/repository/    repository implementations                          (internal)
    feature/<screen>/   <Screen>Contract.kt (State/Intent/Effect) + <Screen>ViewModel.kt
    di/                 Koin modules, initKoin()
  androidMain/          OkHttp engine, Room builder (needs Context)
  iosMain/              Darwin engine, Room builder, FoodHub.start(), ViewModelOwner
  iosTest/              real-SQLite Room tests, ViewModelOwner tests
```

On iOS, each screen holds its shared ViewModel in a `SharedViewModel` wrapper (`@StateObject`). When the
view goes away, the wrapper's `deinit` clears the ViewModel, which cancels its `viewModelScope` just as
leaving a screen does on Android.

### MVI contract

Each screen exposes one `state: StateFlow<State>`, takes user actions through `onIntent(Intent)`, and sends one-off events (snackbars) on `effects: Flow<Effect>`. Platform UIs only render state and send intents. Business logic such as debouncing, pagination, caching, and favorites lives in shared code and is unit-tested once for both platforms.

### Data flow

- **Home** shows random popular recipes (`GET recipes/random`) in a stacked carousel, with four chips:
  - **All**: 10 random recipes.
  - **Popular**: the same 10, sorted by likes, with no extra call.
  - **Quick & Easy**: 40 random recipes, keeping those ready in 30 minutes or less, because the endpoint can't filter by time.
  - **Diets**: a menu of tags (vegetarian, vegan, gluten free, ketogenic, paleo).

  Each source is fetched once per app session, so switching chips back costs no quota. The returned recipes are also cached in Room, so opening a card is free.
- **Search** is network-only. It waits 500 ms after typing stops, a new query cancels the request in flight, and it loads 20 results per page up to Spoonacular's offset limit of 900.
- **Recipe detail** is cache-first. The screen reads from Room, and the network is called only when the recipe isn't cached yet.
- **Favorites** are stored in Room with their own title and image, so a recipe can be saved straight from search results.

## Tests

```bash
./gradlew :shared:testAndroidHostTest      # shared tests on the JVM (fast)
./gradlew :shared:iosSimulatorArm64Test    # same tests + real-SQLite Room tests on the iOS simulator
./gradlew :app:connectedDebugAndroidTest   # Compose UI tests (needs a device/emulator)
cd iosApp && xcodebuild test -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro'   # SwiftUI end-to-end smoke tests
```

The iOS search smoke test calls the real Spoonacular API. Without a key it expects the "key missing" error, which still exercises the full path.

Shared tests cover the ViewModels (debounce, cancellation, pagination, error/retry, effects), the repositories (cache-skip, forced refresh, mapping), and the API layer against Ktor's `MockEngine` (API key sent as a header, status → `DataError` mapping, malformed JSON, offline).
