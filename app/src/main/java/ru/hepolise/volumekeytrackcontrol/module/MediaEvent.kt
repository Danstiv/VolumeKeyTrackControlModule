package ru.hepolise.volumekeytrackcontrol.module

import android.content.SharedPreferences
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.getRewindDuration
import kotlin.math.max
import kotlin.math.min

/**
 * The media actions the module can send. Whether an action is appropriate is
 * decided by the state machine and the handler before it gets here.
 */
sealed class MediaEvent {
    abstract fun execute(context: ExecutionContext)

    /**
     * Sent as a button press rather than through the transport controls: some
     * apps (TeamTalk toggling voice transmission, for one) only handle
     * [MediaController.dispatchMediaButtonEvent] and leave onPlay/onPause empty.
     * Separate play and pause keys, because the default session handler holds
     * back KEYCODE_MEDIA_PLAY_PAUSE waiting for a possible double tap.
     */
    object PlayPause : MediaEvent() {
        override fun execute(context: ExecutionContext) {
            val keyCode = if (context.controller.isMusicActive()) {
                KeyEvent.KEYCODE_MEDIA_PAUSE
            } else {
                KeyEvent.KEYCODE_MEDIA_PLAY
            }
            context.logger("Sending PlayPause as ${KeyEvent.keyCodeToString(keyCode)}")
            val now = SystemClock.uptimeMillis()
            context.controller.dispatchMediaButtonEvent(
                KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
            )
            context.controller.dispatchMediaButtonEvent(
                KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
            )
        }
    }

    object Next : MediaEvent() {
        override fun execute(context: ExecutionContext) {
            context.logger("Sending Next")
            context.controls.skipToNext()
        }
    }

    object Prev : MediaEvent() {
        override fun execute(context: ExecutionContext) {
            context.logger("Sending Prev")
            context.controls.skipToPrevious()
        }
    }

    /** [customStep] seconds, when set, are seeked by even if the app could seek itself. */
    data class FastForward(val customStep: Int?) : MediaEvent() {
        override fun execute(context: ExecutionContext) {
            if (customStep == null && context.supports(PlaybackState.ACTION_FAST_FORWARD)) {
                context.logger("Sending FastForward, the app decides how far")
                context.controls.fastForward()
                return
            }

            val step = customStep ?: context.prefs.getRewindDuration()
            val current = context.currentPosition()
            val duration = context.controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
                ?: Long.MAX_VALUE
            val newPos = min(current + step * 1000L, duration)
            context.seekTo("FastForward", current, newPos)
        }
    }

    /** [customStep] seconds, when set, are seeked by even if the app could seek itself. */
    data class Rewind(val customStep: Int?) : MediaEvent() {
        override fun execute(context: ExecutionContext) {
            if (customStep == null && context.supports(PlaybackState.ACTION_REWIND)) {
                context.logger("Sending Rewind, the app decides how far")
                context.controls.rewind()
                return
            }

            val step = customStep ?: context.prefs.getRewindDuration()
            val current = context.currentPosition()
            val newPos = max(current - step * 1000L, 0L)
            context.seekTo("Rewind", current, newPos)
        }
    }
}

private fun ExecutionContext.seekTo(name: String, current: Long, newPos: Long) {
    if (!supports(PlaybackState.ACTION_SEEK_TO)) {
        logger("$name: the session does not declare ACTION_SEEK_TO, it may ignore the seek")
    }
    logger("Sending $name: $current -> $newPos")
    controls.seekTo(newPos)
}

/**
 * Whether the session handles this action itself.
 *
 * An app that seeks on its own knows exactly where it is, which no amount of
 * bookkeeping on this side can match — so it is asked to seek whenever it says
 * it can, and the position below is only computed for the ones that cannot.
 */
private fun ExecutionContext.supports(action: Long): Boolean =
    (controller.playbackState?.actions ?: 0L) and action != 0L

/**
 * Where playback has actually reached.
 *
 * [PlaybackState.getPosition] is a snapshot taken at
 * [PlaybackState.getLastPositionUpdateTime], not a live value: an app that
 * publishes its state rarely leaves it minutes behind. Seeking relative to the
 * raw snapshot therefore lands somewhere unrelated to what is playing, which is
 * only visible once the snapshot has had time to go stale.
 */
private fun ExecutionContext.currentPosition(): Long {
    val state = controller.playbackState ?: return 0L
    val position = state.position
    if (state.state != PlaybackState.STATE_PLAYING) return position

    val updatedAt = state.lastPositionUpdateTime
    if (updatedAt <= 0L) return position

    val elapsed = SystemClock.elapsedRealtime() - updatedAt
    if (elapsed <= 0L) return position

    val advanced = position + (elapsed * state.playbackSpeed).toLong()
    logger("Position snapshot $position taken ${elapsed}ms ago, extrapolated to $advanced")
    return advanced
}

data class ExecutionContext(
    val controller: MediaController,
    val controls: MediaController.TransportControls,
    val prefs: SharedPreferences,
    val logger: (String) -> Unit
)

fun MediaController.isMusicActive(): Boolean {
    return when (playbackState?.state) {
        android.media.session.PlaybackState.STATE_PLAYING,
        android.media.session.PlaybackState.STATE_FAST_FORWARDING,
        android.media.session.PlaybackState.STATE_REWINDING,
        android.media.session.PlaybackState.STATE_BUFFERING -> true

        else -> false
    }
}
