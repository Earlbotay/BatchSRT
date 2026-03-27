package com.earlstore.batchsrt.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.earlstore.batchsrt.model.*
import com.earlstore.batchsrt.service.BatchProcessingService
import com.earlstore.batchsrt.service.BatchProcessingService.BatchProcessState
import com.earlstore.batchsrt.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(navController: NavController) {
    val context = LocalContext.current
    val processState by BatchProcessingService.processingState.collectAsStateWithLifecycle()
    val files by BatchProcessingService.fileStates.collectAsStateWithLifecycle()

    // Start service
    LaunchedEffect(Unit) {
        val intent = Intent(context, BatchProcessingService::class.java)
        context.startForegroundService(intent)
    }

    val completedCount = files.count { it.status == ProcessingStatus.COMPLETED }
    val failedCount = files.count { it.status == ProcessingStatus.FAILED }
    val totalProgress = if (files.isNotEmpty()) files.sumOf { it.progress.toDouble() } / files.size else 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Processing", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (processState is BatchProcessState.Completed || processState is BatchProcessState.Error) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBg,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = DarkBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Overall Progress ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        when (val state = processState) {
                            is BatchProcessState.Processing -> {
                                Text("⚙️ Processing...", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(Modifier.height(4.dp))
                                Text("File ${state.currentFile} of ${state.totalFiles}: ${state.fileName}", color = Silver400, fontSize = 14.sp)
                            }
                            is BatchProcessState.Completed -> {
                                Text("✅ Batch Complete!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            }
                            is BatchProcessState.Error -> {
                                Text("❌ Error", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF5350))
                                Spacer(Modifier.height(4.dp))
                                Text(state.message, color = Silver400, fontSize = 14.sp)
                            }
                            else -> {
                                Text("⏳ Preparing...", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { totalProgress.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = Blue600,
                            trackColor = SurfaceBg,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("✅ $completedCount done", color = Color(0xFF4CAF50), fontSize = 13.sp)
                            Text("❌ $failedCount failed", color = Color(0xFFEF5350), fontSize = 13.sp)
                            Text("📊 ${(totalProgress * 100).toInt()}%", color = Blue600, fontSize = 13.sp)
                        }
                    }
                }
            }

            // ── File status list ──
            items(files) { file ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceBg)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Status icon
                        val (icon, tint) = when (file.status) {
                            ProcessingStatus.PENDING -> Icons.Filled.HourglassEmpty to Silver400
                            ProcessingStatus.PROCESSING -> Icons.Filled.Sync to Blue600
                            ProcessingStatus.COMPLETED -> Icons.Filled.CheckCircle to Color(0xFF4CAF50)
                            ProcessingStatus.FAILED -> Icons.Filled.Error to Color(0xFFEF5350)
                            ProcessingStatus.CANCELLED -> Icons.Filled.Cancel to Color(0xFFFF9800)
                        }
                        Icon(icon, "status", tint = tint, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            when (file.status) {
                                ProcessingStatus.PROCESSING -> {
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { file.progress },
                                        modifier = Modifier.fillMaxWidth().height(4.dp),
                                        color = Blue600,
                                        trackColor = CardBg,
                                    )
                                }
                                ProcessingStatus.COMPLETED -> {
                                    Text("${file.subtitleCount} subtitles generated", color = Color(0xFF4CAF50), fontSize = 12.sp)
                                }
                                ProcessingStatus.FAILED -> {
                                    Text(file.error ?: "Failed", color = Color(0xFFEF5350), fontSize = 12.sp)
                                }
                                else -> {
                                    Text("Waiting...", color = Silver400, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ── Done button ──
            if (processState is BatchProcessState.Completed) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { navController.popBackStack("home", false) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue600)
                    ) {
                        Icon(Icons.Filled.Home, "home")
                        Spacer(Modifier.width(8.dp))
                        Text("Back to Home", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
