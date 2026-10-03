package ai.eqo.helper.client

import rikka.shizuku.Shizuku

/**
 * EQO-authored (TASK-007, issue #12).
 *
 * EQO-side activation state of the privileged helper connection: tracks whether the
 * helper binder is alive, and produces the re-activation prompt when the binder dies
 * (acceptance criterion: "binder death leads to a clean state and a re-activation prompt").
 *
 * This is pure state tracking over the forked client's binder callbacks; it holds no
 * secrets and issues no requests.
 */
class HelperActivationState(
    private val onStateChanged: (State) -> Unit = {},
) {
    enum class State { INACTIVE, ACTIVE, NEEDS_REACTIVATION }

    @Volatile
    var state: State = State.INACTIVE
        private set

    /** Shown to the owner after binder death; non-empty by design (device-asserted). */
    val reActivationPrompt: String =
        "The EQO helper connection was lost. Re-activate the helper to continue."

    private val receivedListener =
        object : Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() = transition(State.ACTIVE)
        }

    private val deadListener =
        object : Shizuku.OnBinderDeadListener {
            override fun onBinderDead() = transition(State.NEEDS_REACTIVATION)
        }

    fun attach() {
        Shizuku.addBinderReceivedListenerSticky(receivedListener)
        Shizuku.addBinderDeadListener(deadListener)
    }

    fun detach() {
        Shizuku.removeBinderReceivedListener(receivedListener)
        Shizuku.removeBinderDeadListener(deadListener)
    }

    private fun transition(next: State) {
        if (state == next) return
        state = next
        onStateChanged(next)
    }
}
