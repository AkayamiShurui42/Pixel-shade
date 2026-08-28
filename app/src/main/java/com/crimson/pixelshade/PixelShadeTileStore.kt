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
    val icon: String = "default"
)

object PixelShadeTileStore {
    private const val KEY_TILES = "custom_tiles_json"

    fun load(context: Context): MutableList<PixelShadeTile> {
        val raw = PixelShadeConfig.prefs(context).getString(KEY_TILES, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                PixelShadeTile(
                    id = o.getString("id"),
                    label = o.getString("label"),
                    type = o.getString("type"),
                    target = o.getString("target"),
                    icon = o.optString("icon", "default")
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
                put("icon", tile.icon)
            })
        }
        PixelShadeConfig.prefs(context).edit().putString(KEY_TILES, arr.toString()).apply()
    }

    fun launch(context: Context, tile: PixelShadeTile) {
        val intent = when (tile.type) {
            "app" -> context.packageManager.getLaunchIntentForPackage(tile.target)
            "activity" -> {
                val component = ComponentName.unflattenFromString(tile.target) ?: return
                Intent().setComponent(component)
            }
            "website" -> Intent(Intent.ACTION_VIEW, Uri.parse(tile.target))
            else -> null
        } ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
