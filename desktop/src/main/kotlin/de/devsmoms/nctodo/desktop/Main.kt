package de.devsmoms.nctodo.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.skia.Image

/** Accent like the Android app (Microsoft To Do blue). */
val AccentBlue = Color(0xFF2564CF)

fun main() = application {
    val icon = remember {
        Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")?.use {
            BitmapPainter(Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap())
        }
    }
    val windowState = rememberWindowState(size = DpSize(1200.dp, 800.dp))
    val shortcuts = remember { Shortcuts() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "NC Todo",
        icon = icon,
        state = windowState,
        onPreviewKeyEvent = { e ->
            if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) return@Window false
            when (e.key) {
                Key.N -> { shortcuts.newTask(); true }
                Key.F -> { shortcuts.search(); true }
                Key.R -> { shortcuts.sync(); true }
                else -> false
            }
        },
    ) {
        window.minimumSize = java.awt.Dimension(720, 480)
        NcTodoTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val scope = rememberCoroutineScope()
                val state = remember { AppState(scope) }
                when {
                    !state.ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.credentials == null -> LoginView(state)
                    else -> MainView(state, shortcuts)
                }
            }
        }
    }
}

/** Keyboard shortcuts of the window, connected by [MainView]. */
class Shortcuts {
    var newTask: () -> Unit = {}
    var search: () -> Unit = {}
    var sync: () -> Unit = {}
}

@Composable
fun NcTodoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(primary = Color(0xFF8AB4F8)) else lightColorScheme(primary = AccentBlue),
        content = content,
    )
}

/** "#RRGGBB" list colour; falls back to the accent. */
fun parseColor(hex: String): Color = runCatching {
    Color(0xFF000000 or hex.removePrefix("#").take(6).toLong(16))
}.getOrDefault(AccentBlue)
