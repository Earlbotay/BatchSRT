package com.autopilot.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autopilot.ai.data.model.ConversationMessage
import com.autopilot.ai.data.model.TaskStatus
import com.autopilot.ai.ui.theme.AgentBlue
import com.autopilot.ai.ui.theme.AgentGreen
import com.autopilot.ai.ui.theme.AgentOrange
import com.autopilot.ai.viewmodel.MainViewModel

@Composable
fun ChatScreen(viewModel: MainViewModel) {
    val messages by viewModel.messages.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val tasks by viewModel.currentTasks.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Task progress bar
        if (tasks.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Tasks", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    tasks.forEach { task ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            val color = when (task.status) {
                                TaskStatus.COMPLETED -> AgentGreen
                                TaskStatus.RUNNING -> AgentBlue
                                TaskStatus.FAILED -> Color.Red
                                TaskStatus.STOPPED -> AgentOrange
                                TaskStatus.PENDING -> Color.Gray
                            }
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${task.id}. ${task.description}",
                                fontSize = 12.sp,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                MessageBubble(msg)
            }

            if (isRunning) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Working...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Input bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.clearChat() }) {
                Icon(Icons.Default.Delete, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { viewModel.updateInput(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Tell me what to do...") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 3,
                enabled = !isRunning
            )

            Spacer(modifier = Modifier.width(4.dp))

            if (isRunning) {
                IconButton(onClick = { viewModel.stopExecution() }) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.Red)
                }
            } else {
                IconButton(
                    onClick = { viewModel.sendCommand() },
                    enabled = inputText.isNotBlank()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) AgentBlue else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ConversationMessage) {
    val isUser = msg.role == "user"
    val isSystem = msg.role == "system"
    val bgColor = when {
        isUser -> MaterialTheme.colorScheme.primary
        isSystem -> MaterialTheme.colorScheme.surfaceVariant
        msg.isSubAgent -> AgentOrange.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (msg.isSubAgent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(14.dp), tint = AgentOrange)
                Spacer(modifier = Modifier.width(4.dp))
                Text("SubAgent #${msg.subAgentId}", fontSize = 11.sp, color = AgentOrange, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(2.dp))
        }

        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Text(
                text = msg.content,
                modifier = Modifier.padding(12.dp),
                color = textColor,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}
