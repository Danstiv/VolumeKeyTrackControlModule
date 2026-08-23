package ru.hepolise.volumekeytrackcontrol.util

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Build
import android.view.ViewConfiguration
import io.github.libxposed.service.XposedService

object SharedPreferencesUtil {
    const val SETTINGS_PREFS = "settings_prefs"
    const val STATUS_PREFS = "status_prefs"

    const val EFFECT = "selectedEffect"
    const val VIBRATION_LENGTH = "vibrationLength"
    const val VIBRATION_AMPLITUDE = "vibrationAmplitude"
    const val LONG_PRESS_DURATION = "longPressDuration"
    const val ACTION_VOLUME_UP = "actionVolumeUp"
    const val ACTION_VOLUME_DOWN = "actionVolumeDown"
    const val ACTION_BOTH_BUTTONS = "actionBothButtons"
    const val REWIND_DURATION = "rewindDuration"
    const val BYPASS_DURATION = "bypassDuration"
    const val IS_VERBOSE_LOG = "isVerboseLog"
    const val APP_FILTER_TYPE = "appFilterType"
    const val WHITE_LIST_APPS = "whiteListApps"
    const val BLACK_LIST_APPS = "blackListApps"

    const val LAUNCHED_COUNT = "launchedCount"

    val EFFECT_DEFAULT_VALUE =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationType.Click.key else VibrationType.Manual.key
    const val VIBRATION_LENGTH_DEFAULT_VALUE = 50
    const val VIBRATION_AMPLITUDE_DEFAULT_VALUE = 128
    val LONG_PRESS_DURATION_DEFAULT_VALUE = ViewConfiguration.getLongPressTimeout()
    val ACTION_VOLUME_UP_DEFAULT_VALUE = KeyAction.NEXT
    val ACTION_VOLUME_DOWN_DEFAULT_VALUE = KeyAction.PREVIOUS
    val ACTION_BOTH_BUTTONS_DEFAULT_VALUE = KeyAction.PLAY_PAUSE
    const val REWIND_DURATION_DEFAULT_VALUE = 5
    // Kept below two seconds on purpose: the window manager's own volume chord
    // needs three more seconds after the module hands the keys over, and it has
    // to win against screen readers that watch the volume keys themselves and
    // act at around five seconds. Handing over at one second leaves a margin.
    const val BYPASS_DURATION_DEFAULT_VALUE = 1000
    const val IS_VERBOSE_LOG_DEFAULT_VALUE = false
    val APP_FILTER_TYPE_DEFAULT_VALUE = AppFilterType.DISABLED.key

    const val LAUNCHED_COUNT_DEFAULT_VALUE = -1

    fun SharedPreferences?.getVibrationType(): VibrationType {
        val defaultValue = EFFECT_DEFAULT_VALUE
        return VibrationType.fromKey(this?.getString(EFFECT, defaultValue) ?: defaultValue)
    }

    fun SharedPreferences?.getVibrationLength(): Int {
        val defaultValue = VIBRATION_LENGTH_DEFAULT_VALUE
        return this?.getInt(VIBRATION_LENGTH, defaultValue) ?: defaultValue
    }

    fun SharedPreferences?.getVibrationAmplitude(): Int {
        val defaultValue = VIBRATION_AMPLITUDE_DEFAULT_VALUE
        return this?.getInt(VIBRATION_AMPLITUDE, defaultValue) ?: defaultValue
    }

    fun SharedPreferences?.getLongPressDuration(): Int {
        val defaultValue = LONG_PRESS_DURATION_DEFAULT_VALUE
        return this?.getInt(LONG_PRESS_DURATION, defaultValue) ?: defaultValue
    }

    fun SharedPreferences?.getAction(key: String, defaultValue: KeyAction): KeyAction =
        KeyAction.fromKey(this?.getString(key, defaultValue.key), defaultValue)

    fun SharedPreferences?.getActionMap(): ActionMap = ActionMap(
        up = getAction(ACTION_VOLUME_UP, ACTION_VOLUME_UP_DEFAULT_VALUE),
        down = getAction(ACTION_VOLUME_DOWN, ACTION_VOLUME_DOWN_DEFAULT_VALUE),
        both = getAction(ACTION_BOTH_BUTTONS, ACTION_BOTH_BUTTONS_DEFAULT_VALUE)
    )

    fun SharedPreferences?.getRewindDuration(): Int {
        val defaultValue = REWIND_DURATION_DEFAULT_VALUE
        return this?.getInt(REWIND_DURATION, defaultValue) ?: defaultValue
    }

    /** Milliseconds a button is held before the gesture is handed to the system. */
    fun SharedPreferences?.getBypassDuration(): Int {
        val defaultValue = BYPASS_DURATION_DEFAULT_VALUE
        return this?.getInt(BYPASS_DURATION, defaultValue) ?: defaultValue
    }

    fun SharedPreferences?.isVerboseLog(): Boolean {
        val defaultValue = IS_VERBOSE_LOG_DEFAULT_VALUE
        return this?.getBoolean(IS_VERBOSE_LOG, defaultValue) ?: defaultValue
    }

    fun SharedPreferences?.getAppFilterType(): AppFilterType {
        val defaultValue = APP_FILTER_TYPE_DEFAULT_VALUE
        return AppFilterType.fromKey(this?.getString(APP_FILTER_TYPE, defaultValue) ?: defaultValue)
    }

    fun SharedPreferences?.getApps(appFilterType: AppFilterType = getAppFilterType()): Set<String> {
        return when (appFilterType) {
            AppFilterType.DISABLED -> emptySet()
            AppFilterType.WHITE_LIST -> this?.getStringSet(WHITE_LIST_APPS, emptySet())
                ?: emptySet()

            AppFilterType.BLACK_LIST -> this?.getStringSet(BLACK_LIST_APPS, emptySet())
                ?: emptySet()
        }
    }

    fun SharedPreferences.getLaunchedCount(): Int =
        this.getInt(LAUNCHED_COUNT, LAUNCHED_COUNT_DEFAULT_VALUE)

    fun XposedService.getSettingsSharedPreferences(): SharedPreferences =
        getRemotePreferences(SETTINGS_PREFS)

    fun Context.getStatusSharedPreferences(): SharedPreferences =
        getSharedPreferences(STATUS_PREFS, MODE_PRIVATE)

}