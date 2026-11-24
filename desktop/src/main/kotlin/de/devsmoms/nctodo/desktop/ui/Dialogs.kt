package de.devsmoms.nctodo.desktop.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import de.devsmoms.nctodo.data.CalDavRepository
import de.devsmoms.nctodo.data.ListIcon
import de.devsmoms.nctodo.desktop.ListIcons
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.RenderingHints
import java.awt.Window
import java.awt.event.KeyEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/** Small modal dialog: content plus buttons on the right, Enter = OK, Esc = cancel. */
private class SimpleDialog(owner: Component?, title: String) : JDialog(windowOf(owner), title, ModalityType.APPLICATION_MODAL) {
    val content = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = emptyBorder(20, 24, 8, 24)
    }
    private val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply { border = emptyBorder(8, 24, 16, 16) }

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        contentPane.layout = BorderLayout()
        contentPane.add(content, BorderLayout.CENTER)
        contentPane.add(buttons, BorderLayout.SOUTH)
        rootPane.registerKeyboardAction({ dispose() }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW)
    }

    fun button(text: String, default: Boolean = false, danger: Boolean = false, action: () -> Unit): JButton =
        JButton(text).also { b ->
            b.addActionListener { action() }
            if (danger) b.foreground = Theme.error
            buttons.add(b)
            if (default) rootPane.defaultButton = b
        }

    fun row(c: JComponent) {
        c.alignmentX = Component.LEFT_ALIGNMENT
        content.add(c)
    }

    fun gap(h: Int = 12) = content.add(Box.createVerticalStrut(h))

    fun showCentered() {
        pack()
        minimumSize = Dimension(380, size.height)
        setLocationRelativeTo(owner)
        isVisible = true
    }
}

private fun windowOf(c: Component?): Window? = c as? Window ?: c?.let { SwingUtilities.getWindowAncestor(it) }

private fun hint(text: String) = JLabel("<html><div style='width:320px'>$text</div></html>").apply {
    foreground = Theme.subtext
    font = Theme.font(12f)
}

/** Asks for a short text. Returns null when cancelled. */
fun askText(
    owner: Component?,
    title: String,
    initial: String = "",
    confirm: String = "OK",
    placeholder: String = "",
    hintText: String? = null,
    maxLength: Int = CalDavRepository.MAX_GROUP_LENGTH,
    allowEmpty: Boolean = false,
): String? {
    var result: String? = null
    val d = SimpleDialog(owner, title)
    val field = JTextField(initial, 28).apply { putClientProperty("JTextField.placeholderText", placeholder) }
    d.row(field)
    hintText?.let { d.gap(8); d.row(hint(it)) }
    d.button("Abbrechen") { d.dispose() }
    d.button(confirm, default = true) {
        val v = field.text.replace("\n", "").trim().take(maxLength)
        if (v.isNotEmpty() || allowEmpty) {
            result = v
            d.dispose()
        }
    }
    SwingUtilities.invokeLater { field.requestFocusInWindow(); field.selectAll() }
    d.showCentered()
    return result
}

fun confirm(owner: Component?, title: String, text: String, confirmText: String): Boolean {
    var ok = false
    val d = SimpleDialog(owner, title)
    d.row(hint(text))
    d.button("Abbrechen") { d.dispose() }
    d.button(confirmText, default = true, danger = true) { ok = true; d.dispose() }
    d.showCentered()
    return ok
}

/** New list: name, colour and icon (stored as emoji in the name, like on Android). Returns (name, colour). */
fun newListDialog(owner: Component?, group: String?): Pair<String, String>? {
    var result: Pair<String, String>? = null
    var color = CalDavRepository.COLORS.first()
    var emoji: String? = null
    val d = SimpleDialog(owner, if (group == null) "Neue Liste" else "Neue Liste in „$group“")
    val name = JTextField(28).apply { putClientProperty("JTextField.placeholderText", "Name") }
    d.row(name)
    d.gap(16)

    lateinit var refresh: () -> Unit
    val colors = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
    CalDavRepository.COLORS.forEach { c ->
        colors.add(object : JComponent() {
            init {
                preferredSize = Dimension(26, 26)
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                toolTipText = c
                onMouse(click = { color = c; refresh() })
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = parseColor(c)
                g2.fillOval(1, 1, 24, 24)
                if (c == color) {
                    g2.color = Color.WHITE
                    g2.fillOval(9, 9, 8, 8)
                }
            }
        })
    }
    d.row(colors)
    d.gap(12)

    val icons = JPanel(GridLayout(0, 10, 2, 2)).apply { isOpaque = false }
    val options = listOf<Pair<String?, androidx.compose.ui.graphics.vector.ImageVector>>(null to ListIcons.DEFAULT) + ListIcons.ALL
    options.forEach { (e, vector) ->
        val cell = RoundPanel(6) { if (e == emoji) parseColor(color).withAlpha(48) else null }.apply {
            layout = BorderLayout()
            preferredSize = Dimension(30, 30)
            add(JLabel(VectorIcon(vector, 18) { if (e == emoji) parseColor(color) else Theme.subtext }), BorderLayout.CENTER)
            toolTipText = if (e == null) "Kein Symbol" else null
        }
        cell.onMouse(click = { emoji = e; refresh() })
        icons.add(cell)
    }
    d.row(icons)
    refresh = { colors.repaint(); icons.repaint() }

    d.button("Abbrechen") { d.dispose() }
    d.button("Erstellen", default = true) {
        val title = name.text.trim()
        if (title.isNotEmpty()) {
            result = ListIcon.join(emoji, title) to color
            d.dispose()
        }
    }
    SwingUtilities.invokeLater { name.requestFocusInWindow() }
    d.showCentered()
    return result
}

/** Group of a list: none, an existing one or a new one. Returns Pair(changed, group). */
fun groupPicker(owner: Component?, listName: String, current: String?, groups: List<String>): Pair<Boolean, String?> {
    var result: Pair<Boolean, String?> = false to current
    val d = SimpleDialog(owner, "Gruppe für „$listName“")
    val bg = ButtonGroup()
    val radios = (listOf<String?>(null) + groups).map { g ->
        JRadioButton(g ?: "Keine Gruppe", g == current).also { bg.add(it); d.row(it) } to g
    }
    d.gap(8)
    val newName = JTextField(24).apply { putClientProperty("JTextField.placeholderText", "Neue Gruppe") }
    d.row(newName)
    d.button("Abbrechen") { d.dispose() }
    d.button("Verschieben", default = true) {
        val typed = newName.text.trim().take(CalDavRepository.MAX_GROUP_LENGTH).takeIf { it.isNotEmpty() }
        val chosen = typed ?: radios.firstOrNull { it.first.isSelected }?.second
        result = (chosen != current) to chosen
        d.dispose()
    }
    d.showCentered()
    return result
}

/** Calendar for the due date; returns the picked day or null when cancelled. */
fun pickDate(owner: Component?, initial: LocalDate?): LocalDate? {
    var result: LocalDate? = null
    var month = YearMonth.from(initial ?: LocalDate.now())
    val today = LocalDate.now()
    val d = SimpleDialog(owner, "Fälligkeitsdatum")
    val header = uiLabel("", 15f, bold = true)
    val grid = JPanel(GridLayout(0, 7, 2, 2)).apply { isOpaque = false }
    lateinit var render: () -> Unit
    val nav = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(iconButton(VectorIcon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, 20) { Theme.text }, "Vorheriger Monat") { month = month.minusMonths(1); render() }, BorderLayout.WEST)
        add(header.apply { horizontalAlignment = JLabel.CENTER }, BorderLayout.CENTER)
        add(iconButton(VectorIcon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, 20) { Theme.text }, "Nächster Monat") { month = month.plusMonths(1); render() }, BorderLayout.EAST)
    }
    render = {
        header.text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))
        grid.removeAll()
        DayOfWeek.values().forEach { dow ->
            grid.add(JLabel(dow.getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2), JLabel.CENTER).apply { foreground = Theme.subtext; font = Theme.font(12f) })
        }
        repeat(month.atDay(1).dayOfWeek.value - 1) { grid.add(JLabel()) }
        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val b = JButton(day.toString()).apply {
                putClientProperty("JButton.buttonType", "toolBarButton")
                isFocusable = false
                preferredSize = Dimension(38, 32)
                if (date == initial) { foreground = Theme.accent; font = Theme.font(14f, bold = true) }
                if (date == today && date != initial) font = Theme.font(14f, bold = true)
                addActionListener { result = date; d.dispose() }
            }
            grid.add(b)
        }
        grid.revalidate()
        grid.repaint()
        d.pack()
    }
    d.row(nav)
    d.gap(8)
    d.row(grid)
    val quick = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { isOpaque = false }
    listOf("Heute" to today, "Morgen" to today.plusDays(1), "Nächste Woche" to today.with(DayOfWeek.MONDAY).plusWeeks(1)).forEach { (t, date) ->
        quick.add(JButton(t).apply { addActionListener { result = date; d.dispose() } })
        quick.add(Box.createHorizontalStrut(6))
    }
    d.gap(12)
    d.row(quick)
    d.button("Abbrechen") { d.dispose() }
    render()
    d.showCentered()
    return result
}
