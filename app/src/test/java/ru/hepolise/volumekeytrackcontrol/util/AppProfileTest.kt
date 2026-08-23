package ru.hepolise.volumekeytrackcontrol.util

import androidx.core.content.edit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.ACTION_BOTH_BUTTONS
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.ACTION_VOLUME_DOWN
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.ACTION_VOLUME_UP
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.createProfile
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.deleteProfile
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.getActionMap
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.getProfileApps
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.hasProfile
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.setProfileAction

private const val SPOTIFY = "com.spotify.music"
private const val PODCASTS = "com.podcasts.app"

class AppProfileTest {

    private val prefs = FakeSharedPreferences()

    private fun setGlobal(up: KeyAction, down: KeyAction, both: KeyAction) {
        prefs.edit {
            putString(ACTION_VOLUME_UP, up.key)
            putString(ACTION_VOLUME_DOWN, down.key)
            putString(ACTION_BOTH_BUTTONS, both.key)
        }
    }

    @Test
    fun `an app without a profile gets the global actions`() {
        setGlobal(KeyAction.NEXT, KeyAction.PREVIOUS, KeyAction.PLAY_PAUSE)

        assertFalse(prefs.hasProfile(SPOTIFY))
        assertEquals(
            ActionMap(KeyAction.NEXT, KeyAction.PREVIOUS, KeyAction.PLAY_PAUSE),
            prefs.getActionMap(SPOTIFY)
        )
    }

    @Test
    fun `a new profile starts as a copy of the global actions`() {
        setGlobal(KeyAction.NEXT, KeyAction.PREVIOUS, KeyAction.PLAY_PAUSE)
        prefs.createProfile(SPOTIFY)

        assertTrue(prefs.hasProfile(SPOTIFY))
        assertEquals(prefs.getActionMap(), prefs.getActionMap(SPOTIFY))
    }

    @Test
    fun `profiles override the global actions independently of each other`() {
        setGlobal(KeyAction.NEXT, KeyAction.PREVIOUS, KeyAction.PLAY_PAUSE)
        prefs.createProfile(SPOTIFY)
        prefs.createProfile(PODCASTS)

        prefs.setProfileAction(PODCASTS, ACTION_VOLUME_UP, KeyAction.SEEK_FORWARD)
        prefs.setProfileAction(PODCASTS, ACTION_VOLUME_DOWN, KeyAction.SEEK_BACKWARD)

        assertEquals(
            ActionMap(KeyAction.SEEK_FORWARD, KeyAction.SEEK_BACKWARD, KeyAction.PLAY_PAUSE),
            prefs.getActionMap(PODCASTS)
        )
        assertEquals(
            ActionMap(KeyAction.NEXT, KeyAction.PREVIOUS, KeyAction.PLAY_PAUSE),
            prefs.getActionMap(SPOTIFY)
        )
        assertEquals(
            ActionMap(KeyAction.NEXT, KeyAction.PREVIOUS, KeyAction.PLAY_PAUSE),
            prefs.getActionMap()
        )
    }

    @Test
    fun `a slot a profile never stored still follows the global actions`() {
        setGlobal(KeyAction.NEXT, KeyAction.PREVIOUS, KeyAction.PLAY_PAUSE)
        // Marked as having a profile, but with nothing written for it.
        prefs.edit { putStringSet(SharedPreferencesUtil.PROFILE_APPS, setOf(SPOTIFY)) }

        assertEquals(prefs.getActionMap(), prefs.getActionMap(SPOTIFY))

        prefs.setProfileAction(SPOTIFY, ACTION_VOLUME_UP, KeyAction.NONE)
        setGlobal(KeyAction.SEEK_FORWARD, KeyAction.SEEK_BACKWARD, KeyAction.NEXT)

        // Only the stored slot is pinned; the rest keep tracking the global map.
        assertEquals(
            ActionMap(KeyAction.NONE, KeyAction.SEEK_BACKWARD, KeyAction.NEXT),
            prefs.getActionMap(SPOTIFY)
        )
    }

    @Test
    fun `deleting a profile falls back to the global actions and leaves others alone`() {
        setGlobal(KeyAction.NEXT, KeyAction.PREVIOUS, KeyAction.PLAY_PAUSE)
        prefs.createProfile(SPOTIFY)
        prefs.createProfile(PODCASTS)
        prefs.setProfileAction(SPOTIFY, ACTION_VOLUME_UP, KeyAction.NONE)
        prefs.setProfileAction(PODCASTS, ACTION_VOLUME_UP, KeyAction.SEEK_FORWARD)

        prefs.deleteProfile(SPOTIFY)

        assertFalse(prefs.hasProfile(SPOTIFY))
        assertEquals(setOf(PODCASTS), prefs.getProfileApps())
        assertEquals(prefs.getActionMap(), prefs.getActionMap(SPOTIFY))
        assertEquals(KeyAction.SEEK_FORWARD, prefs.getActionMap(PODCASTS).up)

        // Re-creating must not resurrect the deleted override.
        prefs.createProfile(SPOTIFY)
        assertEquals(KeyAction.NEXT, prefs.getActionMap(SPOTIFY).up)
    }
}
