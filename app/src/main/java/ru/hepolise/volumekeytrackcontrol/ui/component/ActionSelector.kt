package ru.hepolise.volumekeytrackcontrol.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import ru.hepolise.volumekeytrackcontrol.util.KeyAction

/**
 * One configurable slot: a row showing the current action, tapping it opens a
 * radio list. A dialog rather than a dropdown so the choice reads as a single
 * labelled group to a screen reader.
 */
@Composable
fun ActionSelector(
    label: String,
    value: KeyAction,
    onValueChange: (KeyAction) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { showDialog = true }
            .padding(vertical = 8.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(value.resourceId),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .selectableGroup()
                ) {
                    KeyAction.entries.forEach { action ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = action == value,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onValueChange(action)
                                        showDialog = false
                                    }
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = action == value, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(action.resourceId))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
