package ru.hepolise.volumekeytrackcontrol.util

import ru.hepolise.volumekeytrackcontrol.R
import ru.hepolise.volumekeytrackcontrol.module.fsm.ActionTrigger
import ru.hepolise.volumekeytrackcontrol.module.fsm.VolumeButton

/** What a long press may do. Every slot is configured independently. */
enum class KeyAction(val key: String, val resourceId: Int) {
    NONE("none", R.string.action_none),
    PLAY_PAUSE("play_pause", R.string.action_play_pause),
    NEXT("next", R.string.action_next),
    PREVIOUS("previous", R.string.action_previous),
    SEEK_FORWARD("seek_forward", R.string.action_seek_forward),
    SEEK_BACKWARD("seek_backward", R.string.action_seek_backward);

    val isSeek: Boolean
        get() = this == SEEK_FORWARD || this == SEEK_BACKWARD

    companion object {
        fun fromKey(key: String?, fallback: KeyAction): KeyAction =
            entries.find { it.key == key } ?: fallback
    }
}

/**
 * The three configurable slots. Kept as one value so that per-app profiles can
 * later swap the whole set at once.
 */
data class ActionMap(
    val up: KeyAction,
    val down: KeyAction,
    val both: KeyAction
) {
    fun forButton(button: VolumeButton): KeyAction =
        if (button == VolumeButton.UP) up else down

    fun forTrigger(trigger: ActionTrigger): KeyAction = when (trigger) {
        is ActionTrigger.Single -> forButton(trigger.button)
        ActionTrigger.Both -> both
    }

    /**
     * True when a button has nothing to do at all — neither on its own nor as
     * part of the combo. Such a button is left to the system untouched, so it
     * keeps behaving exactly like an unmodified volume key.
     */
    fun isIdle(button: VolumeButton): Boolean =
        forButton(button) == KeyAction.NONE && both == KeyAction.NONE

    val usesSeekDuration: Boolean
        get() = up.isSeek || down.isSeek || both.isSeek
}
