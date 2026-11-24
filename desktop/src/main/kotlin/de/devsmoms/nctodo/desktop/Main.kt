package de.devsmoms.nctodo.desktop

import de.devsmoms.nctodo.desktop.ui.MainFrame
import de.devsmoms.nctodo.desktop.ui.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.swing.Swing
import javax.swing.SwingUtilities

/**
 * NC Todo for Windows and Linux. The window is plain Swing (FlatLaf look): Java2D draws text
 * with the system's font smoothing on whole pixels, which is sharper than Skia-based UIs.
 */
fun main() {
    // Must be set before Swing starts: greyscale text smoothing (like browsers) or the system's (ClearType).
    val startSettings = runCatching { DesktopSettingsStore(DesktopPaths.dataDir.resolve("settings.properties")).load() }.getOrDefault(DesktopSettings())
    if (startSettings.grayscaleText) System.setProperty("awt.useSystemAAFontSettings", "on")
    // Native window title bar of FlatLaf on Windows 10/11 (matches light/dark).
    System.setProperty("flatlaf.useWindowDecorations", "true")
    System.setProperty("flatlaf.menuBarEmbedded", "false")
    SwingUtilities.invokeLater {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
        val state = AppState(scope)
        Theme.install(state.settings.theme, state.settings.fontSize)
        MainFrame(state, scope).isVisible = true
    }
}
