package com.earlstore.subforge.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.earlstore.subforge.model.SubtitleItem
import com.earlstore.subforge.service.SubtitleGeneratorService
import kotlinx.coroutines.launch
import com.earlstore.subforge.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoName by remember { mutableStateOf("No video selected") }
    var selectedLanguage by remember { mutableStateOf("en") }
    var languageExpanded by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf("SRT") }

    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("Ready — Select a video to begin") }
    var subtitles by remember { mutableStateOf<List<SubtitleItem>>(emptyList()) }

    var service by remember { mutableStateOf<SubtitleGeneratorService?>(null) }
    var bound by remember { mutableStateOf(false) }

    val languages = listOf(
        "en" to "English", "ms" to "Bahasa Melayu", "id" to "Bahasa Indonesia",
        "zh" to "中文", "ja" to "日本語", "ko" to "한국어",
        "hi" to "हिन्दी", "ar" to "العربية", "th" to "ไทย",
        "vi" to "Tiếng Việt", "fr" to "Français", "de" to "Deutsch",
        "es" to "Español", "pt" to "Português", "ru" to "Русский",
        "it" to "Italiano", "nl" to "Nederlands", "pl" to "Polski",
        "tr" to "Türkçe", "tl" to "Filipino"
    )

    // Video picker
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedVideoUri = it
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                selectedVideoName = if (nameIndex >= 0) cursor.getString(nameIndex) else "Selected video"
            }
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted */ }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    // Service connection
    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as SubtitleGeneratorService.LocalBinder).getService()
                bound = true
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                bound = false
            }
        }
    }

    // Collect service state
    LaunchedEffect(bound) {
        if (bound && service != null) {
            launch { service!!.isProcessing.collect { isProcessing = it } }
            launch { service!!.progress.collect { progress = it } }
            launch { service!!.statusText.collect { statusText = it } }
            launch { service!!.subtitles.collect { subtitles = it } }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Movie,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("SubForge", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("editor") }) {
                        Icon(Icons.Outlined.Edit, "Editor", tint = GoldAccent)
                    }
                    IconButton(onClick = { navController.navigate("about") }) {
                        Icon(Icons.Outlined.Info, "About", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header card
            item {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(listOf(RedPrimary, RedDark))
                            )
                            .padding(24.dp)
                    ) {
                        Column {
                            Text("🎬 Auto Subtitle", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Generate .SRT & .VTT from any video", color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            // Video selector
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("📁 Select Video", fontWeight = FontWeight.SemiBold, color = GoldAccent)
                        Spacer(Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { videoPicker.launch(arrayOf("video/*")) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.VideoFile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Browse Videos")
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            selectedVideoName,
                            color = if (selectedVideoUri != null) Color.White else Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Language selector
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("🌍 Speech Language", fontWeight = FontWeight.SemiBold, color = GoldAccent)
                        Spacer(Modifier.height(12.dp))

                        ExposedDropdownMenuBox(
                            expanded = languageExpanded,
                            onExpandedChange = { languageExpanded = !languageExpanded }
                        ) {
                            OutlinedTextField(
                                value = languages.find { it.first == selectedLanguage }?.second ?: "English",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = languageExpanded,
                                onDismissRequest = { languageExpanded = false }
                            ) {
                                languages.forEach { (code, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            selectedLanguage = code
                                            languageExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Export format
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("💾 Export Format", fontWeight = FontWeight.SemiBold, color = GoldAccent)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf("SRT", "VTT", "Both").forEach { format ->
                                FilterChip(
                                    selected = exportFormat == format,
                                    onClick = { exportFormat = format },
                                    label = { Text(".$format") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RedPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Generate button
            item {
                Button(
                    onClick = {
                        if (selectedVideoUri == null) {
                            Toast.makeText(context, "Please select a video first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val intent = Intent(context, SubtitleGeneratorService::class.java)
                        context.startForegroundService(intent)
                        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

                        // Start generation after binding
                        service?.startGeneration(selectedVideoUri!!, selectedLanguage)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isProcessing && selectedVideoUri != null,
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Processing...")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("🔥 Generate Subtitles", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Progress
            if (isProcessing || subtitles.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(statusText, color = Color.White, fontSize = 13.sp)
                                Text("${(progress * 100).toInt()}%", color = GoldAccent, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = RedPrimary,
                                trackColor = DarkSurface
                            )

                            if (isProcessing) {
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { service?.stopGeneration() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, tint = RedPrimary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Stop", color = RedPrimary)
                                }
                            }
                        }
                    }
                }
            }

            // Subtitle preview
            if (subtitles.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📝 Preview (${subtitles.size} subtitles)", color = GoldAccent, fontWeight = FontWeight.SemiBold)

                        Row {
                            IconButton(onClick = {
                                val content = if (exportFormat == "VTT") service?.exportVtt() else service?.exportSrt()
                                content?.let {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, it)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share subtitles"))
                                }
                            }) {
                                Icon(Icons.Default.Share, "Share", tint = Color.White)
                            }
                        }
                    }
                }

                items(subtitles.takeLast(20)) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Row(Modifier.padding(12.dp)) {
                            Text(
                                "#${item.index}",
                                color = GoldAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.width(36.dp)
                            )
                            Column {
                                Text(
                                    "${item.startTimeFormatted()} → ${item.endTimeFormatted()}",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                                Text(item.text, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
