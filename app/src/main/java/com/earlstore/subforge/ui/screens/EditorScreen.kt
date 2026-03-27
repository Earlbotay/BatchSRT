package com.earlstore.subforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.earlstore.subforge.model.SubtitleItem
import com.earlstore.subforge.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(navController: NavController) {

    var subtitles by remember {
        mutableStateOf(
            listOf(
                SubtitleItem(1, 0, 3000, "Welcome to SubForge"),
                SubtitleItem(2, 3000, 6000, "Edit your subtitles here"),
                SubtitleItem(3, 6000, 10000, "Tap any subtitle to modify it")
            )
        )
    }

    var editingIndex by remember { mutableIntStateOf(-1) }
    var editText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("✏️ Subtitle Editor", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val newIndex = subtitles.size + 1
                        val lastEnd = subtitles.lastOrNull()?.endTime ?: 0
                        subtitles = subtitles + SubtitleItem(
                            newIndex, lastEnd, lastEnd + 3000, "New subtitle"
                        )
                    }) {
                        Icon(Icons.Default.Add, "Add", tint = GoldAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        if (subtitles.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No subtitles yet", color = Color.Gray, fontSize = 18.sp)
                    Text("Generate subtitles from Home or add manually", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }

                item {
                    Text(
                        "${subtitles.size} subtitles",
                        color = GoldAccent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(8.dp))
                }

                itemsIndexed(subtitles) { index, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "#${item.index} | ${item.startTimeFormatted()} → ${item.endTimeFormatted()}",
                                    color = GoldAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row {
                                    IconButton(
                                        onClick = {
                                            editingIndex = index
                                            editText = item.text
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, "Edit", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            subtitles = subtitles.toMutableList().also { it.removeAt(index) }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, "Delete", tint = RedPrimary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            if (editingIndex == index) {
                                OutlinedTextField(
                                    value = editText,
                                    onValueChange = { editText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            subtitles = subtitles.toMutableList().also {
                                                it[index] = item.copy(text = editText)
                                            }
                                            editingIndex = -1
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Save")
                                    }
                                    OutlinedButton(
                                        onClick = { editingIndex = -1 },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                            } else {
                                Text(item.text, color = Color.White, fontSize = 15.sp)
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}
