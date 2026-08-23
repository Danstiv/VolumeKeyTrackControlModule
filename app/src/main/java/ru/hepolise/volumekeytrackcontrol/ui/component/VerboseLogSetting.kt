package ru.hepolise.volumekeytrackcontrol.ui.component

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import ru.hepolise.volumekeytrackcontrol.R
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.IS_VERBOSE_LOG

@Composable
fun VerboseLogSetting(
    isVerboseLog: Boolean,
    sharedPreferences: SharedPreferences,
    onValueChange: (Boolean) -> Unit
) {
    fun set(value: Boolean) {
        onValueChange(value)
        sharedPreferences.edit { putBoolean(IS_VERBOSE_LOG, value) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = isVerboseLog, onCheckedChange = { set(it) })
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.verbose_log),
            modifier = Modifier.clickable { set(!isVerboseLog) }
        )
    }
}
