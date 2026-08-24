package ru.hepolise.volumekeytrackcontrol.ui.component

import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import ru.hepolise.volumekeytrackcontrol.R
import ru.hepolise.volumekeytrackcontrol.util.ActionMap
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.ACTION_BOTH_BUTTONS
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.ACTION_VOLUME_DOWN
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.ACTION_VOLUME_UP
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.REWIND_DURATION

data class ActionSettingData(
    val actionMap: ActionMap,
    val rewindDuration: Int
)

@Composable
fun LongPressActionSetting(
    data: ActionSettingData,
    sharedPreferences: SharedPreferences,
    onValueChange: (ActionSettingData) -> Unit
) {
    val actionMap = data.actionMap
    val rewindDuration = data.rewindDuration

    var showRewindDurationDialog by remember { mutableStateOf(false) }

    ActionSelector(
        label = stringResource(R.string.action_volume_up),
        value = actionMap.up
    ) { action ->
        onValueChange(data.copy(actionMap = actionMap.copy(up = action)))
        sharedPreferences.edit { putString(ACTION_VOLUME_UP, action.key) }
    }

    ActionSelector(
        label = stringResource(R.string.action_volume_down),
        value = actionMap.down
    ) { action ->
        onValueChange(data.copy(actionMap = actionMap.copy(down = action)))
        sharedPreferences.edit { putString(ACTION_VOLUME_DOWN, action.key) }
    }

    ActionSelector(
        label = stringResource(R.string.action_both_buttons),
        value = actionMap.both
    ) { action ->
        onValueChange(data.copy(actionMap = actionMap.copy(both = action)))
        sharedPreferences.edit { putString(ACTION_BOTH_BUTTONS, action.key) }
    }

    Box {
        AnimatedVisibility(
            visible = actionMap.usesSeekDuration,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PrefsSlider(
                    value = rewindDuration,
                    onValueChange = {
                        onValueChange(data.copy(rewindDuration = it))
                    },
                    valueRange = 1f..60f,
                    prefKey = REWIND_DURATION,
                    sharedPreferences = sharedPreferences
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.rewind_duration, rewindDuration),
                        modifier = Modifier.clickable { showRewindDurationDialog = true }
                    )
                    IconButton(
                        onClick = {
                            showRewindDurationDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit_rewind_duration)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.rewind_duration_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showRewindDurationDialog) {
        NumberAlertDialog(
            title = stringResource(R.string.rewind_duration_dialog_title),
            defaultValue = rewindDuration,
            minValue = 1,
            maxValue = 60,
            onDismissRequest = { showRewindDurationDialog = false },
            onConfirm = {
                onValueChange(data.copy(rewindDuration = it))
                sharedPreferences.edit { putInt(REWIND_DURATION, it) }
                showRewindDurationDialog = false
            }
        )
    }
}
