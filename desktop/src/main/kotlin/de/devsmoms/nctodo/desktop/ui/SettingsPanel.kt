package de.devsmoms.nctodo.desktop.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import de.devsmoms.nctodo.desktop.AppState
import de.devsmoms.nctodo.desktop.DesktopSettings
import de.devsmoms.nctodo.desktop.LinkPreviews
import de.devsmoms.nctodo.desktop.ThemeMode
import de.devsmoms.nctodo.desktop.appVersion
import de.devsmoms.nctodo.sync.SyncStatus
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.ScrollPaneConstants

private const val REPO_URL = "https://github.com/devSmoms/nc-ToDo"

/** Settings of this computer, shown in place of tasks and details. */
class SettingsPanel(private val state: AppState, private val frame: MainFrame) : JPanel(BorderLayout()) {
    private val body = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false; alignmentX = Component.LEFT_ALIGNMENT }

    init {
        val head = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = emptyBorder(22, 32, 8, 16)
            add(uiLabel("Einstellungen", 26f).apply { font = Theme.heading(26f) }, BorderLayout.CENTER)
            add(iconButton(VectorIcon(Icons.Outlined.Close, 20) { Theme.subtext }, "Schließen") { state.showSettings = false }, BorderLayout.EAST)
        }
        add(head, BorderLayout.NORTH)
        val wrap = VerticalList().apply {
            border = emptyBorder(0, 32, 24, 32)
            add(body)
        }
        add(scroller(wrap), BorderLayout.CENTER)
    }

    override fun paintComponent(g: Graphics) {
        g.color = Theme.background
        g.fillRect(0, 0, width, height)
    }

    // Rebuilding while a combo box popup is open would close it.
    private var openPopup = false

    fun refresh(status: SyncStatus) {
        if (openPopup) return
        val s = state.settings
        val creds = state.credentials ?: return
        body.removeAll()

        section("Konto") {
            add(JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                border = emptyBorder(14, 16, 14, 16)
                alignmentX = Component.LEFT_ALIGNMENT
                add(Avatar({ state.displayName }, 46))
                add(Box.createHorizontalStrut(14))
                add(JPanel().apply {
                    isOpaque = false
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    add(uiLabel(state.displayName, 16f, bold = true))
                    add(uiLabel("${creds.username} · ${creds.serverUrl.removePrefix("https://").trimEnd('/')}", 12f) { Theme.subtext })
                })
                add(Box.createHorizontalGlue())
            })
            divider()
            add(clickRow("Anzeigename", if (s.accountName.isBlank()) "Name aus Nextcloud: ${creds.displayName}" else s.accountName) {
                askText(
                    frame, "Anzeigename", s.accountName, "Speichern", placeholder = creds.displayName, maxLength = 60, allowEmpty = true,
                    hintText = "Leer lassen, um den Namen aus Nextcloud zu verwenden (${creds.displayName}).",
                )?.let { name -> state.updateSettings { it.copy(accountName = name) } }
            })
            divider()
            add(clickRow("Abmelden", null, color = Theme.error, icon = VectorIcon(Icons.AutoMirrored.Outlined.Logout, 20) { Theme.error }) {
                if (confirm(frame, "Abmelden?", Sidebar.LOGOUT_TEXT, "Abmelden")) state.logout()
            })
        }

        section("Darstellung") {
            val combo = JComboBox(ThemeMode.entries.map { it.label }.toTypedArray()).apply {
                selectedIndex = s.theme.ordinal
                addActionListener {
                    val mode = ThemeMode.entries[selectedIndex]
                    if (mode != state.settings.theme) state.updateSettings { it.copy(theme = mode) }
                }
                trackPopup()
            }
            add(controlRow("Design", null, combo))
            divider()
            val sizes = JComboBox(DesktopSettings.FONT_SIZES.map(DesktopSettings::fontLabel).toTypedArray()).apply {
                selectedIndex = DesktopSettings.FONT_SIZES.indexOf(s.fontSize).coerceAtLeast(0)
                addActionListener {
                    val px = DesktopSettings.FONT_SIZES[selectedIndex]
                    if (px != state.settings.fontSize) state.updateSettings { it.copy(fontSize = px) }
                }
                trackPopup()
            }
            add(controlRow("Schriftgröße", null, sizes))
            divider()
            add(switchRow(
                "Weiche Schriftglättung",
                "Graustufen wie im Browser statt ClearType. Wird nach einem Neustart der App wirksam.",
                s.grayscaleText,
            ) { v -> state.updateSettings { it.copy(grayscaleText = v) } })
        }

        section("Listen und Aufgaben") {
            add(switchRow("Deck-Listen ausblenden", "Listen der Nextcloud-App Deck erscheinen nicht im Menü.", s.hideDeckLists) { v ->
                state.updateSettings { it.copy(hideDeckLists = v) }
            })
            divider()
            add(switchRow("Link-Vorschau", "Lädt Titel und Bild verlinkter Seiten direkt von der jeweiligen Webseite.", s.linkPreviews) { v ->
                if (!v) LinkPreviews.clear()
                state.updateSettings { it.copy(linkPreviews = v) }
            })
            divider()
            add(switchRow("Vor dem Entfernen nachfragen", "Gilt für Links. Gelöschte Aufgaben lassen sich kurz rückgängig machen.", s.confirmRemovals) { v ->
                state.updateSettings { it.copy(confirmRemovals = v) }
            })
        }

        section("Synchronisation") {
            val combo = JComboBox(DesktopSettings.SYNC_OPTIONS.map(DesktopSettings::syncLabel).toTypedArray()).apply {
                selectedIndex = DesktopSettings.SYNC_OPTIONS.indexOf(s.syncIntervalSec).coerceAtLeast(0)
                addActionListener {
                    val sec = DesktopSettings.SYNC_OPTIONS[selectedIndex]
                    if (sec != state.settings.syncIntervalSec) state.updateSettings { it.copy(syncIntervalSec = sec) }
                }
                trackPopup()
            }
            add(controlRow("Automatisch synchronisieren", "Solange das Fenster offen ist; Änderungen werden immer sofort gesendet.", combo))
            divider()
            val sub = when {
                status.running -> "Wird synchronisiert …"
                status.offline -> "Server nicht erreichbar – Änderungen werden später gesendet"
                else -> status.lastSuccess?.let { "Zuletzt: " + lastSync.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: "Strg+R"
            }
            add(clickRow("Jetzt synchronisieren", sub) { state.syncNow() })
        }

        section("Über NC Todo") {
            add(clickRow("Version", appVersion) {})
            divider()
            add(clickRow("Quellcode und Fehler melden", "github.com/devSmoms/nc-ToDo", icon = VectorIcon(Icons.AutoMirrored.Outlined.OpenInNew, 18) { Theme.subtext }, iconRight = true) {
                AppState.openInBrowser(REPO_URL)
            })
            divider()
            add(clickRow("Lizenzen", "NC Todo steht unter der GPL-3.0; verwendete Bibliotheken") { showLicenses() })
        }
        body.revalidate()
        body.repaint()
    }

    private fun JComboBox<*>.trackPopup() {
        addPopupMenuListener(object : javax.swing.event.PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: javax.swing.event.PopupMenuEvent?) { openPopup = true }
            override fun popupMenuWillBecomeInvisible(e: javax.swing.event.PopupMenuEvent?) { openPopup = false }
            override fun popupMenuCanceled(e: javax.swing.event.PopupMenuEvent?) { openPopup = false }
        })
    }

    private fun section(title: String, build: JPanel.() -> Unit) {
        body.add(uiLabel(title, 13f, bold = true) { Theme.accent }.apply {
            alignmentX = Component.LEFT_ALIGNMENT
            border = emptyBorder(14, 4, 8, 0)
        })
        val card = RoundPanel(12) { Theme.card }.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            build()
        }
        card.maximumSize = Dimension(720, card.preferredSize.height)
        body.add(card)
    }

    private fun JPanel.divider() = add(object : JComponent() {
        init {
            alignmentX = Component.LEFT_ALIGNMENT
            preferredSize = Dimension(10, 1)
            maximumSize = Dimension(Int.MAX_VALUE, 1)
        }

        override fun paintComponent(g: Graphics) {
            g.color = Theme.divider
            g.fillRect(0, 0, width, 1)
        }
    })

    private fun texts(title: String, subtitle: String?, color: java.awt.Color? = null) = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(uiLabel(title, 14f) { color ?: Theme.text })
        subtitle?.let { add(uiLabel(it, 12f) { Theme.subtext }) }
    }

    private fun baseRow() = RoundPanel(12) { null }.apply {
        layout = BorderLayout(14, 0)
        alignmentX = Component.LEFT_ALIGNMENT
        border = emptyBorder(12, 16, 12, 16)
    }

    private fun clickRow(
        title: String,
        subtitle: String?,
        color: java.awt.Color? = null,
        icon: VectorIcon? = null,
        iconRight: Boolean = false,
        onClick: () -> Unit,
    ): JComponent = baseRow().apply {
        if (icon != null) add(JLabel(icon), if (iconRight) BorderLayout.EAST else BorderLayout.WEST)
        add(texts(title, subtitle, color), BorderLayout.CENTER)
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        onMouse(click = { onClick() }, hover = { h -> fill = { if (h) Theme.cardHover else null }; repaint() })
    }

    private fun controlRow(title: String, subtitle: String?, control: JComponent): JComponent = baseRow().apply {
        add(texts(title, subtitle), BorderLayout.CENTER)
        add(JPanel(java.awt.GridBagLayout()).apply { isOpaque = false; add(control) }, BorderLayout.EAST)
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    private fun switchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit): JComponent {
        val sw = Switch(checked, onChange)
        return baseRow().apply {
            add(texts(title, subtitle), BorderLayout.CENTER)
            add(JPanel(java.awt.GridBagLayout()).apply { isOpaque = false; add(sw) }, BorderLayout.EAST)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
            onMouse(click = { sw.toggle() }, hover = { h -> fill = { if (h) Theme.cardHover else null }; repaint() })
        }
    }

    /** Libraries of the desktop app and their licence texts (bundled in the resources). */
    private fun showLicenses() {
        val libraries = listOf(
            "NC Todo" to "GPL-3.0",
            "FlatLaf (FormDev Software)" to "Apache-2.0",
            "Compose Multiplatform runtime, Skiko, Material Icons" to "Apache-2.0",
            "Kotlin, kotlinx.coroutines" to "Apache-2.0",
            "OkHttp, Okio (Square)" to "Apache-2.0",
            "JNA (Java Native Access)" to "Apache-2.0",
        )
        val text = buildString {
            libraries.forEach { (name, license) -> append("$name — $license\n") }
            listOf("GPL-3.0", "Apache-2.0").forEach { id ->
                append("\n\n==================== $id ====================\n\n")
                append(javaClass.classLoader.getResourceAsStream("licenses/$id.txt")?.use { it.readBytes().decodeToString() } ?: "Lizenztext nicht gefunden.")
            }
        }
        JDialog(javax.swing.SwingUtilities.getWindowAncestor(this), "Lizenzen", java.awt.Dialog.ModalityType.APPLICATION_MODAL).apply {
            contentPane.add(JScrollPane(JTextArea(text).apply {
                isEditable = false
                font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                caretPosition = 0
                border = emptyBorder(12, 12, 12, 12)
            }))
            size = Dimension(720, 600)
            setLocationRelativeTo(frame)
            isVisible = true
        }
    }
}

private val lastSync = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm 'Uhr'")
