package com.earlstore.batchsrt.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.earlstore.batchsrt.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // ── App Identity ──
            Text("📋", fontSize = 64.sp)
            Spacer(Modifier.height(12.dp))
            Text("BatchSRT", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("v1.0.0", fontSize = 14.sp, color = Silver400)
            Spacer(Modifier.height(8.dp))
            Text(
                "Batch process multiple video/audio files and generate subtitle files automatically using AI speech recognition.",
                textAlign = TextAlign.Center,
                color = Silver400,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(24.dp))

            // ── Earl Store ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🏪", fontSize = 36.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Earl Store", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Blue600)
                    Spacer(Modifier.height(4.dp))
                    Text("Developer & Publisher", fontSize = 14.sp, color = Silver400)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Earl Store builds free, open-source Android applications powered by AI. We believe in making technology accessible to everyone.",
                        textAlign = TextAlign.Center,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Features ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("✨ Features", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    FeatureRow(Icons.Filled.Queue, "Batch Processing", "Process multiple files at once")
                    FeatureRow(Icons.Filled.Subtitles, "Auto Subtitles", "AI-powered speech recognition")
                    FeatureRow(Icons.Filled.Translate, "Translation", "Translate subtitles to 20 languages")
                    FeatureRow(Icons.Filled.Description, "Multiple Formats", "Export as .srt, .vtt, or both")
                    FeatureRow(Icons.Filled.Speed, "Fast Processing", "Optimized batch engine")
                    FeatureRow(Icons.Filled.Notifications, "Progress Tracking", "Real-time progress for each file")
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Tech Stack ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("🛠️ Tech Stack", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    TechItem("Language", "Kotlin 2.0")
                    TechItem("UI Framework", "Jetpack Compose + Material 3")
                    TechItem("Speech Engine", "Android SpeechRecognizer")
                    TechItem("Translation", "Google ML Kit (Offline)")
                    TechItem("Architecture", "Foreground Service + StateFlow")
                    TechItem("Target SDK", "35 (Android 15)")
                    TechItem("Min SDK", "26 (Android 8.0)")
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Supported Languages ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("🌍 20 Supported Languages", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Bahasa Melayu • English • 中文 • 日本語 • 한국어 • हिन्दी • العربية • ไทย • Tiếng Việt • Bahasa Indonesia • Filipino • Français • Deutsch • Español • Português • Русский • Italiano • Nederlands • Türkçe • Polski",
                        color = Silver400,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── License ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📄 Open Source", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("Licensed under MIT License", color = Silver400, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Free to use, modify, and distribute", color = Silver400, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Made with ❤️ by Earl Store", color = Silver400, fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun FeatureRow(icon: ImageVector, title: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, title, tint = Blue600, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(desc, color = Silver400, fontSize = 12.sp)
        }
    }
}

@Composable
fun TechItem(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Silver400, fontSize = 13.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}
