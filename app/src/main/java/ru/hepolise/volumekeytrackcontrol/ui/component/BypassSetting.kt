package ru.hepolise.volumekeytrackcontrol.ui.component

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
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
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.BYPASS_DURATION

/**
 * How long a button may be held before the module gives it back to the system.
 * Zero keeps the keys swallowed for the whole press.
 */
@Composable
fun BypassSetting(
    bypassDuration: Int,
    sharedPreferences: SharedPreferences,
    onValueChange: (Int) -> Unit
) {
    var showBypassDurationDialog by remember { mutableStateOf(false) }

    PrefsSlider(
        value = bypassDuration,
        onValueChange = { onValueChange(it) },
        valueRange = 0f..5000f,
        prefKey = BYPASS_DURATION,
        sharedPreferences = sharedPreferences
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (bypassDuration == 0) {
                stringResource(R.string.bypass_disabled)
            } else {
                stringResource(R.string.bypass_duration, bypassDuration)
            },
            modifier = Modifier.clickable { showBypassDurationDialog = true }
        )
        IconButton(onClick = { showBypassDurationDialog = true }) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.edit)
            )
        }
    }

    if (showBypassDurationDialog) {
        NumberAlertDialog(
            title = stringResource(R.string.bypass_duration_dialog_title),
            defaultValue = bypassDuration,
            minValue = 0,
            maxValue = 5000,
            onDismissRequest = { showBypassDurationDialog = false },
            onConfirm = {
                onValueChange(it)
                sharedPreferences.edit { putInt(BYPASS_DURATION, it) }
                showBypassDurationDialog = false
            }
        )
    }
}
