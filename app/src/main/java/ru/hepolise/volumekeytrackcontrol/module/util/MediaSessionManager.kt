package ru.hepolise.volumekeytrackcontrol.module.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.view.KeyEvent
import ru.hepolise.volumekeytrackcontrol.util.AppFilterType
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.getAppFilterType
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.getApps

class MediaSessionManager(private val context: Context) {
    lateinit var audioManager: AudioManager
        private set
    private lateinit var mediaSessionManager: MediaSessionManager
    private lateinit var sessionHelper: Any
    private var mediaControllers: List<MediaController>? = null

    init {
        initManagers()
    }

    private fun initManagers() {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mediaSessionManager =
            context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        sessionHelper = getMediaSessionLegacyHelper()
    }

    private fun getMediaSessionLegacyHelper(): Any {
        val helperClass =
            "android.media.session.MediaSessionLegacyHelper".toClass(context.classLoader)
        val method = helperClass.getMethod("getHelper", Context::class.java)
        return method.invoke(null, context)
            ?: throw NullPointerException("Unable to get MediaSessionLegacyHelper")
    }

    @SuppressLint("BlockedPrivateApi")
    fun refreshControllers() {
        val method = MediaSessionManager::class.java.getDeclaredMethod(
            "getActiveSessionsForUser",
            ComponentName::class.java,
            Int::class.javaPrimitiveType
        )

        method.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        mediaControllers = method.invoke(
            mediaSessionManager,
            null,     // ComponentName
            -1              // android.os.UserHandle.ALL
        ) as List<MediaController>
    }

    fun getActiveMediaController(prefs: android.content.SharedPreferences): MediaController? {
        val filterType = prefs.getAppFilterType()
        val apps = prefs.getApps(filterType)
        val chosen = mediaControllers
            ?.sortedByDescending { isMusicActive(it) }
            ?.find { controller ->
                when (filterType) {
                    AppFilterType.DISABLED -> true
                    AppFilterType.WHITE_LIST -> controller.packageName in apps
                    AppFilterType.BLACK_LIST -> controller.packageName !in apps
                }
            }
        return chosen
    }

    fun isMusicActive(controller: MediaController?): Boolean {
        return when (controller?.playbackState?.state) {
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING,
            PlaybackState.STATE_BUFFERING -> true

            else -> false
        }
    }

    fun adjustStreamVolume(keyCode: Int, handler: Handler) {
        try {
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val method = sessionHelper.javaClass.getMethod(
                "sendVolumeKeyEvent",
                KeyEvent::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            method.invoke(sessionHelper, downEvent, AudioManager.STREAM_MUSIC, false)
            handler.postDelayed({
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
                method.invoke(sessionHelper, upEvent, AudioManager.STREAM_MUSIC, false)
            }, 20)
        } catch (e: Exception) {
            val direction = when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
                else -> AudioManager.ADJUST_LOWER
            }
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        }
    }
}