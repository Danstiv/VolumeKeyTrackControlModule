package ru.hepolise.volumekeytrackcontrol.ui.screen

import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import ru.hepolise.volumekeytrackcontrol.R
import ru.hepolise.volumekeytrackcontrol.ui.model.AppInfo
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.getActionMap
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.getProfileApps
import ru.hepolise.volumekeytrackcontrol.viewmodel.AppIconViewModel

/** Apps whose long-press actions differ from the global configuration. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppProfilesScreen(
    sharedPreferences: SharedPreferences,
    navController: NavController?,
    iconViewModel: AppIconViewModel = viewModel()
) {
    val context = LocalContext.current
    val iconMap by iconViewModel.iconMap.collectAsState()
    var profiles by remember { mutableStateOf(emptyList<AppInfo>()) }

    // Re-read on every entry: a profile may have just been added or deleted.
    LaunchedEffect(navController?.currentBackStackEntry) {
        profiles = sharedPreferences.getProfileApps()
            .map { AppInfo(name = context.appLabel(it), packageName = it) }
            .sortedBy { it.name.lowercase() }
        profiles.forEach { iconViewModel.loadIcon(it.packageName) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_profiles)) },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController?.navigate("appProfilePicker") }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_app))
            }
        }
    ) { padding ->
        if (profiles.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.app_profiles_empty),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(profiles, key = { it.packageName }) { app ->
                val actionMap = sharedPreferences.getActionMap(app.packageName)
                // Spelled out rather than three bare action names, so the row
                // says which slot is which when read aloud.
                val summary = stringResource(R.string.action_volume_up) + " " +
                    stringResource(actionMap.up.resourceId) + "  " +
                    stringResource(R.string.action_volume_down) + " " +
                    stringResource(actionMap.down.resourceId) + "  " +
                    stringResource(R.string.action_both_buttons) + " " +
                    stringResource(actionMap.both.resourceId)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController?.navigate("appProfile/${app.packageName}") }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    AppIcon(
                        bitmap = iconMap[app.packageName],
                        contentDescription = stringResource(R.string.app_icon)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = app.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

internal fun android.content.Context.appLabel(packageName: String): String = try {
    packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
} catch (_: PackageManager.NameNotFoundException) {
    packageName
}
