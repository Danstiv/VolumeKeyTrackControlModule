package ru.hepolise.volumekeytrackcontrol.module.fsm

/**
 * Timing and ownership logic for the volume keys, free of any Android types so
 * that it can be driven by a virtual clock in unit tests.
 *
 * A gesture starts when the module swallows a key press and ends when every
 * physical button is up again. While a gesture is running the module owns the
 * buttons it swallowed; each owned button has two deadlines:
 *
 *  * `actionDelayMs` — the configured action fires, still while the button is held.
 *    Two buttons pressed within that window form a combo instead, and the combo
 *    deadline is measured from the second press.
 *  * `bypassDelayMs` — the module gives the button back to the system: a synthetic
 *    press is injected, and the real release is answered with a matching synthetic
 *    release. This is what keeps key combinations working — chords handled by the
 *    window manager policy, and apps that read the volume keys themselves.
 *    (Accessibility services are *not* among them: they receive key events
 *    upstream of the policy and see the keys whatever the module does.)
 *
 * Deadlines are per button, so keys pressed 40 ms apart are handed back 40 ms
 * apart and system chords still assemble. Anything that removes a button from
 * the module — a release or a bypass — cancels the whole gesture's pending
 * actions, mirroring the "release aborts everything" behaviour of the original
 * implementation.
 *
 * A chord that cannot wait for the deadline — the power key going down — hands
 * the buttons over at once through [bypassNow].
 */
class VolumeKeyStateMachine(private val config: () -> Config) {

    data class Config(
        val actionDelayMs: Long,
        /** Zero disables the bypass: buttons then stay swallowed until released. */
        val bypassDelayMs: Long
    )

    data class Outcome(
        /** True when the event must not reach the rest of the system. */
        val consume: Boolean,
        val effects: List<Effect> = emptyList()
    )

    private class Held(
        val pressedAt: Long,
        var owned: Boolean = true,
        var actionFired: Boolean = false,
        var actionCancelled: Boolean = false
    )

    private sealed interface Timer {
        data object Combo : Timer
        data class Action(val button: VolumeButton) : Timer
        data class Bypass(val button: VolumeButton) : Timer
    }

    private val held = LinkedHashMap<VolumeButton, Held>()
    private val physicallyDown = LinkedHashSet<VolumeButton>()
    private var comboAt: Long? = null
    private var gestureGivenAway = false

    /**
     * True while a gesture is in flight. The caller must keep feeding events in
     * that case even if the module would no longer arm itself, otherwise a
     * release could escape without its press.
     */
    val isActive: Boolean
        get() = physicallyDown.isNotEmpty() || held.isNotEmpty()

    fun onKey(button: VolumeButton, isDown: Boolean, now: Long): Outcome =
        if (isDown) onDown(button, now) else onUp(button, now)

    private fun onDown(button: VolumeButton, now: Long): Outcome {
        val alreadyDown = !physicallyDown.add(button)
        // Auto-repeat of a key we already track changes nothing.
        if (alreadyDown) return Outcome(consume = held[button] != null)

        if (gestureGivenAway) return Outcome(consume = false)

        val other = held.entries.firstOrNull { (key, state) ->
            key != button && state.owned && !state.actionFired && !state.actionCancelled
        }
        held[button] = Held(pressedAt = now)
        if (other != null) {
            // Combo: both single-button actions give way to one combined action,
            // timed from this second press.
            comboAt = now + config().actionDelayMs
        }
        return Outcome(consume = true)
    }

    private fun onUp(button: VolumeButton, now: Long): Outcome {
        physicallyDown.remove(button)
        val state = held.remove(button)

        val outcome = when {
            // Never ours — a press that arrived after the gesture was given away.
            state == null -> Outcome(consume = false)

            // Handed to the system earlier; answer the real release with a
            // synthetic one so the injected press is properly closed.
            !state.owned -> Outcome(consume = true, effects = listOf(Effect.InjectUp(button)))

            else -> {
                val effects = mutableListOf<Effect>()
                if (!state.actionFired) effects += Effect.AdjustVolume(button)
                cancelPendingActions()
                Outcome(consume = true, effects = effects)
            }
        }

        if (physicallyDown.isEmpty()) reset()
        return outcome
    }

    fun onTimeout(now: Long): Outcome {
        val effects = mutableListOf<Effect>()
        while (true) {
            val (dueAt, timer) = pendingTimers().firstOrNull() ?: break
            if (dueAt > now) break
            when (timer) {
                Timer.Combo -> {
                    comboAt = null
                    held.values.forEach { it.actionFired = true }
                    effects += Effect.RunAction(ActionTrigger.Both)
                }

                is Timer.Action -> {
                    held[timer.button]?.actionFired = true
                    effects += Effect.RunAction(ActionTrigger.Single(timer.button))
                }

                is Timer.Bypass -> {
                    held[timer.button]?.owned = false
                    gestureGivenAway = true
                    cancelPendingActions()
                    effects += Effect.InjectDown(timer.button)
                }
            }
        }
        return Outcome(consume = false, effects = effects)
    }

    /**
     * Hands every button the module still owns back to the system immediately,
     * without waiting for its bypass deadline. Used when another key joins the
     * gesture and the combination would otherwise be missed: the system pairs a
     * chord only when both keys go down within a short window of each other.
     */
    fun bypassNow(now: Long): Outcome {
        val effects = held.entries
            .filter { (_, state) -> state.owned }
            .map { (button, state) ->
                state.owned = false
                Effect.InjectDown(button)
            }
        if (effects.isEmpty()) return Outcome(consume = false)

        gestureGivenAway = true
        cancelPendingActions()
        return Outcome(consume = false, effects = effects)
    }

    /** Absolute time of the next deadline, or null when nothing is pending. */
    fun nextTimeoutAt(): Long? = pendingTimers().firstOrNull()?.first

    private fun pendingTimers(): List<Pair<Long, Timer>> {
        val config = config()
        val timers = mutableListOf<Pair<Long, Timer>>()
        comboAt?.let { timers += it to Timer.Combo }
        held.forEach { (button, state) ->
            if (!state.owned) return@forEach
            if (comboAt == null && !state.actionFired && !state.actionCancelled) {
                timers += (state.pressedAt + config.actionDelayMs) to Timer.Action(button)
            }
            if (config.bypassDelayMs > 0) {
                timers += (state.pressedAt + config.bypassDelayMs) to Timer.Bypass(button)
            }
        }
        return timers.sortedBy { it.first }
    }

    private fun cancelPendingActions() {
        comboAt = null
        held.values.forEach { if (!it.actionFired) it.actionCancelled = true }
    }

    private fun reset() {
        held.clear()
        physicallyDown.clear()
        comboAt = null
        gestureGivenAway = false
    }

    override fun toString(): String =
        "VolumeKeyStateMachine(held=${held.keys}, down=$physicallyDown, " +
            "comboAt=$comboAt, givenAway=$gestureGivenAway)"
}
