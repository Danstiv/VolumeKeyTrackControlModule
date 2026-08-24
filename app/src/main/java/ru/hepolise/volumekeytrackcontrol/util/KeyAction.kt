package ru.hepolise.volumekeytrackcontrol.util

import android.view.KeyEvent
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

    /**
     * The media key that means this action. Remapping a headset button is done
     * by swapping the key code and letting the app act on it, the way it would
     * for a button that really sent it.
     */
    val mediaKeyCode: Int?
        get() = when (this) {
            NONE -> null
            PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            SEEK_FORWARD -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            SEEK_BACKWARD -> KeyEvent.KEYCODE_MEDIA_REWIND
        }

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

/** Which headset button a media key event belongs to. */
enum class MediaKey { NEXT, PREVIOUS, PLAY_PAUSE }

/**
 * What the headset buttons do. Unset means the module does not interfere and the
 * button reaches the app unchanged.
 */
data class MediaKeyMap(
    val next: KeyAction,
    val previous: KeyAction,
    val playPause: KeyAction
) {
    fun forKey(key: MediaKey): KeyAction = when (key) {
        MediaKey.NEXT -> next
        MediaKey.PREVIOUS -> previous
        MediaKey.PLAY_PAUSE -> playPause
    }

    companion object {
        fun keyOf(keyCode: Int): MediaKey? = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> MediaKey.NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> MediaKey.PREVIOUS
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> MediaKey.PLAY_PAUSE

            else -> null
        }
    }
}
