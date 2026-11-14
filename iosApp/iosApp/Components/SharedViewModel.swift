import SwiftUI
@preconcurrency import Shared

/// Keeps a shared Kotlin ViewModel alive for as long as the SwiftUI view that owns it
/// (through `@StateObject`). On deinit it clears the ViewModel, which cancels its
/// `viewModelScope`, the same as leaving a screen on Android.
///
/// UI updates come from `Observing(viewModel.state)` (SKIE), not from this object,
/// so it never needs to publish changes itself.
final class SharedViewModel<VM: AnyObject>: ObservableObject {
    let instance: VM
    private let owner: ViewModelOwner

    init(_ create: (ViewModelOwner) -> VM) {
        let owner = ViewModelOwner()
        self.owner = owner
        self.instance = create(owner)
    }

    deinit {
        owner.clear()
    }
}
