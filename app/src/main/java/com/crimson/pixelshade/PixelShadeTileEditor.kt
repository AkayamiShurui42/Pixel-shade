@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.crimson.pixelshade

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.UUID

private data class AppChoice(val label: String, val packageName: String, val resolveInfo: ResolveInfo)
private data class ActivityChoice(val label: String, val component: ComponentName)
private data class MaterialIconChoice(val id: String, val label: String, val image: ImageVector)

private val materialIcons = listOf(
    MaterialIconChoice("apps", "Apps", Icons.Default.Apps),
    MaterialIconChoice("bolt", "Bolt", Icons.Default.Bolt),
    MaterialIconChoice("flashlight", "Flashlight", Icons.Default.FlashlightOn),
    MaterialIconChoice("bluetooth", "Bluetooth", Icons.Default.Bluetooth),
    MaterialIconChoice("wifi", "Wi-Fi", Icons.Default.Wifi),
    MaterialIconChoice("link", "Link", Icons.Default.Link),
    MaterialIconChoice("language", "Web", Icons.Default.Language),
    MaterialIconChoice("gamepad", "Game", Icons.Default.SportsEsports),
    MaterialIconChoice("terminal", "Terminal", Icons.Default.Terminal),
    MaterialIconChoice("settings", "Settings", Icons.Default.Settings),
    MaterialIconChoice("star", "Star", Icons.Default.Star),
    MaterialIconChoice("camera", "Camera", Icons.Default.PhotoCamera)
)

@Composable
fun PixelShadeTileEditor(onClose: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    var tiles by remember { mutableStateOf(PixelShadeTileStore.load(context)) }
    var type by remember { mutableStateOf("app") }
    var selectedApp by remember { mutableStateOf<AppChoice?>(null) }
    var selectedActivity by remember { mutableStateOf<ActivityChoice?>(null) }
    var website by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var iconSource by remember { mutableStateOf("app") }
    var iconValue by remember { mutableStateOf("") }
    var monochrome by remember { mutableStateOf(true) }
    var widthUnits by remember { mutableIntStateOf(2) }
    var appPickerOpen by remember { mutableStateOf(false) }
    var activityPickerOpen by remember { mutableStateOf(false) }
    var iconPackPickerOpen by remember { mutableStateOf(false) }
    var appSearch by remember { mutableStateOf("") }
    var activitySearch by remember { mutableStateOf("") }

    val apps = remember {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { AppChoice(it.loadLabel(pm).toString(), it.activityInfo.packageName, it) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
    val iconPacks = remember { IconPackResolver.discover(context) }
    val activities = remember(selectedApp?.packageName) {
        selectedApp?.let { app ->
            runCatching {
                pm.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_ACTIVITIES)
                    .activities.orEmpty()
                    .filter { it.exported }
                    .map {
                        ActivityChoice(
                            runCatching { it.loadLabel(pm).toString() }.getOrDefault(it.name.substringAfterLast('.')),
                            ComponentName(app.packageName, it.name)
                        )
                    }
                    .sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
        }.orEmpty()
    }

    fun save(next: MutableList<PixelShadeTile>) {
        tiles = next
        PixelShadeTileStore.save(context, next)
    }
    fun chooseApp(app: AppChoice) {
        selectedApp = app
        selectedActivity = null
        label = app.label
        if (iconSource == "app") iconValue = app.packageName
    }

    val target = when (type) {
        "app" -> selectedApp?.packageName.orEmpty()
        "activity" -> selectedActivity?.component?.flattenToString().orEmpty()
        else -> website.trim()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tiles & shortcuts") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = { TextButton(onClick = onClose) { Text("Done") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Create tile", style = MaterialTheme.typography.headlineSmall)
                Text("Choose from what is actually installed. Package and activity strings stay out of the normal workflow.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("app" to "App", "activity" to "Activity", "website" to "Website").forEachIndexed { i, pair ->
                        SegmentedButton(selected = type == pair.first, onClick = { type = pair.first }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(pair.second) }
                    }
                }
            }
            if (type != "website") {
                item {
                    ElevatedCard(Modifier.fillMaxWidth().clickable { appPickerOpen = true }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            DrawableIcon(selectedApp?.packageName, null, Modifier.size(48.dp))
                            Column(Modifier.weight(1f)) {
                                Text(selectedApp?.label ?: "Choose installed app", style = MaterialTheme.typography.titleMedium)
                                Text(selectedApp?.packageName ?: "Search your apps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                }
            }
            if (type == "activity") {
                item {
                    ElevatedCard(Modifier.fillMaxWidth().clickable(enabled = selectedApp != null) { activityPickerOpen = true }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(selectedActivity?.label ?: "Choose activity", style = MaterialTheme.typography.titleMedium)
                                Text(selectedActivity?.component?.className ?: "Browse exported activities for this app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                }
            }
            if (type == "website") {
                item {
                    OutlinedTextField(
                        value = website,
                        onValueChange = { website = it },
                        label = { Text("Website") },
                        placeholder = { Text("https://example.com") },
                        leadingIcon = { Icon(Icons.Default.Language, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
            item {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Tile label") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            item {
                Text("Icon source", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("app" to "App", "material" to "Material", "pack" to "Icon pack").forEachIndexed { i, pair ->
                        SegmentedButton(
                            selected = iconSource == pair.first,
                            onClick = {
                                iconSource = pair.first
                                if (pair.first == "app") iconValue = selectedApp?.packageName.orEmpty()
                                if (pair.first == "material" && iconValue !in materialIcons.map { it.id }) iconValue = "apps"
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, 3)
                        ) { Text(pair.second) }
                    }
                }
            }
            if (iconSource == "material") {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        materialIcons.forEach { choice ->
                            FilterChip(
                                selected = iconValue == choice.id,
                                onClick = { iconValue = choice.id },
                                label = { Text(choice.label) },
                                leadingIcon = { Icon(choice.image, null, Modifier.size(18.dp)) }
                            )
                        }
                    }
                }
            }
            if (iconSource == "pack") {
                item {
                    ElevatedCard(Modifier.fillMaxWidth().clickable { iconPackPickerOpen = true }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(iconPacks.firstOrNull { it.packageName == iconValue }?.label ?: "Choose installed icon pack", style = MaterialTheme.typography.titleMedium)
                                Text(if (iconPacks.isEmpty()) "No compatible icon packs discovered" else "Pixel Shade will resolve this app through the pack's appfilter", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Adaptive monochrome", style = MaterialTheme.typography.titleMedium)
                        Text("Use Material You colors for active and inactive states.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = monochrome, onCheckedChange = { monochrome = it })
                }
            }
            item {
                Text("Live preview", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TilePreview(label.ifBlank { selectedApp?.label ?: "New tile" }, selectedApp?.packageName, selectedActivity?.component, iconSource, iconValue, false, Modifier.weight(1f))
                    TilePreview(label.ifBlank { selectedApp?.label ?: "New tile" }, selectedApp?.packageName, selectedActivity?.component, iconSource, iconValue, true, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "1×1", 2 to "2×1", 4 to "4×1").forEach { (units, text) ->
                        FilterChip(selected = widthUnits == units, onClick = { widthUnits = units }, label = { Text(text) })
                    }
                }
            }
            item {
                Button(
                    enabled = label.isNotBlank() && target.isNotBlank() && (iconSource != "pack" || iconValue.isNotBlank()),
                    onClick = {
                        val next = tiles.toMutableList()
                        next += PixelShadeTile(
                            id = UUID.randomUUID().toString(),
                            label = label.trim(),
                            type = type,
                            target = target,
                            iconSource = iconSource,
                            iconValue = iconValue,
                            monochrome = monochrome,
                            widthUnits = widthUnits,
                            heightUnits = 1
                        )
                        save(next)
                        label = ""
                        website = ""
                        selectedActivity = null
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) { Text("Add to Pixel Shade") }
            }
            item { HorizontalDivider(); Text("Your tiles", style = MaterialTheme.typography.headlineSmall) }
            itemsIndexed(tiles, key = { _, tile -> tile.id }) { index, tile ->
                ElevatedCard(shape = MaterialTheme.shapes.extraLarge) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.DragIndicator, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(tile.label, style = MaterialTheme.typography.titleMedium)
                            Text("${tile.type} • ${tile.widthUnits}×${tile.heightUnits}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(enabled = index > 0, onClick = { val next = tiles.toMutableList(); val value = next.removeAt(index); next.add(index - 1, value); save(next) }) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                        IconButton(enabled = index < tiles.lastIndex, onClick = { val next = tiles.toMutableList(); val value = next.removeAt(index); next.add(index + 1, value); save(next) }) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                        IconButton(onClick = { val next = tiles.toMutableList(); next.removeAt(index); save(next) }) { Icon(Icons.Default.DeleteOutline, "Remove") }
                    }
                }
            }
        }
    }

    if (appPickerOpen) {
        ModalBottomSheet(onDismissRequest = { appPickerOpen = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("Choose app", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(value = appSearch, onValueChange = { appSearch = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Search installed apps") }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), singleLine = true)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                    items(apps.filter { appSearch.isBlank() || it.label.contains(appSearch, true) || it.packageName.contains(appSearch, true) }) { app ->
                        ListItem(
                            headlineContent = { Text(app.label) },
                            supportingContent = { Text(app.packageName) },
                            leadingContent = { DrawableIcon(app.packageName, null, Modifier.size(40.dp)) },
                            modifier = Modifier.clickable { chooseApp(app); appPickerOpen = false }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (activityPickerOpen) {
        ModalBottomSheet(onDismissRequest = { activityPickerOpen = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("Choose activity", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(value = activitySearch, onValueChange = { activitySearch = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Search activities") }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), singleLine = true)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                    items(activities.filter { activitySearch.isBlank() || it.label.contains(activitySearch, true) || it.component.className.contains(activitySearch, true) }) { activity ->
                        ListItem(
                            headlineContent = { Text(activity.label) },
                            supportingContent = { Text(activity.component.className) },
                            modifier = Modifier.clickable { selectedActivity = activity; label = activity.label; activityPickerOpen = false }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (iconPackPickerOpen) {
        ModalBottomSheet(onDismissRequest = { iconPackPickerOpen = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("Choose icon pack", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                if (iconPacks.isEmpty()) {
                    Text("No compatible installed icon packs were found.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp))
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                        items(iconPacks) { pack ->
                            ListItem(
                                headlineContent = { Text(pack.label) },
                                supportingContent = { Text(pack.packageName) },
                                leadingContent = { DrawableIcon(pack.packageName, null, Modifier.size(40.dp)) },
                                modifier = Modifier.clickable { iconValue = pack.packageName; iconPackPickerOpen = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DrawableIcon(packageName: String?, drawableOverride: android.graphics.drawable.Drawable?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { image -> image.setImageDrawable(drawableOverride ?: packageName?.let { runCatching { context.packageManager.getApplicationIcon(it) }.getOrNull() }) }
    )
}

@Composable
private fun TilePreview(label: String, packageName: String?, component: ComponentName?, iconSource: String, iconValue: String, active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val packDrawable = remember(packageName, component, iconSource, iconValue) {
        if (iconSource == "pack" && iconValue.isNotBlank()) {
            if (component != null) IconPackResolver.resolveForComponent(context, iconValue, component)
            else packageName?.let { IconPackResolver.resolveForPackage(context, iconValue, it) }
        } else null
    }
    Surface(
        modifier.height(76.dp),
        shape = RoundedCornerShape(28.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (iconSource) {
                "material" -> Icon(materialIcons.firstOrNull { it.id == iconValue }?.image ?: Icons.Default.Apps, null, Modifier.size(26.dp))
                "pack" -> DrawableIcon(packageName, packDrawable, Modifier.size(28.dp))
                else -> DrawableIcon(packageName, null, Modifier.size(28.dp))
            }
            Text(label, maxLines = 2, style = MaterialTheme.typography.labelLarge)
        }
    }
}
