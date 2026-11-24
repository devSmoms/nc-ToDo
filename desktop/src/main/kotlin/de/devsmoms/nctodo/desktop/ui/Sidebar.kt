package de.devsmoms.nctodo.desktop.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import de.devsmoms.nctodo.data.TaskList
import de.devsmoms.nctodo.desktop.AppState
import de.devsmoms.nctodo.desktop.ListIcons
import de.devsmoms.nctodo.desktop.Selection
import de.devsmoms.nctodo.desktop.SmartList
import de.devsmoms.nctodo.desktop.groupedLists
import de.devsmoms.nctodo.desktop.label
import de.devsmoms.nctodo.desktop.tasksFor
import de.devsmoms.nctodo.sync.LocalData
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.KeyEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

fun SmartList.icon(): ImageVector = when (this) {
    SmartList.MY_DAY -> Icons.Outlined.WbSunny
    SmartList.IMPORTANT -> Icons.Outlined.StarBorder
    SmartList.PLANNED -> Icons.Outlined.CalendarMonth
    SmartList.ALL -> Icons.Outlined.Home
}

fun SmartList.color(): Color = when (this) {
    SmartList.MY_DAY -> Theme.sun
    SmartList.IMPORTANT -> Theme.star
    SmartList.PLANNED -> Theme.planned
    SmartList.ALL -> Theme.blue
}

/** Left column: account, search, smart lists, lists and groups, "Neue Liste". */
class Sidebar(private val state: AppState, private val frame: MainFrame) : JPanel(BorderLayout()) {
    private val nameLabel = uiLabel("", 14f, bold = true)
    private val serverLabel = uiLabel("", 12f) { Theme.subtext }
    private val search = JTextField()
    private val entries = VerticalList()
    private var updatingSearch = false

    init {
        preferredSize = Dimension(290, 0)
        background = null

        // Account (pinned at the top): click opens the account menu.
        val account = RoundPanel(10) { null }.apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = emptyBorder(6, 6, 6, 6)
            add(Avatar({ state.displayName }, 38))
            add(Box.createHorizontalStrut(12))
            add(JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(nameLabel)
                add(serverLabel)
            })
            add(Box.createHorizontalGlue())
        }
        account.onMouse(
            click = { e -> accountMenu().show(account, 0, account.height) },
            hover = { h -> account.fill = { if (h) Theme.navHover else null }; account.repaint() },
        )
        val gear = iconButton(VectorIcon(Icons.Outlined.Settings, 20) { if (state.showSettings) Theme.accent else Theme.subtext }, "Einstellungen") {
            state.showSettings = !state.showSettings
        }
        val top = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = emptyBorder(12, 10, 8, 8)
            add(account, BorderLayout.CENTER)
            add(gear, BorderLayout.EAST)
        }

        search.apply {
            putClientProperty("JTextField.placeholderText", "Suchen   (Strg+F)")
            putClientProperty("JTextField.leadingIcon", VectorIcon(Icons.Outlined.Search, 18) { Theme.subtext })
            putClientProperty("JTextField.showClearButton", true)
            putClientProperty("FlatLaf.style", "borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; margin: 6,8,6,8")
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = changed()
                override fun removeUpdate(e: DocumentEvent?) = changed()
                override fun changedUpdate(e: DocumentEvent?) = changed()
            })
            registerKeyboardAction({ text = "" }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED)
        }
        val searchWrap = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = emptyBorder(0, 14, 10, 14)
            add(search)
        }

        val head = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(top, BorderLayout.NORTH)
            add(searchWrap, BorderLayout.SOUTH)
        }
        add(head, BorderLayout.NORTH)

        val scroll = scroller(entries)
        add(scroll, BorderLayout.CENTER)

        // "Neue Liste" and "Neue Gruppe" (pinned at the bottom).
        val newList = NavRow(Icons.Outlined.Add, { Theme.subtext }, "Neue Liste", 0, false, 0, onClick = { createList(null) })
        val bottom = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.divider),
                emptyBorder(6, 0, 8, 8),
            )
            add(newList, BorderLayout.CENTER)
            add(iconButton(VectorIcon(Icons.Outlined.CreateNewFolder, 20) { Theme.subtext }, "Neue Gruppe") { createGroup() }, BorderLayout.EAST)
        }
        add(bottom, BorderLayout.SOUTH)
    }

    override fun paintComponent(g: Graphics) {
        g.color = Theme.sidebar
        g.fillRect(0, 0, width, height)
        g.color = Theme.divider
        g.fillRect(width - 1, 0, 1, height)
    }

    fun focusSearch() {
        search.requestFocusInWindow()
        search.selectAll()
    }

    private fun changed() {
        if (updatingSearch) return
        state.search = search.text.replace("\n", "")
        if (search.text.isNotBlank()) state.showSettings = false
    }

    fun refresh(data: LocalData) {
        nameLabel.text = state.displayName
        serverLabel.text = state.credentials?.serverUrl?.removePrefix("https://")?.trimEnd('/').orEmpty()
        if (search.text != state.search) {
            updatingSearch = true
            search.text = state.search
            updatingSearch = false
        }
        search.background = Theme.field

        entries.removeAll()
        val plain = state.search.isBlank() && !state.showSettings
        fun count(sel: Selection) = tasksFor(sel, data, state.hidden).count { !it.todo.completed }
        SmartList.entries.forEach { kind ->
            val sel = Selection.Smart(kind)
            entries.add(NavRow(kind.icon(), { kind.color() }, kind.title, count(sel), plain && state.selection == sel, 0, onClick = { select(sel) }))
        }
        entries.add(Divider())
        val (ungrouped, groups) = groupedLists(data.lists, state.settings.emptyGroups)
        val groupNames = groups.map { it.first }
        ungrouped.forEach { entries.add(listRow(it, data, groupNames, indent = 0)) }
        groups.forEach { (group, lists) ->
            val collapsed = group in state.settings.collapsedGroups
            entries.add(groupRow(group, collapsed, lists.isEmpty()))
            if (!collapsed) lists.forEach { entries.add(listRow(it, data, groupNames, indent = 24)) }
        }
        entries.add(Box.createVerticalGlue())
        entries.revalidate()
        entries.repaint()
    }

    private fun select(sel: Selection) {
        state.search = ""
        state.showSettings = false
        state.selection = sel
    }

    private fun listRow(list: TaskList, data: LocalData, groups: List<String>, indent: Int): JComponent {
        val sel = Selection.OfList(list.url)
        val selected = state.search.isBlank() && !state.showSettings && state.selection == sel
        val color = parseColor(list.color)
        val open = tasksFor(sel, data, state.hidden).count { !it.todo.completed }
        val status = when {
            !list.isOwner && !list.canWrite -> Icons.Outlined.Lock to "Geteilt, nur lesen"
            !list.isOwner || list.shares.isNotEmpty() -> Icons.Outlined.People to "Geteilte Liste"
            else -> null
        }
        return NavRow(ListIcons.iconOf(list.name) ?: ListIcons.DEFAULT, { color }, list.label(), open, selected, indent, status, onClick = { select(sel) }) {
            popupMenu(
                (if (list.group == null) "In Gruppe verschieben …" else "Gruppe ändern …") to {
                    val (changed, group) = groupPicker(frame, list.label(), list.group, groups)
                    if (changed) state.moveListToGroup(list, group)
                },
            )
        }
    }

    private fun groupRow(group: String, collapsed: Boolean, empty: Boolean): JComponent {
        fun menu() = popupMenu(
            "Neue Liste in der Gruppe" to { createList(group) },
            "Gruppe umbenennen" to {
                askText(frame, "Gruppe umbenennen", group, "Umbenennen")?.let { state.renameGroup(group, it) }
            },
            "Gruppe auflösen" to {
                if (confirm(frame, "Gruppe „$group“ auflösen?", "Die Listen bleiben erhalten und stehen danach ohne Gruppe im Menü.", "Auflösen")) {
                    state.dissolveGroup(group)
                }
            },
        )
        val row = RoundPanel(8) { null }.apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = emptyBorder(4, 14, 4, 4)
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, Theme.px(38))
            preferredSize = Dimension(260, Theme.px(38))
            if (empty) toolTipText = "Noch leer – Rechtsklick auf eine Liste → In Gruppe verschieben"
            add(JLabel(VectorIcon(if (collapsed) Icons.Outlined.Folder else Icons.Outlined.FolderOpen, 20) { Theme.subtext }))
            add(Box.createHorizontalStrut(12))
            add(JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(uiLabel(group, 14f, bold = true))
            })
            add(Box.createHorizontalGlue())
        }
        val more = iconButton(VectorIcon(Icons.Outlined.MoreVert, 18) { Theme.subtext }, "Gruppe bearbeiten") {}
        more.addActionListener { menu().show(more, 0, more.height) }
        row.add(more)
        row.add(JLabel(VectorIcon(if (collapsed) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.Outlined.ExpandMore, 20) { Theme.subtext }))
        row.add(Box.createHorizontalStrut(6))
        row.onMouse(
            click = { state.toggleGroup(group) },
            popup = { menu() },
            hover = { h -> row.fill = { if (h) Theme.navHover else null }; row.repaint() },
        )
        return wrapRow(row)
    }

    private fun accountMenu(): JPopupMenu = JPopupMenu().apply {
        add(javax.swing.JMenuItem("Jetzt synchronisieren", VectorIcon(Icons.Outlined.Sync, 18) { Theme.subtext }).apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_R, java.awt.event.InputEvent.CTRL_DOWN_MASK)
            addActionListener { state.syncNow() }
        })
        add(javax.swing.JMenuItem("Einstellungen", VectorIcon(Icons.Outlined.Settings, 18) { Theme.subtext }).apply {
            addActionListener { state.showSettings = true }
        })
        addSeparator()
        add(javax.swing.JMenuItem("Abmelden", VectorIcon(Icons.AutoMirrored.Outlined.Logout, 18) { Theme.subtext }).apply {
            addActionListener {
                if (confirm(frame, "Abmelden?", LOGOUT_TEXT, "Abmelden")) state.logout()
            }
        })
    }

    private fun createList(group: String?) {
        newListDialog(frame, group)?.let { (name, color) -> state.createList(name, color, group) }
    }

    private fun createGroup() {
        askText(
            frame, "Neue Gruppe", confirm = "Erstellen", placeholder = "Name der Gruppe",
            hintText = "Danach Listen per Rechtsklick → „In Gruppe verschieben“ hinzufügen.",
        )?.let(state::createGroup)
    }

    companion object {
        const val LOGOUT_TEXT = "Zugangsdaten, Offline-Daten und Einstellungen werden von diesem Computer gelöscht. Deine Aufgaben auf der Nextcloud bleiben erhalten."
    }
}

/** One entry of the menu: icon, name, number of open tasks. */
class NavRow(
    icon: ImageVector,
    tint: () -> Color,
    text: String,
    count: Int,
    private val selected: Boolean,
    indent: Int,
    /** Small symbol after the name (shared list), with tooltip. */
    status: Pair<ImageVector, String>? = null,
    onClick: () -> Unit,
    popup: (() -> JPopupMenu?)? = null,
) : JPanel(BorderLayout()) {
    private var hover = false
    private val inner = object : RoundPanel(8, { if (selected) Theme.navSelected else if (hover) Theme.navHover else null }) {}

    init {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        border = emptyBorder(1, 8 + indent, 1, 8)
        maximumSize = Dimension(Int.MAX_VALUE, Theme.px(38))
        preferredSize = Dimension(260, Theme.px(38))
        inner.layout = BoxLayout(inner, BoxLayout.X_AXIS)
        inner.border = emptyBorder(0, 12, 0, 12)
        inner.add(JLabel(VectorIcon(icon, 20, tint)))
        inner.add(Box.createHorizontalStrut(14))
        val title = uiLabel(text, 14f)
        title.minimumSize = Dimension(0, 0)
        inner.add(title)
        status?.let { (icon, tip) ->
            inner.add(Box.createHorizontalStrut(6))
            inner.add(JLabel(VectorIcon(icon, 15) { Theme.subtext }).apply { toolTipText = tip })
        }
        inner.add(Box.createHorizontalGlue())
        if (count > 0) inner.add(uiLabel(count.toString(), 12f) { Theme.subtext })
        add(inner)
        onMouse(click = { onClick() }, popup = popup, hover = { hover = it; inner.repaint() })
    }
}

private class Divider : JComponent() {
    init {
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, 13)
        preferredSize = Dimension(10, 13)
    }

    override fun paintComponent(g: Graphics) {
        g.color = Theme.divider
        g.fillRect(16, 6, width - 32, 1)
    }
}

private fun wrapRow(row: JComponent): JComponent = JPanel(BorderLayout()).apply {
    isOpaque = false
    alignmentX = Component.LEFT_ALIGNMENT
    border = emptyBorder(1, 8, 1, 8)
    maximumSize = Dimension(Int.MAX_VALUE, row.maximumSize.height + 2)
    add(row)
}
