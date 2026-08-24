package ru.hepolise.volumekeytrackcontrol.ui.component

import android.content.SharedPreferences
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import ru.hepolise.volumekeytrackcontrol.R
import ru.hepolise.volumekeytrackcontrol.util.KeyAction
import ru.hepolise.volumekeytrackcontrol.util.MediaKeyMap
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.ACTION_MEDIA_NEXT
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.ACTION_MEDIA_PLAY_PAUSE
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.ACTION_MEDIA_PREVIOUS

/**
 * The three headset buttons. [onStore] takes the preference key so the same
 * component serves the global settings and a per-app profile.
 */
@Composable
fun MediaKeySetting(
    mediaKeyMap: MediaKeyMap,
    onValueChange: (MediaKeyMap) -> Unit,
    onStore: (String, KeyAction) -> Unit
) {
    Text(
        text = stringResource(R.string.media_keys_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    ActionSelector(
        label = stringResource(R.string.action_media_next),
        value = mediaKeyMap.next
    ) { action ->
        onValueChange(mediaKeyMap.copy(next = action))
        onStore(ACTION_MEDIA_NEXT, action)
    }

    ActionSelector(
        label = stringResource(R.string.action_media_previous),
        value = mediaKeyMap.previous
    ) { action ->
        onValueChange(mediaKeyMap.copy(previous = action))
        onStore(ACTION_MEDIA_PREVIOUS, action)
    }

    ActionSelector(
        label = stringResource(R.string.action_media_play_pause),
        value = mediaKeyMap.playPause
    ) { action ->
        onValueChange(mediaKeyMap.copy(playPause = action))
        onStore(ACTION_MEDIA_PLAY_PAUSE, action)
    }
}

/** Stores into the global settings rather than a profile. */
fun SharedPreferences.storeGlobalAction(key: String, action: KeyAction) {
    edit { putString(key, action.key) }
}
