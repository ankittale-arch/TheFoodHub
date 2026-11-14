@preconcurrency import Shared

extension DataError {
    /// Same wording as the Android app's strings.xml.
    var message: String {
        switch self {
        case .network: "Can’t reach Spoonacular. Check your connection."
        case .unauthorized: "The Spoonacular API key is missing or invalid. Add spoonacular.apiKey to local.properties."
        case .quotaExceeded: "The daily Spoonacular quota is used up. Try again tomorrow."
        case .notFound: "This recipe could not be found."
        case .server: "Spoonacular is having problems. Try again later."
        case .serialization, .unknown: "Something went wrong."
        }
    }
}
