package com.earlstore.batchsrt.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.earlstore.batchsrt.model.*
import com.earlstore.batchsrt.service.BatchProcessingService
import com.earlstore.batchsrt.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    var files by remember { mutableStateOf(listOf<VideoFile>()) }
    var settings by remember { mutableStateOf(BatchSettings()) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showTargetLangDialog by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf(false) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled */ }

    // File picker — multiple files
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val newFiles = uris.mapNotNull { uri ->
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    VideoFile(
                        uri = uri,
                        name = cursor.getString(nameIndex) ?: "Unknown",
                        size = cursor.getLong(sizeIndex)
                    )
                } else null
            }
        }
        files = files + newFiles
    }

    // Check & request permissions
    LaunchedEffect(Unit) {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.READ_MEDIA_VIDEO)
            perms.add(Manifest.permission.READ_MEDIA_AUDIO)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = perms.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ContentCopy, "icon", tint = Blue600, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("BatchSRT", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("about") }) {
                        Icon(Icons.Outlined.Info, "About")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBg,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = DarkBg,
        floatingActionButton = {
            if (files.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        BatchProcessingService.updateFiles(files)
                        BatchProcessingService.updateSettings(settings)
                        navController.navigate("processing")
                    },
                    containerColor = Blue600,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Filled.PlayArrow, "Start") },
                    text = { Text("Process ${files.size} Files", fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Add Files Card ──
            item {
                Card(
                    onClick = { filePickerLauncher.launch(arrayOf("video/*", "audio/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.VideoLibrary, "Add", tint = Blue600, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Add Video/Audio Files", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text("Tap to select multiple files", fontSize = 14.sp, color = Silver400)
                    }
                }
            }

            // ── Settings Card ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("⚙️ Batch Settings", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Spacer(Modifier.height(12.dp))

                        // Source language
                        val srcLang = SUPPORTED_LANGUAGES.find { it.speechCode == settings.sourceLanguage }
                        OutlinedButton(
                            onClick = { showLanguageDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Language, "lang", tint = Blue600)
                            Spacer(Modifier.width(8.dp))
                            Text("Source: ${srcLang?.name ?: "Bahasa Melayu"}", color = Color.White)
                        }

                        Spacer(Modifier.height(8.dp))

                        // Translation toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🌐 Translate Subtitles", color = Color.White)
                            Switch(
                                checked = settings.translateEnabled,
                                onCheckedChange = { settings = settings.copy(translateEnabled = it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = Blue600)
                            )
                        }

                        if (settings.translateEnabled) {
                            Spacer(Modifier.height(8.dp))
                            val tgtLang = SUPPORTED_LANGUAGES.find { it.code == settings.targetLanguage }
                            OutlinedButton(
                                onClick = { showTargetLangDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Translate, "target", tint = Blue600)
                                Spacer(Modifier.width(8.dp))
                                Text("Target: ${tgtLang?.name ?: "English"}", color = Color.White)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Output format
                        OutlinedButton(
                            onClick = { showFormatDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Description, "format", tint = Blue600)
                            Spacer(Modifier.width(8.dp))
                            Text("Format: ${settings.outputFormat.displayName}", color = Color.White)
                        }
                    }
                }
            }

            // ── File Queue ──
            if (files.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📁 File Queue (${files.size})", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        TextButton(onClick = { files = emptyList() }) {
                            Text("Clear All", color = Color(0xFFEF5350))
                        }
                    }
                }

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
                            Icon(
                                if (file.name.contains("audio", true)) Icons.Filled.AudioFile else Icons.Filled.VideoFile,
                                "file", tint = Blue600, modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatFileSize(file.size), color = Silver400, fontSize = 12.sp)
                            }
                            IconButton(onClick = { files = files.filter { it.uri != file.uri } }) {
                                Icon(Icons.Filled.Close, "Remove", tint = Color(0xFFEF5350))
                            }
                        }
                    }
                }
            }

            // ── Empty state ──
            if (files.isEmpty()) {
                item {
                    Spacer(Modifier.height(32.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📂", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No files added yet", color = Silver400, fontSize = 16.sp)
                        Text("Tap the card above to add files", color = Silver400, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    // ── Dialogs ──
    if (showLanguageDialog) {
        LanguagePickerDialog(
            title = "Source Language",
            selected = settings.sourceLanguage,
            languages = SUPPORTED_LANGUAGES.map { it.speechCode to it.name },
            onSelect = { settings = settings.copy(sourceLanguage = it) ; showLanguageDialog = false },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showTargetLangDialog) {
        LanguagePickerDialog(
            title = "Target Language",
            selected = settings.targetLanguage,
            languages = SUPPORTED_LANGUAGES.map { it.code to it.name },
            onSelect = { settings = settings.copy(targetLanguage = it) ; showTargetLangDialog = false },
            onDismiss = { showTargetLangDialog = false }
        )
    }

    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text("Output Format") },
            text = {
                Column {
                    OutputFormat.entries.forEach { format ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.outputFormat == format,
                                onClick = { settings = settings.copy(outputFormat = format) ; showFormatDialog = false }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(format.displayName)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showFormatDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun LanguagePickerDialog(title: String, selected: String, languages: List<Pair<String, String>>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(languages.size) { i ->
                    val (code, name) = languages[i]
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == code, onClick = { onSelect(code) })
                        Spacer(Modifier.width(8.dp))
                        Text(name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024 -> String.format("%.1f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }
}
