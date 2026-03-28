package com.autopilot.ai.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.autopilot.ai.viewmodel.MainViewModel

data class NavItem(val label: String, val icon: ImageVector)

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val navItems = listOf(
        NavItem("Chat", Icons.Default.Chat),
        NavItem("API Keys", Icons.Default.Key),
        NavItem("Settings", Icons.Default.Settings)
    )
    var selectedIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index }
                    )
                }
            }
        }
    ) { padding ->
        when (selectedIndex) {
            0 -> ChatScreen(viewModel = viewModel)
            1 -> ApiKeyScreen(viewModel = viewModel)
            2 -> SettingsScreen(viewModel = viewModel)
        }
    }
}
