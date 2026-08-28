package com.crimson.pixelshade

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Xml

object IconPackResolver {
    data class Pack(val label: String, val packageName: String)

    private val discoveryActions = listOf(
        "org.adw.launcher.THEMES",
        "com.gau.go.launcherex.theme",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME"
    )

    fun discover(context: Context): List<Pack> {
        val pm = context.packageManager
        return discoveryActions.flatMap { action ->
            runCatching { pm.queryIntentActivities(Intent(action), 0) }.getOrDefault(emptyList())
        }.map { info ->
            Pack(info.loadLabel(pm).toString(), info.activityInfo.packageName)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    fun resolveForPackage(context: Context, iconPackPackage: String, targetPackage: String): Drawable? {
        val launch = context.packageManager.getLaunchIntentForPackage(targetPackage)
        val component = launch?.component
        return if (component != null) resolveForComponent(context, iconPackPackage, component) else null
    }

    fun resolveForComponent(context: Context, iconPackPackage: String, component: ComponentName): Drawable? {
        val packContext = runCatching {
            context.createPackageContext(iconPackPackage, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: return null
        val res = packContext.resources
        val assets = res.assets
        val parser = runCatching { Xml.newPullParser().apply { setInput(assets.open("appfilter.xml"), "UTF-8") } }.getOrNull() ?: return null
        val flattened = component.flattenToString()
        val shortFlattened = component.flattenToShortString()
        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (event == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "item") {
                val comp = parser.getAttributeValue(null, "component").orEmpty()
                    .removePrefix("ComponentInfo{")
                    .removeSuffix("}")
                val drawableName = parser.getAttributeValue(null, "drawable")
                if (drawableName != null && (comp == flattened || comp == shortFlattened || flattened.endsWith(comp))) {
                    val id = res.getIdentifier(drawableName, "drawable", iconPackPackage)
                    if (id != 0) return runCatching { res.getDrawable(id, packContext.theme) }.getOrNull()
                }
            }
            event = parser.next()
        }
        return null
    }
}
