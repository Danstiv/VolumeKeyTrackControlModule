package ru.hepolise.volumekeytrackcontrol.module

import android.content.SharedPreferences
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.getRewindDuration
import kotlin.math.max
import kotlin.math.min

/**
 * The media actions the module can send. Whether an action is appropriate is
 * decided by the state machine and the handler before it gets here.
 */
sealed class MediaEvent {
    abstract fun execute(context: ExecutionContext)

    object PlayPause : MediaEvent() {
        override fun execute(context: ExecutionContext) {
            context.logger("Sending PlayPause")
            if (context.controller.isMusicActive()) {
                context.controls.pause()
            } else {
                context.controls.play()
            }
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

    object FastForward : MediaEvent() {
        override fun execute(context: ExecutionContext) {
            val current = context.currentPosition()
            val duration = context.controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
                ?: Long.MAX_VALUE
            val newPos = min(current + context.prefs.getRewindDuration() * 1000L, duration)
            context.logger("Sending FastForward: $current -> $newPos")
            context.controls.seekTo(newPos)
        }
    }

    object Rewind : MediaEvent() {
        override fun execute(context: ExecutionContext) {
            val current = context.currentPosition()
            val newPos = max(current - context.prefs.getRewindDuration() * 1000L, 0L)
            context.logger("Sending Rewind: $current -> $newPos")
            context.controls.seekTo(newPos)
        }
    }
}

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
