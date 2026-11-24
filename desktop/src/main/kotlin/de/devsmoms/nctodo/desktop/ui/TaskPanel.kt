package de.devsmoms.nctodo.desktop.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Sync
import de.devsmoms.nctodo.data.TaskItem
import de.devsmoms.nctodo.desktop.AppState
import de.devsmoms.nctodo.desktop.ListIcons
import de.devsmoms.nctodo.desktop.Selection
import de.devsmoms.nctodo.desktop.label
import de.devsmoms.nctodo.desktop.searchTasks
import de.devsmoms.nctodo.desktop.stepsOf
import de.devsmoms.nctodo.desktop.tasksFor
import de.devsmoms.nctodo.sync.LocalData
import de.devsmoms.nctodo.sync.SyncStatus
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.event.KeyEvent
import java.awt.font.TextAttribute
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants

/** Middle column: title of the list, tasks, "Erledigt", field for new tasks. */
class TaskPanel(private val state: AppState, private val frame: MainFrame) : JPanel(BorderLayout()) {
    private val titleIcon = JLabel()
    private val title = uiLabel("", 26f, bold = true)
    private val offline = JLabel(VectorIcon(Icons.Outlined.CloudOff, 20) { Theme.subtext }).apply { toolTipText = "Offline – Änderungen werden später gesendet" }
    private val rows = VerticalList()
    private val addField = JTextField()
    private var accent: Color = Theme.blue
    private var showDone = false
    private var doneFor: Selection? = null

    init {
        border = emptyBorder(0, 28, 16, 28)
        title.font = Theme.heading(26f)
        val sync = iconButton(VectorIcon(Icons.Outlined.Sync, 20) { Theme.subtext }, "Synchronisieren (Strg+R)") { state.syncNow() }
        val head = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = emptyBorder(22, 0, 14, 0)
            add(titleIcon)
            add(Box.createHorizontalStrut(10))
            add(title)
            add(Box.createHorizontalGlue())
            add(offline)
            add(Box.createHorizontalStrut(6))
            add(sync)
        }
        add(head, BorderLayout.NORTH)
        add(scroller(rows), BorderLayout.CENTER)

        addField.apply {
            putClientProperty("JTextField.placeholderText", "Aufgabe hinzufügen   (Strg+N)")
            putClientProperty("JTextField.leadingIcon", VectorIcon(Icons.Outlined.Add, 20) { accent })
            putClientProperty("FlatLaf.style", "borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; margin: 12,10,12,10")
            addActionListener {
                if (text.isNotBlank()) {
                    state.addTask(text)
                    text = ""
                }
            }
            registerKeyboardAction({ text = ""; this@TaskPanel.requestFocusInWindow() }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED)
        }
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            border = emptyBorder(10, 0, 0, 0)
            add(addField)
        }, BorderLayout.SOUTH)
    }

    override fun paintComponent(g: Graphics) {
        g.color = Theme.background
        g.fillRect(0, 0, width, height)
    }

    fun focusAdd() {
        if (addField.isShowing) addField.requestFocusInWindow()
    }

    fun refresh(data: LocalData, status: SyncStatus) {
        val sel = state.selection
        if (sel != doneFor) {
            doneFor = sel
            showDone = false
        }
        val searching = state.search.isNotBlank()
        val list = (sel as? Selection.OfList)?.let { data.list(it.url) }
        title.text = when {
            searching -> "Suche: „${state.search.trim()}“"
            list != null -> list.label()
            sel is Selection.Smart -> sel.kind.title
            else -> "Liste"
        }
        accent = when {
            searching -> Theme.accent
            list != null -> parseColor(list.color)
            sel is Selection.Smart -> sel.kind.color()
            else -> Theme.blue
        }
        val a = accent
        title.tint = { a }
        val headIcon = when {
            searching -> null
            list != null -> ListIcons.iconOf(list.name)
            sel is Selection.Smart -> sel.kind.icon()
            else -> null
        }
        titleIcon.icon = headIcon?.let { VectorIcon(it, 26, a) }
        titleIcon.isVisible = headIcon != null
        offline.isVisible = status.offline
        addField.isVisible = !searching && (list?.canWrite ?: (sel is Selection.Smart))
        addField.background = Theme.field

        val tasks = if (searching) searchTasks(state.search, data, state.hidden) else tasksFor(sel, data, state.hidden)
        val open = tasks.filter { !it.todo.completed }
        val done = tasks.filter { it.todo.completed }
        val showList = sel is Selection.Smart || searching
        rows.removeAll()
        open.forEach { rows.add(TaskRow(state, frame, data, it, a, showList)) }
        if (done.isNotEmpty()) {
            val header = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
                border = emptyBorder(10, 0, 8, 0)
                maximumSize = Dimension(Int.MAX_VALUE, 44)
                add(JLabel(VectorIcon(if (showDone) Icons.Outlined.ExpandMore else Icons.AutoMirrored.Outlined.KeyboardArrowRight, 20) { Theme.text }))
                add(uiLabel("Erledigt (${done.size})", 14f, bold = true))
            }
            header.onMouse(click = { showDone = !showDone; refresh(data, status) })
            rows.add(header)
            if (showDone) done.forEach { rows.add(TaskRow(state, frame, data, it, a, showList)) }
        }
        if (tasks.isEmpty()) {
            rows.add(uiLabel(if (searching) "Nichts gefunden." else "Keine Aufgaben.", 14f) { Theme.subtext }.apply {
                alignmentX = Component.LEFT_ALIGNMENT
                border = emptyBorder(12, 4, 0, 0)
            })
        }
        rows.add(Box.createVerticalGlue())
        rows.revalidate()
        rows.repaint()
    }
}

/** One task: check circle, title with details and symbols, star. */
private class TaskRow(
    private val state: AppState,
    frame: MainFrame,
    data: LocalData,
    private val item: TaskItem,
    accent: Color,
    showList: Boolean,
) : JPanel(BorderLayout()) {
    private var hover = false

    init {
        val t = item.todo
        val writable = data.list(item.listUrl)?.canWrite == true
        val selected = state.selectedTask == item.url
        val steps = stepsOf(item, data)

        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        border = emptyBorder(0, 0, 4, 0)
        val card = RoundPanel(8) { if (selected) Theme.cardSelected else if (hover) Theme.cardHover else Theme.card }
        card.layout = BorderLayout()
        card.border = emptyBorder(6, 6, 6, 8)

        val check = iconButton(
            VectorIcon(if (t.completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, 22, accent),
            if (t.completed) "Als nicht erledigt markieren" else "Als erledigt markieren",
        ) { state.toggleDone(item) }
        check.isEnabled = writable
        card.add(check, BorderLayout.WEST)

        // All rows have the same height; without details the title is centred vertically.
        val text = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = emptyBorder(0, 8, 0, 8)
            add(Box.createVerticalGlue())
        }
        val titleLabel = uiLabel(t.summary.ifBlank { "(ohne Titel)" }, 14f) { if (t.completed) Theme.subtext else Theme.text }
        if (t.completed) titleLabel.font = titleLabel.font.deriveFont(mapOf(TextAttribute.STRIKETHROUGH to TextAttribute.STRIKETHROUGH_ON))
        text.add(titleLabel)

        val meta = buildList {
            if (showList) data.list(item.listUrl)?.let { add(it.label()) }
            if (steps.isNotEmpty()) add("${steps.count { it.todo.completed }} von ${steps.size}")
            t.due?.let { add(formatDate(it)) }
        }
        val badges = buildList {
            if (t.recurring) add(Icons.Outlined.Repeat to "Wiederholt sich")
            if (t.links.isNotEmpty()) add(Icons.Outlined.Link to if (t.links.size == 1) "1 Link" else "${t.links.size} Links")
            if (t.description.isNotBlank()) add(Icons.AutoMirrored.Outlined.StickyNote2 to "Notiz")
        }
        if (meta.isNotEmpty() || badges.isNotEmpty()) {
            val overdue = t.due?.let { it.isBefore(LocalDate.now()) } == true && !t.completed
            val line = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { isOpaque = false }
            if (meta.isNotEmpty()) line.add(uiLabel(meta.joinToString(" · "), 12f) { if (overdue) Theme.error else Theme.subtext })
            badges.forEachIndexed { i, (icon, tip) ->
                if (i == 0 && meta.isNotEmpty()) line.add(uiLabel("  ·  ", 12f) { Theme.subtext })
                line.add(JLabel(VectorIcon(icon, 14) { Theme.subtext }).apply { toolTipText = tip; border = emptyBorder(0, 0, 0, 6) })
            }
            line.alignmentX = Component.LEFT_ALIGNMENT
            titleLabel.alignmentX = Component.LEFT_ALIGNMENT
            text.add(Box.createVerticalStrut(2))
            text.add(line)
        }
        text.add(Box.createVerticalGlue())
        card.add(text, BorderLayout.CENTER)

        val star = iconButton(
            VectorIcon(if (t.starred) Icons.Outlined.Star else Icons.Outlined.StarBorder, 20) { if (t.starred) accent else Theme.subtext },
            if (t.starred) "Wichtig entfernen" else "Als wichtig markieren",
        ) { state.toggleStar(item) }
        star.isEnabled = writable
        card.add(star, BorderLayout.EAST)
        add(card)
        val h = Theme.px(56) + 4
        preferredSize = Dimension(100, h)
        maximumSize = Dimension(Int.MAX_VALUE, h)

        onMouse(
            click = {
                state.selectedTask = item.url
                // Takes the focus out of text fields, so "Entf" deletes the selected task.
                frame.rootPane.requestFocusInWindow()
            },
            popup = {
                if (!writable) null else popupMenu(
                    (if (t.completed) "Als nicht erledigt markieren" else "Als erledigt markieren") to { state.toggleDone(item) },
                    (if (t.starred) "Wichtig entfernen" else "Als wichtig markieren") to { state.toggleStar(item) },
                    "Heute fällig" to { state.setDue(item, LocalDate.now()) },
                    "Fälligkeitsdatum …" to { pickDate(frame, t.due)?.let { state.setDue(item, it) } },
                    "Löschen (Entf)" to { state.delete(item) },
                )
            },
            hover = { hover = it; card.repaint() },
        )
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("EE, dd.MM.yyyy", Locale.GERMAN)

fun formatDate(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Heute"
        today.plusDays(1) -> "Morgen"
        today.minusDays(1) -> "Gestern"
        else -> dateFormat.format(d)
    }
}

