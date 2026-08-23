package ru.hepolise.volumekeytrackcontrol.module

import android.content.SharedPreferences
import android.media.MediaMetadata
import android.media.session.MediaController
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
            context.logger("Sending FastForward")
            val current = context.controller.playbackState?.position ?: 0L
            val duration = context.controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
                ?: Long.MAX_VALUE
            val newPos = min(current + context.prefs.getRewindDuration() * 1000L, duration)
            context.controls.seekTo(newPos)
        }
    }

    object Rewind : MediaEvent() {
        override fun execute(context: ExecutionContext) {
            context.logger("Sending Rewind")
            val current = context.controller.playbackState?.position ?: 0L
            val newPos = max(current - context.prefs.getRewindDuration() * 1000L, 0L)
            context.controls.seekTo(newPos)
        }
    }
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
