package ru.hepolise.volumekeytrackcontrol.ui.screen

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ru.hepolise.volumekeytrackcontrol.R
import ru.hepolise.volumekeytrackcontrol.ui.component.ActionSelector
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.ACTION_BOTH_BUTTONS
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.ACTION_VOLUME_DOWN
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.ACTION_VOLUME_UP
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.deleteProfile
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.getActionMap
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.setProfileAction

/** Actions for one app, overriding the global configuration while it plays. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppProfileScreen(
    packageName: String,
    sharedPreferences: SharedPreferences,
    navController: NavController?
) {
    val context = LocalContext.current
    val label = remember(packageName) { context.appLabel(packageName) }
    var actionMap by remember { mutableStateOf(sharedPreferences.getActionMap(packageName)) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(label) },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_profile)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            ActionSelector(
                label = stringResource(R.string.action_volume_up),
                value = actionMap.up
            ) { action ->
                actionMap = actionMap.copy(up = action)
                sharedPreferences.setProfileAction(packageName, ACTION_VOLUME_UP, action)
            }

            ActionSelector(
                label = stringResource(R.string.action_volume_down),
                value = actionMap.down
            ) { action ->
                actionMap = actionMap.copy(down = action)
                sharedPreferences.setProfileAction(packageName, ACTION_VOLUME_DOWN, action)
            }

            ActionSelector(
                label = stringResource(R.string.action_both_buttons),
                value = actionMap.both
            ) { action ->
                actionMap = actionMap.copy(both = action)
                sharedPreferences.setProfileAction(packageName, ACTION_BOTH_BUTTONS, action)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_profile)) },
            text = { Text(stringResource(R.string.delete_profile_message, label)) },
            confirmButton = {
                Button(onClick = {
                    showDeleteDialog = false
                    sharedPreferences.deleteProfile(packageName)
                    navController?.popBackStack()
                }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }
}
