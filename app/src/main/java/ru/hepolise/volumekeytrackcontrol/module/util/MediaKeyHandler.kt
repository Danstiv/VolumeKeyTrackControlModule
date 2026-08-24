package ru.hepolise.volumekeytrackcontrol.module.util

import android.content.SharedPreferences
import android.view.KeyEvent
import ru.hepolise.volumekeytrackcontrol.util.KeyAction
import ru.hepolise.volumekeytrackcontrol.util.MediaKeyMap
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.getMediaKeyMap
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.isVerboseLog

/**
 * Remaps the media buttons of a Bluetooth headset before the app sees them.
 *
 * The event is not swallowed and replayed — its key code is swapped and it
 * continues on its way, so the app acts once, on the button it was told about.
 * A headset pressing "previous" on a video with no playlist can that way scrub
 * backwards instead of doing nothing.
 */
class MediaKeyHandler(
    private val prefs: SharedPreferences,
    private val logger: (String) -> Unit
) {
    /**
     * The event to dispatch instead, or null to leave the original alone.
     *
     * [callerPackage] is who asked for the key to be dispatched, which is how a
     * headset is told apart from an on-screen control: only the Bluetooth stack
     * is remapped, since the buttons in a notification already say what they do.
     */
    fun remap(targetPackage: String?, callerPackage: String?, event: KeyEvent): KeyEvent? {
        if (targetPackage == null) return null
        if (callerPackage == null || !isBluetooth(callerPackage)) return null

        val key = MediaKeyMap.keyOf(event.keyCode) ?: return null
        val action = prefs.getMediaKeyMap(targetPackage).forKey(key)
        if (action == KeyAction.NONE) return null

        val keyCode = action.mediaKeyCode ?: return null
        if (keyCode == event.keyCode) return null

        if (prefs.isVerboseLog()) {
            logger("Remapping $key from $callerPackage for $targetPackage to $action")
        }

        return KeyEvent(
            event.downTime, event.eventTime, event.action, keyCode, event.repeatCount,
            event.metaState, event.deviceId, event.scanCode, event.flags, event.source
        )
    }

    // Matched loosely: the stack is com.android.bluetooth on most builds but not
    // on all of them.
    private fun isBluetooth(packageName: String) = packageName.contains("bluetooth", true)
}
