package com.crimson.pixelshade

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

object IconPackResolver {
    data class Pack(val label: String, val packageName: String)
    data class PackIcon(
        val drawableName: String,
        val component: String,
        val searchLabel: String
    )
    data class Selection(val packPackage: String, val drawableName: String?)

    private const val SEPARATOR = "|"

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

    fun encodeSelection(packPackage: String, drawableName: String): String =
        "$packPackage$SEPARATOR$drawableName"

    fun decodeSelection(value: String): Selection? {
        if (value.isBlank()) return null
        val split = value.indexOf(SEPARATOR)
        return if (split > 0 && split < value.lastIndex) {
            Selection(value.substring(0, split), value.substring(split + 1))
        } else {
            // Legacy builds stored only the icon-pack package, meaning auto-match.
            Selection(value, null)
        }
    }

    fun listIcons(context: Context, iconPackPackage: String): List<PackIcon> {
        val packContext = runCatching {
            context.createPackageContext(iconPackPackage, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: return emptyList()

        val parser = openAppFilter(packContext, iconPackPackage) ?: return emptyList()
        val unique = linkedMapOf<String, PackIcon>()
        runCatching {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name.equals("item", ignoreCase = true)) {
                    val drawableName = parser.getAttributeValue(null, "drawable").orEmpty().trim()
                    val component = parser.getAttributeValue(null, "component").orEmpty()
                        .removePrefix("ComponentInfo{")
                        .removeSuffix("}")
                    if (drawableName.isNotBlank()) {
                        val id = packContext.resources.getIdentifier(drawableName, "drawable", iconPackPackage)
                        if (id != 0 && drawableName !in unique) {
                            val componentHint = component
                                .substringBefore('/')
                                .substringAfterLast('.')
                                .takeIf { it.isNotBlank() }
                                .orEmpty()
                            unique[drawableName] = PackIcon(
                                drawableName = drawableName,
                                component = component,
                                searchLabel = listOf(
                                    drawableName.replace('_', ' '),
                                    componentHint
                                ).filter { it.isNotBlank() }.joinToString(" ")
                            )
                        }
                    }
                }
                event = parser.next()
            }
        }
        return unique.values.toList()
    }

    fun resolveDrawable(context: Context, iconPackPackage: String, drawableName: String): Drawable? {
        val packContext = runCatching {
            context.createPackageContext(iconPackPackage, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: return null
        val id = packContext.resources.getIdentifier(drawableName, "drawable", iconPackPackage)
        if (id == 0) return null
        return runCatching { packContext.resources.getDrawable(id, packContext.theme) }.getOrNull()
    }

    fun resolveSelection(
        context: Context,
        selectionValue: String,
        targetPackage: String?,
        component: ComponentName?
    ): Drawable? {
        val selection = decodeSelection(selectionValue) ?: return null
        return if (!selection.drawableName.isNullOrBlank()) {
            resolveDrawable(context, selection.packPackage, selection.drawableName)
        } else {
            component?.let { resolveForComponent(context, selection.packPackage, it) }
                ?: targetPackage?.let { resolveForPackage(context, selection.packPackage, it) }
        }
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
        val parser = openAppFilter(packContext, iconPackPackage) ?: return null
        val flattened = component.flattenToString()
        val shortFlattened = component.flattenToShortString()
        return runCatching {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name.equals("item", ignoreCase = true)) {
                    val comp = parser.getAttributeValue(null, "component").orEmpty()
                        .removePrefix("ComponentInfo{")
                        .removeSuffix("}")
                    val drawableName = parser.getAttributeValue(null, "drawable")
                    if (drawableName != null && (comp == flattened || comp == shortFlattened || flattened.endsWith(comp))) {
                        return@runCatching resolveDrawable(context, iconPackPackage, drawableName)
                    }
                }
                event = parser.next()
            }
            null
        }.getOrNull()
    }

    private fun openAppFilter(packContext: Context, iconPackPackage: String): XmlPullParser? {
        runCatching {
            return Xml.newPullParser().apply {
                setInput(packContext.assets.open("appfilter.xml"), "UTF-8")
            }
        }
        val id = packContext.resources.getIdentifier("appfilter", "xml", iconPackPackage)
        if (id != 0) return runCatching { packContext.resources.getXml(id) }.getOrNull()
        return null
    }
}
