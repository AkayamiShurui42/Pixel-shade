package com.crimson.pixelshade

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.UUID

@Composable
fun PixelShadeTileEditor(onClose: () -> Unit) {
    val context = LocalContext.current
    var tiles by remember { mutableStateOf(PixelShadeTileStore.load(context)) }
    var type by remember { mutableStateOf("app") }
    var label by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("default") }

    fun persist(next: MutableList<PixelShadeTile>) {
        tiles = next
        PixelShadeTileStore.save(context, next)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Quick tiles") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Done") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ElevatedCard(shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Create tile", style = MaterialTheme.typography.titleLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("app", "activity", "website").forEach { option ->
                                FilterChip(
                                    selected = type == option,
                                    onClick = { type = option },
                                    label = { Text(option.replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }
                        OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Tile label") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            value = target,
                            onValueChange = { target = it },
                            label = {
                                Text(
                                    when (type) {
                                        "activity" -> "package/class activity"
                                        "website" -> "https://..."
                                        else -> "package name"
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Icon", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("default", "star", "bolt", "link", "apps").forEach { option ->
                                FilterChip(selected = icon == option, onClick = { icon = option }, label = { Text(option.replaceFirstChar { it.uppercase() }) })
                            }
                        }
                        Button(
                            enabled = label.isNotBlank() && target.isNotBlank(),
                            onClick = {
                                val next = tiles.toMutableList()
                                next += PixelShadeTile(UUID.randomUUID().toString(), label.trim(), type, target.trim(), icon)
                                persist(next)
                                label = ""
                                target = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Add tile") }
                    }
                }
            }

            item { Text("Your tiles", style = MaterialTheme.typography.titleLarge) }

            itemsIndexed(tiles, key = { _, tile -> tile.id }) { index, tile ->
                ElevatedCard(shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tile.label, style = MaterialTheme.typography.titleMedium)
                        Text("${tile.type}: ${tile.target}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                enabled = index > 0,
                                onClick = {
                                    val next = tiles.toMutableList()
                                    val item = next.removeAt(index)
                                    next.add(index - 1, item)
                                    persist(next)
                                }
                            ) { Text("Up") }
                            FilledTonalButton(
                                enabled = index < tiles.lastIndex,
                                onClick = {
                                    val next = tiles.toMutableList()
                                    val item = next.removeAt(index)
                                    next.add(index + 1, item)
                                    persist(next)
                                }
                            ) { Text("Down") }
                            OutlinedButton(onClick = {
                                val next = tiles.toMutableList()
                                next.removeAt(index)
                                persist(next)
                            }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
}
