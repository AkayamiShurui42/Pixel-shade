package com.crimson.pixelshade

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class MaterialTileIcon(
    val id: String,
    val label: String,
    val category: String,
    val image: ImageVector
)

val materialTileIcons: List<MaterialTileIcon> = listOf(
    MaterialTileIcon("apps", "Apps", "General", Icons.Default.Apps),
    MaterialTileIcon("star", "Star", "General", Icons.Default.Star),
    MaterialTileIcon("favorite", "Favorite", "General", Icons.Default.Favorite),
    MaterialTileIcon("home", "Home", "General", Icons.Default.Home),
    MaterialTileIcon("work", "Work", "General", Icons.Default.Work),
    MaterialTileIcon("school", "School", "General", Icons.Default.School),
    MaterialTileIcon("settings", "Settings", "General", Icons.Default.Settings),
    MaterialTileIcon("tune", "Tune", "General", Icons.Default.Tune),
    MaterialTileIcon("search", "Search", "General", Icons.Default.Search),
    MaterialTileIcon("edit", "Edit", "General", Icons.Default.Edit),
    MaterialTileIcon("add", "Add", "General", Icons.Default.Add),
    MaterialTileIcon("delete", "Delete", "General", Icons.Default.Delete),

    MaterialTileIcon("wifi", "Wi-Fi", "Connectivity", Icons.Default.Wifi),
    MaterialTileIcon("bluetooth", "Bluetooth", "Connectivity", Icons.Default.Bluetooth),
    MaterialTileIcon("hotspot", "Hotspot", "Connectivity", Icons.Default.WifiTethering),
    MaterialTileIcon("cellular", "Cellular", "Connectivity", Icons.Default.SignalCellularAlt),
    MaterialTileIcon("data", "Data", "Connectivity", Icons.Default.DataUsage),
    MaterialTileIcon("airplane", "Airplane", "Connectivity", Icons.Default.AirplanemodeActive),
    MaterialTileIcon("nfc", "NFC", "Connectivity", Icons.Default.Nfc),
    MaterialTileIcon("vpn", "VPN", "Connectivity", Icons.Default.VpnKey),
    MaterialTileIcon("link", "Link", "Connectivity", Icons.Default.Link),
    MaterialTileIcon("public", "Internet", "Connectivity", Icons.Default.Public),
    MaterialTileIcon("sync", "Sync", "Connectivity", Icons.Default.Sync),
    MaterialTileIcon("cast", "Cast", "Connectivity", Icons.Default.Cast),

    MaterialTileIcon("flashlight", "Flashlight", "Device", Icons.Default.FlashlightOn),
    MaterialTileIcon("brightness", "Brightness", "Device", Icons.Default.Brightness6),
    MaterialTileIcon("rotation", "Rotation", "Device", Icons.Default.ScreenRotation),
    MaterialTileIcon("dark", "Dark mode", "Device", Icons.Default.DarkMode),
    MaterialTileIcon("light", "Light mode", "Device", Icons.Default.LightMode),
    MaterialTileIcon("battery", "Battery", "Device", Icons.Default.BatteryFull),
    MaterialTileIcon("battery_saver", "Battery saver", "Device", Icons.Default.BatterySaver),
    MaterialTileIcon("power", "Power", "Device", Icons.Default.PowerSettingsNew),
    MaterialTileIcon("lock", "Lock", "Device", Icons.Default.Lock),
    MaterialTileIcon("unlock", "Unlock", "Device", Icons.Default.LockOpen),
    MaterialTileIcon("phone", "Phone", "Device", Icons.Default.PhoneAndroid),
    MaterialTileIcon("computer", "Computer", "Device", Icons.Default.Computer),
    MaterialTileIcon("watch", "Watch", "Device", Icons.Default.Watch),
    MaterialTileIcon("tv", "TV", "Device", Icons.Default.Tv),

    MaterialTileIcon("volume", "Volume", "Media", Icons.Default.VolumeUp),
    MaterialTileIcon("mute", "Mute", "Media", Icons.Default.VolumeOff),
    MaterialTileIcon("music", "Music", "Media", Icons.Default.MusicNote),
    MaterialTileIcon("play", "Play", "Media", Icons.Default.PlayArrow),
    MaterialTileIcon("pause", "Pause", "Media", Icons.Default.Pause),
    MaterialTileIcon("previous", "Previous", "Media", Icons.Default.SkipPrevious),
    MaterialTileIcon("next", "Next", "Media", Icons.Default.SkipNext),
    MaterialTileIcon("mic", "Microphone", "Media", Icons.Default.Mic),
    MaterialTileIcon("mic_off", "Mic off", "Media", Icons.Default.MicOff),
    MaterialTileIcon("camera", "Camera", "Media", Icons.Default.PhotoCamera),
    MaterialTileIcon("video", "Video", "Media", Icons.Default.Videocam),

    MaterialTileIcon("dnd", "Do Not Disturb", "Status", Icons.Default.DoNotDisturbOn),
    MaterialTileIcon("notifications", "Notifications", "Status", Icons.Default.Notifications),
    MaterialTileIcon("notifications_off", "Notifications off", "Status", Icons.Default.NotificationsOff),
    MaterialTileIcon("alarm", "Alarm", "Status", Icons.Default.Alarm),
    MaterialTileIcon("timer", "Timer", "Status", Icons.Default.Timer),
    MaterialTileIcon("bedtime", "Bedtime", "Status", Icons.Default.Bedtime),
    MaterialTileIcon("location", "Location", "Status", Icons.Default.LocationOn),
    MaterialTileIcon("security", "Security", "Status", Icons.Default.Security),
    MaterialTileIcon("shield", "Shield", "Status", Icons.Default.Shield),

    MaterialTileIcon("language", "Web", "Apps & actions", Icons.Default.Language),
    MaterialTileIcon("gamepad", "Game", "Apps & actions", Icons.Default.SportsEsports),
    MaterialTileIcon("terminal", "Terminal", "Apps & actions", Icons.Default.Terminal),
    MaterialTileIcon("map", "Map", "Apps & actions", Icons.Default.Map),
    MaterialTileIcon("navigation", "Navigation", "Apps & actions", Icons.Default.Navigation),
    MaterialTileIcon("car", "Car", "Apps & actions", Icons.Default.DirectionsCar),
    MaterialTileIcon("walk", "Walk", "Apps & actions", Icons.Default.DirectionsWalk),
    MaterialTileIcon("calculate", "Calculator", "Apps & actions", Icons.Default.Calculate),
    MaterialTileIcon("qr", "QR scanner", "Apps & actions", Icons.Default.QrCodeScanner),
    MaterialTileIcon("copy", "Copy", "Apps & actions", Icons.Default.ContentCopy),
    MaterialTileIcon("share", "Share", "Apps & actions", Icons.Default.Share),
    MaterialTileIcon("download", "Download", "Apps & actions", Icons.Default.Download),
    MaterialTileIcon("upload", "Upload", "Apps & actions", Icons.Default.Upload)
)

fun materialTileIcon(id: String): ImageVector =
    materialTileIcons.firstOrNull { it.id == id }?.image ?: Icons.Default.Apps
