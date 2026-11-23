package de.devsmoms.nctodo.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.List as PlainListIcon
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector
import de.devsmoms.nctodo.data.ListIcon

/**
 * Monochrome list icons. Each icon is stored as an emoji at the start of the list name
 * (so it syncs via CalDAV), but is always displayed as a single-color Material icon
 * tinted with the list color – never as a colorful emoji.
 */
object ListIcons {
    /** Emoji (as stored in the name) to the icon shown in the app. Order = order in the picker. */
    val ALL: List<Pair<String, ImageVector>> = listOf(
        "🛒" to Icons.Outlined.ShoppingCart,
        "🏠" to Icons.Outlined.Home,
        "💼" to Icons.Outlined.Work,
        "👥" to Icons.Outlined.Groups,
        "📚" to Icons.AutoMirrored.Outlined.MenuBook,
        "🎓" to Icons.Outlined.School,
        "✈️" to Icons.Outlined.Flight,
        "🚗" to Icons.Outlined.DirectionsCar,
        "🎁" to Icons.Outlined.CardGiftcard,
        "🎉" to Icons.Outlined.Celebration,
        "📅" to Icons.Outlined.Event,
        "🎵" to Icons.Outlined.MusicNote,
        "📷" to Icons.Outlined.PhotoCamera,
        "💻" to Icons.Outlined.Computer,
        "🔧" to Icons.Outlined.Build,
        "🧹" to Icons.Outlined.CleaningServices,
        "🏃" to Icons.AutoMirrored.Outlined.DirectionsRun,
        "🏋️" to Icons.Outlined.FitnessCenter,
        "⚽" to Icons.Outlined.SportsSoccer,
        "💊" to Icons.Outlined.LocalPharmacy,
        "🍽️" to Icons.Outlined.Restaurant,
        "🌸" to Icons.Outlined.LocalFlorist,
        "🌳" to Icons.Outlined.Park,
        "🐾" to Icons.Outlined.Pets,
        "💰" to Icons.Outlined.Savings,
        "📦" to Icons.Outlined.Inventory2,
        "💡" to Icons.Outlined.Lightbulb,
        "❤️" to Icons.Outlined.FavoriteBorder,
        "⭐" to Icons.Outlined.StarBorder,
    )

    val DEFAULT: ImageVector = Icons.AutoMirrored.Outlined.PlainListIcon

    // Lookup ignores emoji variation selectors (U+FE0F), so "✈" and "✈️" match alike.
    private val byKey = ALL.associate { (emoji, icon) -> key(emoji) to icon }

    private fun key(emoji: String) = emoji.replace("️", "").replace("︎", "")

    /** Icon for an emoji, or null if the emoji has no monochrome counterpart. */
    fun iconFor(emoji: String?): ImageVector? = emoji?.let { byKey[key(it)] }

    /** Icon of a list name, or null if it has none (or only an unknown emoji). */
    fun iconOf(listName: String): ImageVector? = iconFor(ListIcon.split(listName).first)

    /** List name for display: any leading emoji is removed, known or not. */
    fun label(listName: String): String = ListIcon.plainName(listName)
}
