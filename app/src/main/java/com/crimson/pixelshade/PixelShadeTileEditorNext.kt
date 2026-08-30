@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.crimson.pixelshade

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private data class NextAppChoice(val label: String, val packageName: String, val resolveInfo: ResolveInfo)
private data class NextActivityChoice(val label: String, val component: ComponentName)

@Composable
fun PixelShadeTileEditorNext(onClose: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    var tiles by remember { mutableStateOf(PixelShadeTileStore.load(context)) }
    var type by remember { mutableStateOf("app") }
    var selectedApp by remember { mutableStateOf<NextAppChoice?>(null) }
    var selectedActivity by remember { mutableStateOf<NextActivityChoice?>(null) }
    var website by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var iconSource by remember { mutableStateOf("app") }
    var iconValue by remember { mutableStateOf("") }
    var monochrome by remember { mutableStateOf(true) }
    var widthUnits by remember { mutableIntStateOf(2) }
    var appPickerOpen by remember { mutableStateOf(false) }
    var activityPickerOpen by remember { mutableStateOf(false) }
    var materialPickerOpen by remember { mutableStateOf(false) }
    var iconPackPickerOpen by remember { mutableStateOf(false) }
    var appSearch by remember { mutableStateOf("") }
    var activitySearch by remember { mutableStateOf("") }

    val apps = remember {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { NextAppChoice(it.loadLabel(pm).toString(), it.activityInfo.packageName, it) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
    val iconPacks = remember { IconPackResolver.discover(context) }
    val activities = remember(selectedApp?.packageName) {
        selectedApp?.let { app ->
            runCatching {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_ACTIVITIES)
                    .activities.orEmpty()
                    .filter { it.exported }
                    .map {
                        NextActivityChoice(
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
                Text("Pick the target first, then choose any Material icon, app icon, or a specific glyph from an installed icon pack.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("app" to "App", "activity" to "Activity", "website" to "Website").forEachIndexed { i, pair ->
                        SegmentedButton(
                            selected = type == pair.first,
                            onClick = { type = pair.first },
                            shape = SegmentedButtonDefaults.itemShape(i, 3)
                        ) { Text(pair.second) }
                    }
                }
            }

            if (type != "website") {
                item {
                    ElevatedCard(Modifier.fillMaxWidth().clickable { appPickerOpen = true }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            AppOrDrawableIcon(selectedApp?.packageName, null, null, Modifier.size(48.dp))
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
                                Text(selectedActivity?.component?.className ?: "Browse exported activities", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                when (pair.first) {
                                    "app" -> iconValue = selectedApp?.packageName.orEmpty()
                                    "material" -> if (materialTileIcons.none { it.id == iconValue }) iconValue = "apps"
                                    "pack" -> if (IconPackResolver.decodeSelection(iconValue) == null) iconValue = ""
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, 3)
                        ) { Text(pair.second) }
                    }
                }
            }

            if (iconSource == "material") {
                item {
                    val selected = materialTileIcons.firstOrNull { it.id == iconValue }
                    ElevatedCard(Modifier.fillMaxWidth().clickable { materialPickerOpen = true }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(selected?.image ?: Icons.Default.Apps, null, Modifier.size(32.dp))
                            Column(Modifier.weight(1f)) {
                                Text(selected?.label ?: "Choose Material icon", style = MaterialTheme.typography.titleMedium)
                                Text("Search ${materialTileIcons.size} built-in icons by category", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.GridView, null)
                        }
                    }
                }
            }

            if (iconSource == "pack") {
                item {
                    val selection = IconPackResolver.decodeSelection(iconValue)
                    val selectedPack = iconPacks.firstOrNull { it.packageName == selection?.packPackage }
                    val selectedDrawable = selection?.drawableName?.let { name ->
                        selection.packPackage.let { pkg -> IconPackResolver.resolveDrawable(context, pkg, name) }
                    }
                    ElevatedCard(Modifier.fillMaxWidth().clickable { iconPackPickerOpen = true }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            AppOrDrawableIcon(selectedPack?.packageName, selectedDrawable, null, Modifier.size(44.dp))
                            Column(Modifier.weight(1f)) {
                                Text(selectedPack?.label ?: "Choose installed icon pack", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when {
                                        iconPacks.isEmpty() -> "No compatible icon packs discovered"
                                        selection?.drawableName != null -> selection.drawableName.replace('_', ' ')
                                        selectedPack != null -> "Auto-match selected app, or browse every glyph"
                                        else -> "Choose a pack, then browse its actual drawable icons"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.GridView, null)
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Adaptive monochrome", style = MaterialTheme.typography.titleMedium)
                        Text("Tint app or icon-pack artwork like a native active/inactive Pixel tile.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(monochrome, { monochrome = it })
                }
            }

            item {
                Text("Live preview", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NextTilePreview(
                        label.ifBlank { selectedApp?.label ?: "New tile" },
                        selectedApp?.packageName,
                        selectedActivity?.component,
                        iconSource,
                        iconValue,
                        monochrome,
                        false,
                        Modifier.weight(1f)
                    )
                    NextTilePreview(
                        label.ifBlank { selectedApp?.label ?: "New tile" },
                        selectedApp?.packageName,
                        selectedActivity?.component,
                        iconSource,
                        iconValue,
                        monochrome,
                        true,
                        Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DragIndicator, null)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tile.label, style = MaterialTheme.typography.titleMedium)
                            Text("${tile.type} • ${tile.widthUnits}×${tile.heightUnits}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(enabled = index > 0, onClick = {
                            val n = tiles.toMutableList(); val v = n.removeAt(index); n.add(index - 1, v); save(n)
                        }) { Icon(Icons.Default.KeyboardArrowUp, "Up") }
                        IconButton(enabled = index < tiles.lastIndex, onClick = {
                            val n = tiles.toMutableList(); val v = n.removeAt(index); n.add(index + 1, v); save(n)
                        }) { Icon(Icons.Default.KeyboardArrowDown, "Down") }
                        IconButton(onClick = {
                            val n = tiles.toMutableList(); n.removeAt(index); save(n)
                        }) { Icon(Icons.Default.DeleteOutline, "Remove") }
                    }
                }
            }
        }
    }

    if (appPickerOpen) {
        ModalBottomSheet(onDismissRequest = { appPickerOpen = false }) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Choose app", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    appSearch,
                    { appSearch = it },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Search installed apps") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
                LazyColumn(Modifier.heightIn(max = 520.dp)) {
                    items(apps.filter { appSearch.isBlank() || it.label.contains(appSearch, true) || it.packageName.contains(appSearch, true) }) { app ->
                        ListItem(
                            headlineContent = { Text(app.label) },
                            supportingContent = { Text(app.packageName) },
                            leadingContent = { AppOrDrawableIcon(app.packageName, null, null, Modifier.size(40.dp)) },
                            modifier = Modifier.clickable {
                                selectedApp = app
                                selectedActivity = null
                                label = app.label
                                if (iconSource == "app") iconValue = app.packageName
                                appPickerOpen = false
                            }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (activityPickerOpen) {
        ModalBottomSheet(onDismissRequest = { activityPickerOpen = false }) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Choose activity", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    activitySearch,
                    { activitySearch = it },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Search activities") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
                LazyColumn(Modifier.heightIn(max = 520.dp)) {
                    items(activities.filter { activitySearch.isBlank() || it.label.contains(activitySearch, true) || it.component.className.contains(activitySearch, true) }) { activity ->
                        ListItem(
                            headlineContent = { Text(activity.label) },
                            supportingContent = { Text(activity.component.className) },
                            modifier = Modifier.clickable {
                                selectedActivity = activity
                                label = activity.label
                                activityPickerOpen = false
                            }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (materialPickerOpen) {
        MaterialIconBrowserSheet(
            selected = iconValue,
            onSelect = { iconValue = it; materialPickerOpen = false },
            onDismiss = { materialPickerOpen = false }
        )
    }

    if (iconPackPickerOpen) {
        IconPackGlyphBrowserSheet(
            packs = iconPacks,
            currentValue = iconValue,
            targetPackage = selectedApp?.packageName,
            targetComponent = selectedActivity?.component,
            onSelect = { iconValue = it; iconPackPickerOpen = false },
            onDismiss = { iconPackPickerOpen = false }
        )
    }
}

@Composable
private fun MaterialIconBrowserSheet(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    val categories = remember { listOf("All") + materialTileIcons.map { it.category }.distinct() }
    val shown = materialTileIcons.filter {
        (category == "All" || it.category == category) &&
            (search.isBlank() || it.label.contains(search, true) || it.id.contains(search, true))
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Choose Material icon", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Search icons") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                singleLine = true
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
            }
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                gridItems(shown, key = { it.id }) { choice ->
                    Surface(
                        modifier = Modifier.aspectRatio(1f).clickable { onSelect(choice.id) },
                        shape = RoundedCornerShape(22.dp),
                        color = if (selected == choice.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(choice.image, choice.label, Modifier.size(30.dp))
                            Spacer(Modifier.height(5.dp))
                            Text(choice.label, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun IconPackGlyphBrowserSheet(
    packs: List<IconPackResolver.Pack>,
    currentValue: String,
    targetPackage: String?,
    targetComponent: ComponentName?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val decoded = remember(currentValue) { IconPackResolver.decodeSelection(currentValue) }
    var selectedPack by remember { mutableStateOf(decoded?.packPackage) }
    var search by remember { mutableStateOf("") }
    val glyphs by produceState<List<IconPackResolver.PackIcon>>(emptyList(), selectedPack) {
        value = selectedPack?.let { pack -> withContext(Dispatchers.IO) { IconPackResolver.listIcons(context, pack) } }.orEmpty()
    }
    val shown = glyphs.filter {
        search.isBlank() || it.searchLabel.contains(search, true) || it.drawableName.contains(search, true)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (selectedPack == null) "Choose icon pack" else "Choose icon", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (selectedPack != null) TextButton(onClick = { selectedPack = null; search = "" }) { Text("Packs") }
            }

            if (selectedPack == null) {
                if (packs.isEmpty()) {
                    Text("No compatible icon packs were found.", modifier = Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.heightIn(max = 560.dp)) {
                        items(packs) { pack ->
                            ListItem(
                                headlineContent = { Text(pack.label) },
                                supportingContent = { Text(pack.packageName) },
                                leadingContent = { AppOrDrawableIcon(pack.packageName, null, null, Modifier.size(42.dp)) },
                                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                                modifier = Modifier.clickable { selectedPack = pack.packageName }
                            )
                        }
                    }
                }
            } else {
                val pack = selectedPack!!
                val autoDrawable = remember(pack, targetPackage, targetComponent) {
                    targetComponent?.let { IconPackResolver.resolveForComponent(context, pack, it) }
                        ?: targetPackage?.let { IconPackResolver.resolveForPackage(context, pack, it) }
                }
                ElevatedCard(Modifier.fillMaxWidth().clickable { onSelect(pack) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppOrDrawableIcon(pack, autoDrawable, null, Modifier.size(40.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Auto-match selected app", style = MaterialTheme.typography.titleMedium)
                            Text("Use the pack's appfilter.xml mapping", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Search pack icons") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    singleLine = true
                )
                if (glyphs.isEmpty()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Reading appfilter.xml…", modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        gridItems(shown, key = { it.drawableName }) { glyph ->
                            val drawable = remember(pack, glyph.drawableName) { IconPackResolver.resolveDrawable(context, pack, glyph.drawableName) }
                            val encoded = IconPackResolver.encodeSelection(pack, glyph.drawableName)
                            Surface(
                                modifier = Modifier.aspectRatio(1f).clickable { onSelect(encoded) },
                                shape = RoundedCornerShape(20.dp),
                                color = if (currentValue == encoded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    AppOrDrawableIcon(null, drawable, null, Modifier.size(36.dp))
                                    Spacer(Modifier.height(4.dp))
                                    Text(glyph.drawableName.replace('_', ' '), style = MaterialTheme.typography.labelSmall, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NextTilePreview(
    label: String,
    packageName: String?,
    component: ComponentName?,
    source: String,
    value: String,
    monochrome: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val foreground = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val drawable = remember(packageName, component, source, value) {
        when (source) {
            "pack" -> IconPackResolver.resolveSelection(context, value, packageName, component)
            "app" -> packageName?.let { runCatching { context.packageManager.getApplicationIcon(it) }.getOrNull() }
            else -> null
        }
    }
    Surface(
        modifier.height(76.dp),
        shape = RoundedCornerShape(28.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = foreground
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (source) {
                "material" -> Icon(materialTileIcon(value), null, Modifier.size(26.dp))
                else -> AppOrDrawableIcon(packageName, drawable, if (monochrome) foreground else null, Modifier.size(29.dp))
            }
            Text(label, maxLines = 2, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun AppOrDrawableIcon(
    packageName: String?,
    override: Drawable?,
    tint: Color?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { imageView ->
            val drawable = override ?: packageName?.let { p -> runCatching { context.packageManager.getApplicationIcon(p) }.getOrNull() }
            imageView.setImageDrawable(drawable)
            imageView.imageTintList = tint?.let { ColorStateList.valueOf(it.toArgb()) }
        }
    )
}
