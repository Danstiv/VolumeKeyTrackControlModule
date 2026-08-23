package ru.hepolise.volumekeytrackcontrol.module.util

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.session.MediaController
import android.os.Handler
import android.os.SystemClock
import android.view.KeyEvent
import ru.hepolise.volumekeytrackcontrol.module.ExecutionContext
import ru.hepolise.volumekeytrackcontrol.module.MediaEvent
import ru.hepolise.volumekeytrackcontrol.module.fsm.ActionTrigger
import ru.hepolise.volumekeytrackcontrol.module.fsm.Effect
import ru.hepolise.volumekeytrackcontrol.module.fsm.VolumeButton
import ru.hepolise.volumekeytrackcontrol.module.fsm.VolumeKeyStateMachine
import ru.hepolise.volumekeytrackcontrol.util.KeyAction
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.getActionMap
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.getBypassDuration
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.getLongPressDuration
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.isVerboseLog
import ru.hepolise.volumekeytrackcontrol.util.VibratorUtil.getVibrator
import ru.hepolise.volumekeytrackcontrol.util.VibratorUtil.triggerVibration

private const val REPEAT_TIMEOUT_MS = 400L
private const val REPEAT_DELAY_MS = 50L

/** Safety net in case a release is ever lost: ~30 s of repeats, then stop. */
private const val MAX_REPEATS = 600

/** How long a power press is trusted before it is treated as a lost release. */
private const val POWER_HELD_TIMEOUT_MS = 30_000L

class VolumeKeyHandler(
    private val context: Context,
    private val handler: Handler,
    private val mediaSessionManager: MediaSessionManager,
    private val prefs: SharedPreferences,
    private val logger: (String) -> Unit
) {
    private val stateMachine = VolumeKeyStateMachine {
        VolumeKeyStateMachine.Config(
            actionDelayMs = prefs.getLongPressDuration().toLong(),
            bypassDelayMs = prefs.getBypassDuration().toLong()
        )
    }

    /** Media session picked when the gesture started; held for its whole duration. */
    private var gestureController: MediaController? = null

    /** Real presses kept around so injected events can mimic the same device. */
    private val pressedEvents = mutableMapOf<VolumeButton, KeyEvent>()
    private val injectedDownTimes = mutableMapOf<VolumeButton, Long>()
    private val repeatJobs = mutableMapOf<VolumeButton, Runnable>()
    private val repeatCounts = mutableMapOf<VolumeButton, Int>()

    /**
     * When the power key went down, or null while it is up. A volume key pressed
     * while it is held belongs to a chord (screenshot, power menu), not to the
     * module.
     */
    private var powerPressedAt: Long? = null

    /**
     * Should a power release ever be missed, a stale "held" state would silently
     * disable the module, so it is only trusted for a while.
     */
    private fun isPowerHeld(): Boolean {
        val pressedAt = powerPressedAt ?: return false
        if (SystemClock.uptimeMillis() - pressedAt <= POWER_HELD_TIMEOUT_MS) return true
        logger("Power key looks stuck as held, ignoring it")
        powerPressedAt = null
        return false
    }

    /**
     * Key events arrive on the input policy thread while timeouts run on the
     * window manager handler, so every access to the state machine is serialised.
     */
    private val timeoutRunnable = Runnable {
        synchronized(this) {
            val outcome = stateMachine.onTimeout(SystemClock.uptimeMillis())
            applyEffects(outcome.effects)
            rescheduleTimeout()
        }
    }

    private fun verbose(message: String) {
        if (prefs.isVerboseLog()) logger(message)
    }

    /**
     * Returns true when the event must be swallowed. Everything before the state
     * machine is a cheap filter: this runs for every key press in the system, so
     * the expensive session lookup only happens once, when a gesture starts.
     */
    @Synchronized
    fun handleKeyEvent(event: KeyEvent, policyFlags: Int): Boolean {
        val button = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> VolumeButton.UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> VolumeButton.DOWN
            else -> return false
        }

        // Our own injected events: never swallow them again.
        if (policyFlags and InputInjector.POLICY_FLAG_INJECTED != 0) return false
        if (event.flags and KeyEvent.FLAG_FROM_SYSTEM == 0) return false

        val isDown = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return false
        }

        if (!stateMachine.isActive && !armFor(isDown, button)) return false

        if (isDown) pressedEvents[button] = KeyEvent(event)

        val outcome = stateMachine.onKey(button, isDown, SystemClock.uptimeMillis())
        verbose("$button ${if (isDown) "down" else "up"} -> consume=${outcome.consume}, effects=${outcome.effects}")
        applyEffects(outcome.effects)
        rescheduleTimeout()
        if (!stateMachine.isActive) endGesture()
        return outcome.consume
    }

    /**
     * The power key is never consumed; it only tells the module that a chord is
     * being assembled. A chord is paired by the system only when both keys go
     * down within a short window, so a gesture already in flight cannot wait for
     * its bypass deadline and is handed over at once.
     */
    @Synchronized
    fun handlePowerKey(isDown: Boolean) {
        val wasHeld = powerPressedAt != null
        powerPressedAt = if (isDown) powerPressedAt ?: SystemClock.uptimeMillis() else null
        if (!isDown || wasHeld || !stateMachine.isActive) return

        val outcome = stateMachine.bypassNow(SystemClock.uptimeMillis())
        if (outcome.effects.isEmpty()) return

        logger("Power key pressed, handing the volume keys over for the chord")
        applyEffects(outcome.effects)
        rescheduleTimeout()
    }

    /**
     * Decides whether a new gesture may start. Only a press can arm the module —
     * a release without a matching press belongs to the system.
     */
    private fun armFor(isDown: Boolean, button: VolumeButton): Boolean {
        if (!isDown) return false

        // Nothing is bound to this button, so there is no reason to take it:
        // left alone it keeps behaving like an untouched volume key.
        if (prefs.getActionMap().isIdle(button)) {
            verbose("Not arming: nothing is bound to $button")
            return false
        }

        if (isPowerHeld()) {
            verbose("Not arming: power key is held")
            return false
        }

        mediaSessionManager.refreshControllers()

        val audioMode = mediaSessionManager.audioManager.mode
        if (audioMode != AudioManager.MODE_NORMAL) {
            verbose("Not arming: audio mode $audioMode")
            return false
        }

        val controller = mediaSessionManager.getActiveMediaController(prefs)
        if (controller == null) {
            verbose("Not arming: no media session passes the filter")
            return false
        }

        logger("Gesture started, controller: ${controller.packageName}")
        gestureController = controller
        return true
    }

    private fun endGesture() {
        gestureController = null
        pressedEvents.clear()
        injectedDownTimes.clear()
        VolumeButton.entries.forEach(::stopRepeats)
        handler.removeCallbacks(timeoutRunnable)
    }

    private fun rescheduleTimeout() {
        handler.removeCallbacks(timeoutRunnable)
        val nextAt = stateMachine.nextTimeoutAt() ?: return
        val delay = (nextAt - SystemClock.uptimeMillis()).coerceAtLeast(0)
        handler.postDelayed(timeoutRunnable, delay)
    }

    private fun applyEffects(effects: List<Effect>) {
        effects.forEach { effect ->
            when (effect) {
                is Effect.RunAction -> runAction(effect.trigger)
                is Effect.AdjustVolume -> mediaSessionManager.adjustStreamVolume(
                    keyCodeOf(effect.button),
                    handler
                )

                is Effect.InjectDown -> injectDown(effect.button)
                is Effect.InjectUp -> injectUp(effect.button)
            }
        }
    }

    private fun runAction(trigger: ActionTrigger) {
        val controller = gestureController
        if (controller == null) {
            logger("No controller for $trigger, skipping")
            return
        }

        val event = resolveEvent(trigger, controller)
        if (event == null) {
            verbose("No action configured for $trigger")
            return
        }

        context.getVibrator().triggerVibration(prefs)
        logger("Executing ${event::class.simpleName} for $trigger")
        event.execute(
            ExecutionContext(
                controller = controller,
                controls = controller.transportControls,
                prefs = prefs,
                logger = logger
            )
        )
    }

    private fun resolveEvent(trigger: ActionTrigger, controller: MediaController): MediaEvent? {
        val action = prefs.getActionMap().forTrigger(trigger)

        // Skipping or seeking a session that is not playing is not useful, so
        // those actions stay tied to active playback. Play/pause is the point
        // when nothing is playing, so it always applies.
        if (action != KeyAction.PLAY_PAUSE && !mediaSessionManager.isMusicActive(controller)) {
            return null
        }

        return when (action) {
            KeyAction.NONE -> null
            KeyAction.PLAY_PAUSE -> MediaEvent.PlayPause
            KeyAction.NEXT -> MediaEvent.Next
            KeyAction.PREVIOUS -> MediaEvent.Prev
            KeyAction.SEEK_FORWARD -> MediaEvent.FastForward
            KeyAction.SEEK_BACKWARD -> MediaEvent.Rewind
        }
    }

    private fun injectDown(button: VolumeButton) {
        val source = pressedEvents[button] ?: return
        val now = SystemClock.uptimeMillis()
        injectedDownTimes[button] = now
        val event = InputInjector.buildDown(source, now)
        logger("Bypassing $button, handing the press to the system")
        // Never inject from inside the interception path.
        handler.post {
            if (!InputInjector.inject(context, event)) {
                logger("Failed to inject press for $button")
            }
        }
        startRepeats(button, source, now)
    }

    private fun injectUp(button: VolumeButton) {
        val source = pressedEvents[button] ?: return
        stopRepeats(button)
        val downTime = injectedDownTimes.remove(button) ?: return
        val event = InputInjector.buildUp(source, downTime, SystemClock.uptimeMillis())
        verbose("Closing bypassed $button")
        handler.post {
            if (!InputInjector.inject(context, event)) {
                logger("Failed to inject release for $button")
            }
        }
    }

    /**
     * Keeps the handed-over press alive as a stream of repeats, the way the input
     * dispatcher would for a real key. Without it the press reads as a tap: no
     * volume ramping, and no long-press for anything downstream.
     */
    private fun startRepeats(button: VolumeButton, source: KeyEvent, downTime: Long) {
        stopRepeats(button)
        repeatCounts[button] = 0

        val job = object : Runnable {
            override fun run() {
                synchronized(this@VolumeKeyHandler) {
                    // The button was released, or the gesture ended under us.
                    if (repeatJobs[button] !== this) return
                    val count = (repeatCounts[button] ?: return) + 1
                    if (count > MAX_REPEATS) {
                        logger("Repeat limit reached for $button")
                        stopRepeats(button)
                        return
                    }
                    repeatCounts[button] = count

                    val event = InputInjector.buildRepeat(
                        source, downTime, SystemClock.uptimeMillis(), count
                    )
                    InputInjector.inject(context, event)
                    handler.postDelayed(this, REPEAT_DELAY_MS)
                }
            }
        }

        repeatJobs[button] = job
        handler.postDelayed(job, REPEAT_TIMEOUT_MS)
    }

    private fun stopRepeats(button: VolumeButton) {
        repeatJobs.remove(button)?.let { handler.removeCallbacks(it) }
        repeatCounts.remove(button)
    }

    private fun keyCodeOf(button: VolumeButton) = when (button) {
        VolumeButton.UP -> KeyEvent.KEYCODE_VOLUME_UP
        VolumeButton.DOWN -> KeyEvent.KEYCODE_VOLUME_DOWN
    }
}
