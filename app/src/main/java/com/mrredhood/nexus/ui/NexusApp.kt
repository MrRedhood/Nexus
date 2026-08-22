package com.mrredhood.nexus.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrredhood.nexus.ui.components.NexusHome
import com.mrredhood.nexus.ui.components.OnboardingNotice
import com.mrredhood.nexus.ui.connectors.ConnectorsScreen
import kotlinx.coroutines.launch

private enum class Destination(val label: String) {
    HOME("Home"), SEARCH("Search"), AUTOMATIONS("Automations"), CONNECTORS("Connectors"), SETTINGS("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusApp() {
    var noticeVisible by remember { mutableStateOf(true) }
    var current by remember { mutableStateOf(Destination.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Nexus", style = MaterialTheme.typography.titleLarge) }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = current == Destination.HOME,
                    onClick = { current = Destination.HOME },
                    icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = current == Destination.SEARCH,
                    onClick = { current = Destination.SEARCH },
                    icon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                    label = { Text("Search") }
                )
                NavigationBarItem(
                    selected = current == Destination.AUTOMATIONS,
                    onClick = { current = Destination.AUTOMATIONS },
                    icon = { Icon(Icons.Rounded.Sync, contentDescription = "Automations") },
                    label = { Text("Automations") }
                )
                NavigationBarItem(
                    selected = current == Destination.CONNECTORS,
                    onClick = { current = Destination.CONNECTORS },
                    icon = { Icon(Icons.Rounded.Extension, contentDescription = "Connectors") },
                    label = { Text("Connectors") }
                )
                NavigationBarItem(
                    selected = current == Destination.SETTINGS,
                    onClick = { current = Destination.SETTINGS },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (current) {
                Destination.HOME -> NexusHome(androidx.compose.foundation.layout.PaddingValues())
                Destination.SEARCH -> PlaceholderPage("Universal search")
                Destination.AUTOMATIONS -> PlaceholderPage("Automations")
                Destination.CONNECTORS -> ConnectorsScreen()
                Destination.SETTINGS -> PlaceholderPage("Settings")
            }

            if (noticeVisible) {
                OnboardingNotice(
                    onDismiss = { noticeVisible = false },
                    onPrivacy = {
                        noticeVisible = false
                        scope.launch { snackbarHostState.showSnackbar("Privacy policy is available from Settings") }
                    }
                )
            }
        }
    }
}

@Composable
private fun PlaceholderPage(title: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            "Foundation screen — capability is added behind this stable shell.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
    }
}
