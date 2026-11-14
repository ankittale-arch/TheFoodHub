package com.ankitt.thefoodhub.core.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Base class for MVI screens shared by Android and iOS.
 *
 * - [state]: a single immutable snapshot of what the screen shows. SKIE exposes it to Swift
 *   as an `AsyncSequence` (or use `Observing` in SwiftUI).
 * - [onIntent]: the only entry point for user actions.
 * - [effects]: one-off events (snackbars, for example) that must not be replayed on
 *   configuration change. Each effect is delivered to a single collector.
 */
abstract class MviViewModel<State : Any, Intent : Any, Effect : Any>(
    initialState: State,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    protected val currentState: State
        get() = _state.value

    abstract fun onIntent(intent: Intent)

    protected fun updateState(reduce: State.() -> State) {
        _state.update(reduce)
    }

    protected fun emitEffect(effect: Effect) {
        _effects.trySend(effect)
    }
}
