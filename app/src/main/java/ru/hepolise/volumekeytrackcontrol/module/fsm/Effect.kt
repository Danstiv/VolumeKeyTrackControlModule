package ru.hepolise.volumekeytrackcontrol.module.fsm

enum class VolumeButton { UP, DOWN }

sealed interface ActionTrigger {
    data class Single(val button: VolumeButton) : ActionTrigger
    data object Both : ActionTrigger
}

/**
 * What the state machine asks the host to do. The state machine itself performs
 * no side effects, which is what keeps it testable.
 */
sealed interface Effect {
    /** Run the configured media action for this trigger (and vibrate). */
    data class RunAction(val trigger: ActionTrigger) : Effect

    /** Short press: hand the volume change to the system. */
    data class AdjustVolume(val button: VolumeButton) : Effect

    /** Bypass: give the button back to the system as a synthetic press. */
    data class InjectDown(val button: VolumeButton) : Effect

    /** Close a previously injected press. */
    data class InjectUp(val button: VolumeButton) : Effect
}
