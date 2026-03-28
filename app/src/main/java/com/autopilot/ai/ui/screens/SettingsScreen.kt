package com.autopilot.ai.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopilot.ai.service.AutoPilotAccessibilityService
import com.autopilot.ai.ui.theme.AgentGreen
import com.autopilot.ai.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activeKeys by viewModel.activeKeys.collectAsState(initial = emptyList())
    val isolatedKeys by viewModel.isolatedKeys.collectAsState(initial = emptyList())
    val isAccessibilityEnabled = AutoPilotAccessibilityService.instance != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Settings", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(16.dp))

        // Accessibility Service status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isAccessibilityEnabled)
                    AgentGreen.copy(alpha = 0.1f)
                else
                    Color.Red.copy(alpha = 0.1f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isAccessibilityEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isAccessibilityEnabled) AgentGreen else Color.Red,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isAccessibilityEnabled) "Accessibility Service: Active"
                        else "Accessibility Service: Not Active",
                        fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Required for screen control, screenshots, and automation.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isAccessibilityEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) {
                        Icon(Icons.Default.Accessibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Accessibility Settings")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // API Key stats
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("API Key Status", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatItem("Active", activeKeys.size.toString(), AgentGreen, Modifier.weight(1f))
                    StatItem("Isolated", isolatedKeys.size.toString(), Color(0xFFFF9800), Modifier.weight(1f))
                    StatItem("Total", (activeKeys.size + isolatedKeys.size).toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("About AutoPilot AI", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                InfoRow("Version", "1.0.0")
                InfoRow("AI Model", "Claude Sonnet 4")
                InfoRow("API Provider", "Bytez")
                InfoRow("Min Android", "11 (API 30)")
                InfoRow("Target", "Android 16 (API 36)")
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = color)
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(label, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
