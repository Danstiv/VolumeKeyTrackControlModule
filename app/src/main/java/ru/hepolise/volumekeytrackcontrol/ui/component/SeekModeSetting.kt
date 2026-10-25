package ru.hepolise.volumekeytrackcontrol.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.hepolise.volumekeytrackcontrol.R

/** How a per-app profile seeks: the app's own way, or always by [step] seconds. */
@Composable
fun SeekModeSetting(
    custom: Boolean,
    step: Int,
    globalStep: Int,
    onCustomChange: (Boolean) -> Unit,
    onStepChange: (Int) -> Unit
) {
    var showStepDialog by remember { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.seek_mode),
        style = MaterialTheme.typography.bodyLarge
    )

    Column(modifier = Modifier.selectableGroup()) {
        SeekModeOption(
            text = stringResource(R.string.seek_mode_default),
            selected = !custom,
            onClick = { onCustomChange(false) }
        )
        SeekModeOption(
            text = stringResource(R.string.seek_mode_custom),
            selected = custom,
            onClick = { onCustomChange(true) }
        )
    }

    if (custom) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.seek_step, step))
            IconButton(onClick = { showStepDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit_seek_step)
                )
            }
        }
    }

    Text(
        text = if (custom) {
            stringResource(R.string.seek_mode_custom_hint)
        } else {
            stringResource(R.string.seek_mode_default_hint, globalStep)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (showStepDialog) {
        NumberAlertDialog(
            title = stringResource(R.string.edit_seek_step),
            defaultValue = step,
            minValue = 1,
            maxValue = 600,
            onDismissRequest = { showStepDialog = false },
            onConfirm = {
                onStepChange(it)
                showStepDialog = false
            }
        )
    }
}

@Composable
private fun SeekModeOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}
