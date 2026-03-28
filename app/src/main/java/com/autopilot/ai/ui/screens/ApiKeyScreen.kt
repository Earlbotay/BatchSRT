package com.autopilot.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopilot.ai.data.db.ApiKeyEntity
import com.autopilot.ai.ui.theme.AgentGreen
import com.autopilot.ai.ui.theme.AgentOrange
import com.autopilot.ai.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ApiKeyScreen(viewModel: MainViewModel) {
    val activeKeys by viewModel.activeKeys.collectAsState(initial = emptyList())
    val isolatedKeys by viewModel.isolatedKeys.collectAsState(initial = emptyList())
    var newKeysText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        // Add keys section
        Text("Add API Keys", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Paste Bytez API keys (one per line). Duplicates ignored.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = newKeysText,
            onValueChange = { newKeysText = it },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            placeholder = { Text("Paste API keys here...\nOne key per line") },
            shape = RoundedCornerShape(12.dp),
            maxLines = 10
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val lineCount = newKeysText.lines().count { it.trim().isNotEmpty() }
            Text("$lineCount key(s) detected", fontSize = 12.sp, color = Color.Gray)

            Button(
                onClick = {
                    viewModel.addApiKeys(newKeysText)
                    newKeysText = ""
                },
                enabled = newKeysText.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Keys")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Key lists
        LazyColumn(modifier = Modifier.weight(1f)) {
            // Active keys
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = AgentGreen, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Active Keys (${activeKeys.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (activeKeys.isEmpty()) {
                item {
                    Text("No active keys. Add some above!", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(start = 28.dp))
                }
            }

            items(activeKeys) { key ->
                ApiKeyCard(key = key, isIsolated = false, onDelete = { viewModel.deleteApiKey(key.id) })
            }

            // Isolated keys
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = AgentOrange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Isolated Keys (${isolatedKeys.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    if (isolatedKeys.isNotEmpty()) {
                        ElevatedButton(onClick = { viewModel.restoreMonthlyKeys() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Check Restore", fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (isolatedKeys.isEmpty()) {
                item {
                    Text("No isolated keys.", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(start = 28.dp))
                }
            }

            items(isolatedKeys) { key ->
                ApiKeyCard(key = key, isIsolated = true, onDelete = { viewModel.deleteApiKey(key.id) })
            }
        }
    }
}

@Composable
fun ApiKeyCard(key: ApiKeyEntity, isIsolated: Boolean, onDelete: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isIsolated) AgentOrange.copy(alpha = 0.1f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = key.keyValue.take(8) + "..." + key.keyValue.takeLast(4),
                    fontWeight = FontWeight.Medium, fontSize = 14.sp
                )
                Text(
                    text = "Added: ${dateFormat.format(Date(key.addedAt))} | Used: ${key.totalUsageCount}x",
                    fontSize = 11.sp, color = Color.Gray
                )
                if (isIsolated && key.isolatedAt != null) {
                    Text(
                        text = "Isolated: ${dateFormat.format(Date(key.isolatedAt))} (auto-restore next month)",
                        fontSize = 11.sp, color = AgentOrange
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
            }
        }
    }
}
