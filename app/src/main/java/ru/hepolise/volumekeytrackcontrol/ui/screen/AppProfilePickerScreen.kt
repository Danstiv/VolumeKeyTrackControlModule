package ru.hepolise.volumekeytrackcontrol.ui.screen

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.createProfile
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.getProfileApps
import ru.hepolise.volumekeytrackcontrol.viewmodel.AppFilterViewModel
import ru.hepolise.volumekeytrackcontrol.viewmodel.AppIconViewModel

/**
 * Picks the app a new profile is for. Apps that already have one are left out —
 * they are edited from the list instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppProfilePickerScreen(
    sharedPreferences: SharedPreferences,
    navController: NavController?,
    viewModel: AppFilterViewModel = viewModel(),
    iconViewModel: AppIconViewModel = viewModel()
) {
    val context = LocalContext.current
    val apps by viewModel.apps.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val iconMap by iconViewModel.iconMap.collectAsState()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadApps(context) }

    val existing = sharedPreferences.getProfileApps()
    val visible = apps
        .filter { it.packageName !in existing }
        .filter {
            query.isBlank() ||
                it.name.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
        .sortedBy { it.name.lowercase() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_app)) },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
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
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_apps)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                return@Column
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visible, key = { it.packageName }) { app ->
                    LaunchedEffect(app.packageName) { iconViewModel.loadIcon(app.packageName) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sharedPreferences.createProfile(app.packageName)
                                navController?.popBackStack()
                                navController?.navigate("appProfile/${app.packageName}")
                            }
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
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
