package com.crimson.pixelshade

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class PixelShadeTile(
    val id: String,
    val label: String,
    val type: String,
    val target: String,
    val iconSource: String = "app",
    val iconValue: String = "",
    val monochrome: Boolean = true,
    val widthUnits: Int = 2,
    val heightUnits: Int = 1
) {
    val icon: String get() = iconValue.ifBlank { iconSource }
}

object PixelShadeTileStore {
    private const val KEY_TILES = "custom_tiles_json"

    fun load(context: Context): MutableList<PixelShadeTile> {
        val raw = PixelShadeConfig.prefs(context).getString(KEY_TILES, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val legacyIcon = o.optString("icon", "")
                PixelShadeTile(
                    id = o.getString("id"),
                    label = o.getString("label"),
                    type = o.getString("type"),
                    target = o.getString("target"),
                    iconSource = o.optString("iconSource", if (legacyIcon.isBlank() || legacyIcon == "default") "app" else "material"),
                    iconValue = o.optString("iconValue", legacyIcon),
                    monochrome = o.optBoolean("monochrome", true),
                    widthUnits = o.optInt("widthUnits", 2).coerceIn(1, 4),
                    heightUnits = o.optInt("heightUnits", 1).coerceIn(1, 2)
                )
            }
        }.getOrDefault(mutableListOf())
    }

    fun save(context: Context, tiles: List<PixelShadeTile>) {
        val arr = JSONArray()
        tiles.forEach { tile ->
            arr.put(JSONObject().apply {
                put("id", tile.id)
                put("label", tile.label)
                put("type", tile.type)
                put("target", tile.target)
                put("iconSource", tile.iconSource)
                put("iconValue", tile.iconValue)
                put("monochrome", tile.monochrome)
                put("widthUnits", tile.widthUnits)
                put("heightUnits", tile.heightUnits)
            })
        }
        PixelShadeConfig.prefs(context).edit().putString(KEY_TILES, arr.toString()).apply()
    }

    fun launch(context: Context, tile: PixelShadeTile): Boolean {
        val intent = when (tile.type) {
            "app" -> context.packageManager.getLaunchIntentForPackage(tile.target)
            "activity" -> {
                val component = ComponentName.unflattenFromString(tile.target) ?: return false
                Intent().setComponent(component)
            }
            "website" -> {
                val uri = runCatching { Uri.parse(tile.target) }.getOrNull() ?: return false
                Intent(Intent.ACTION_VIEW, uri)
            }
            else -> null
        } ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
