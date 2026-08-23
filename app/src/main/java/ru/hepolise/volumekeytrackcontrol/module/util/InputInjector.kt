package ru.hepolise.volumekeytrackcontrol.module.util

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputEvent
import android.view.KeyEvent
import java.lang.reflect.Method

/**
 * Feeds synthetic key events back into the input pipeline when a gesture is
 * handed over to the system.
 *
 * Unlike [MediaSessionManager.adjustStreamVolume], which only asks the media
 * stack to change the volume, an injected event travels the normal path and is
 * therefore visible to the window manager's own key chords and to apps that read
 * the volume keys themselves.
 *
 * Injected events come back through `interceptKeyBeforeQueueing` carrying
 * [POLICY_FLAG_INJECTED], which is how the hook recognises its own echo.
 */
object InputInjector {

    /** `WindowManagerPolicyConstants.FLAG_INJECTED`. */
    const val POLICY_FLAG_INJECTED = 0x01000000

    private const val INJECT_INPUT_EVENT_MODE_ASYNC = 0

    private class Injector(val target: Any, val method: Method)

    private var injector: Injector? = null
    private var resolved = false

    /**
     * `InputManager.injectInputEvent` is hidden and was moved to
     * `InputManagerGlobal` in newer releases, so both are tried.
     */
    private fun resolveInjector(context: Context): Injector? {
        if (resolved) return injector
        resolved = true

        injector = runCatching {
            val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
            Injector(inputManager, injectMethodOf(inputManager.javaClass))
        }.recoverCatching {
            val globalClass = Class.forName("android.hardware.input.InputManagerGlobal")
            val instance = globalClass.getMethod("getInstance").invoke(null)!!
            Injector(instance, injectMethodOf(globalClass))
        }.getOrNull()

        return injector
    }

    private fun injectMethodOf(clazz: Class<*>): Method = clazz
        .getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
        .apply { isAccessible = true }

    /**
     * Builds a press that looks like it came from the same physical device as
     * [source], but with a fresh `downTime` so the system sees a clean press.
     */
    fun buildDown(source: KeyEvent, now: Long): KeyEvent = KeyEvent(
        now, now, KeyEvent.ACTION_DOWN, source.keyCode, 0, source.metaState,
        source.deviceId, source.scanCode, source.flags, source.source
    )

    /**
     * Builds an auto-repeat of a press injected at [downTime].
     *
     * A held key normally produces a stream of repeats synthesised by the input
     * dispatcher, and "long press" downstream means nothing more than the first
     * of those repeats, marked with [KeyEvent.FLAG_LONG_PRESS]. Injected events
     * get no such treatment, so the module produces the stream itself —
     * otherwise a handed-over press reads as an instant tap to everything below,
     * and neither volume ramping nor long-press detection works.
     */
    fun buildRepeat(source: KeyEvent, downTime: Long, now: Long, repeatCount: Int): KeyEvent {
        val flags = if (repeatCount == 1) {
            source.flags or KeyEvent.FLAG_LONG_PRESS
        } else {
            source.flags
        }
        return KeyEvent(
            downTime, now, KeyEvent.ACTION_DOWN, source.keyCode, repeatCount, source.metaState,
            source.deviceId, source.scanCode, flags, source.source
        )
    }

    /** Builds the release matching a press injected at [downTime]. */
    fun buildUp(source: KeyEvent, downTime: Long, now: Long): KeyEvent = KeyEvent(
        downTime, now, KeyEvent.ACTION_UP, source.keyCode, 0, source.metaState,
        source.deviceId, source.scanCode, source.flags, source.source
    )

    /** Returns true when the event was handed to the input manager. */
    fun inject(context: Context, event: KeyEvent): Boolean {
        val injector = resolveInjector(context) ?: return false
        return runCatching {
            injector.method.invoke(injector.target, event, INJECT_INPUT_EVENT_MODE_ASYNC)
            true
        }.getOrDefault(false)
    }
}
