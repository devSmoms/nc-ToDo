package de.devsmoms.nctodo.desktop.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import de.devsmoms.nctodo.data.TaskItem
import de.devsmoms.nctodo.desktop.AppState
import de.devsmoms.nctodo.desktop.LinkInfo
import de.devsmoms.nctodo.desktop.LinkPreviews
import de.devsmoms.nctodo.desktop.label
import de.devsmoms.nctodo.desktop.stepsOf
import de.devsmoms.nctodo.ical.TodoMapper
import de.devsmoms.nctodo.sync.LocalData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Image
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.awt.font.TextAttribute
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** Right column: details of the selected task. Title and notes are saved shortly after typing stops. */
class DetailPanel(private val state: AppState, private val frame: MainFrame) : JPanel(BorderLayout()) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    private val listName = uiLabel("", 14f, bold = true)
    private val body = VerticalList()

    private val title = JTextField()
    private val notes = JTextArea(5, 20)
    private val newStep = JTextField()
    private val created = uiLabel("", 12f) { Theme.subtext }
    private val titleCheck = iconButton(VectorIcon(Icons.Outlined.RadioButtonUnchecked, 22) { Theme.subtext }, "Erledigt") { item?.let(state::toggleDone) }
    private val titleStar = iconButton(VectorIcon(Icons.Outlined.StarBorder, 20) { Theme.subtext }, "Wichtig") { item?.let(state::toggleStar) }
    private val steps = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false; alignmentX = Component.LEFT_ALIGNMENT }
    private val newStepRow: JComponent
    private val mainCard: JComponent
    private val dates = card()
    private val links = card()
    private val notesCard = card()

    private var item: TaskItem? = null
    private var setting = false
    private val previews = HashMap<String, LinkInfo>()
    private val loading = HashSet<String>()

    private val saveTitle = Timer(600) { item?.let { state.rename(it, title.text.replace("\n", " ")) } }.apply { isRepeats = false }
    private val saveNotes = Timer(800) { item?.let { state.setNotes(it, notes.text) } }.apply { isRepeats = false }

    init {
        border = emptyBorder(0, 0, 0, 0)
        val head = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = emptyBorder(14, 18, 6, 8)
            add(listName, BorderLayout.CENTER)
            add(iconButton(VectorIcon(Icons.Outlined.Close, 20) { Theme.subtext }, "Schließen") { flush(); state.selectedTask = null }, BorderLayout.EAST)
        }
        add(head, BorderLayout.NORTH)
        add(scroller(body), BorderLayout.CENTER)
        val delete = iconButton(VectorIcon(Icons.Outlined.Delete, 20) { Theme.subtext }, "Aufgabe löschen") { item?.let(state::delete) }
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            border = javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.divider),
                emptyBorder(6, 18, 6, 8),
            )
            add(created, BorderLayout.CENTER)
            add(delete, BorderLayout.EAST)
        }, BorderLayout.SOUTH)

        title.apply {
            font = Theme.font(17f, bold = true)
            putClientProperty("FlatLaf.style", "borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; margin: 2,4,2,4")
            isOpaque = false
            document.addDocumentListener(listener { if (!setting) saveTitle.restart() })
            addActionListener { flush() }
        }
        notes.apply {
            lineWrap = true
            wrapStyleWord = true
            font = Theme.font(14f)
            border = emptyBorder(8, 10, 8, 10)
            document.addDocumentListener(listener { if (!setting) saveNotes.restart() })
            addFocusListener(object : FocusAdapter() {
                override fun focusLost(e: FocusEvent?) = flush()
            })
        }
        newStep.apply {
            putClientProperty("JTextField.placeholderText", "Nächster Schritt")
            putClientProperty("FlatLaf.style", "borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; margin: 6,4,6,4")
            isOpaque = false
            addActionListener {
                val i = item
                if (i != null && text.isNotBlank()) {
                    state.addStep(i, text)
                    text = ""
                }
            }
            registerKeyboardAction({ text = "" }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED)
        }

        // Fixed structure: only the inner parts are rebuilt, so the text fields keep focus while syncing.
        body.border = emptyBorder(4, 14, 14, 14)
        newStepRow = row(null, newStep, null, indent = 8)
        mainCard = card().apply {
            add(row(titleCheck, title, titleStar))
            add(steps)
            add(newStepRow)
        }
        notesCard.layout = BorderLayout()
        notesCard.add(notes)
        section(mainCard)
        section(dates)
        section(links)
        body.add(uiLabel("Notiz", 12f) { Theme.subtext }.apply { alignmentX = Component.LEFT_ALIGNMENT; border = emptyBorder(0, 4, 4, 0) })
        section(notesCard)
        body.add(Box.createVerticalGlue())
    }

    override fun paintComponent(g: Graphics) {
        g.color = Theme.sidebar
        g.fillRect(0, 0, width, height)
    }

    /** Saves pending typing right away (before switching tasks or closing). */
    fun flush() {
        if (saveTitle.isRunning) { saveTitle.stop(); item?.let { state.rename(it, title.text.replace("\n", " ")) } }
        if (saveNotes.isRunning) { saveNotes.stop(); item?.let { state.setNotes(it, notes.text) } }
    }

    fun refresh(current: TaskItem, data: LocalData) {
        if (item?.url != current.url) {
            flush()
            item = current
            setText(title, current.todo.summary)
            setText(notes, current.todo.description)
            newStep.text = ""
        } else {
            item = current
            // Updates from sync only where the user is not typing.
            if (!title.isFocusOwner && !saveTitle.isRunning) setText(title, current.todo.summary)
            if (!notes.isFocusOwner && !saveNotes.isRunning) setText(notes, current.todo.description)
        }
        val t = current.todo
        val list = data.list(current.listUrl)
        val writable = list?.canWrite == true
        val accent = list?.let { parseColor(it.color) } ?: Theme.blue
        listName.text = list?.label().orEmpty()
        listName.tint = { accent }
        title.font = Theme.font(17f, bold = true)
        notes.font = Theme.font(14f)
        title.isEditable = writable
        notes.isEditable = writable
        newStep.putClientProperty("JTextField.leadingIcon", VectorIcon(Icons.Outlined.Add, 18, accent))
        created.text = t.created?.let { "Erstellt " + DateTimeFormatter.ofPattern("dd.MM.yyyy").format(it.atZone(ZoneId.systemDefault())) }.orEmpty()

        titleCheck.icon = VectorIcon(if (t.completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, 22, accent)
        titleCheck.isEnabled = writable
        titleStar.icon = VectorIcon(if (t.starred) Icons.Outlined.Star else Icons.Outlined.StarBorder, 20) { if (t.starred) accent else Theme.subtext }
        titleStar.isEnabled = writable
        title.background = Theme.card
        title.foreground = Theme.text
        newStep.background = Theme.card
        newStepRow.isVisible = writable
        notes.background = Theme.card
        notes.foreground = Theme.text

        // Steps
        steps.removeAll()
        stepsOf(current, data).forEach { step ->
            val text = uiLabel(step.todo.summary, 14f) { if (step.todo.completed) Theme.subtext else Theme.text }
            if (step.todo.completed) text.font = text.font.deriveFont(mapOf(TextAttribute.STRIKETHROUGH to TextAttribute.STRIKETHROUGH_ON))
            steps.add(row(
                iconButton(VectorIcon(if (step.todo.completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, 18, accent), "Erledigt") { state.toggleDone(step) }.apply { isEnabled = writable },
                text,
                if (writable) iconButton(VectorIcon(Icons.Outlined.Close, 16) { Theme.subtext }, "Schritt entfernen") { state.delete(step) } else null,
                indent = 6,
            ))
        }

        // My Day, due date
        dates.removeAll()
        val today = LocalDate.now()
        val inMyDay = t.due?.let { !it.isAfter(today) } == true
        dates.add(actionRow(Icons.Outlined.WbSunny, if (inMyDay) "In „Mein Tag“" else "Zu „Mein Tag“ hinzufügen", if (inMyDay) accent else null, writable) {
            state.setDue(current, if (inMyDay) null else today)
        })
        dates.add(line())
        dates.add(actionRow(
            Icons.Outlined.CalendarMonth,
            t.due?.let { "Fällig: " + formatDate(it) } ?: "Fälligkeitsdatum hinzufügen",
            if (t.due != null) accent else null, writable,
            onClear = if (writable && t.due != null) ({ state.setDue(current, null) }) else null,
        ) { pickDate(frame, t.due)?.let { state.setDue(current, it) } })

        // Links
        links.removeAll()
        t.links.forEachIndexed { i, url ->
            if (i > 0) links.add(line())
            links.add(linkRow(current, url, writable))
        }
        if (writable) {
            if (t.links.isNotEmpty()) links.add(line())
            links.add(actionRow(Icons.Outlined.Link, "Link hinzufügen", accent, true) {
                askText(
                    frame, "Link hinzufügen", confirm = "Hinzufügen", placeholder = "https://…", maxLength = 2000,
                    hintText = "Der Link wird in der Aufgabe gespeichert und ist auch in der Android-App sichtbar.",
                )?.let { state.addLink(current, if ("://" in it) it else "https://$it") }
            })
        }
        links.isVisible = t.links.isNotEmpty() || writable

        listOf(mainCard, dates, links, notesCard).forEach { it.maximumSize = Dimension(Int.MAX_VALUE, it.preferredSize.height) }
        body.revalidate()
        body.repaint()
    }

    private fun setText(field: javax.swing.text.JTextComponent, value: String) {
        if (field.text == value) return
        setting = true
        field.text = value
        field.caretPosition = 0
        setting = false
    }

    private fun card() = RoundPanel(10) { Theme.card }.apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = emptyBorder(4, 4, 4, 4)
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun section(c: JComponent) {
        body.add(c)
        body.add(Box.createVerticalStrut(12))
    }

    private fun row(left: JComponent?, center: JComponent, right: JComponent?, indent: Int = 0) = JPanel(BorderLayout()).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        border = emptyBorder(0, indent, 0, 0)
        left?.let { add(it, BorderLayout.WEST) }
        add(center, BorderLayout.CENTER)
        right?.let { add(it, BorderLayout.EAST) }
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    private fun line() = object : JComponent() {
        init {
            alignmentX = Component.LEFT_ALIGNMENT
            preferredSize = Dimension(10, 1)
            maximumSize = Dimension(Int.MAX_VALUE, 1)
        }

        override fun paintComponent(g: Graphics) {
            g.color = Theme.divider
            g.fillRect(0, 0, width, 1)
        }
    }

    private fun actionRow(icon: ImageVector, text: String, highlight: Color?, enabled: Boolean, onClear: (() -> Unit)? = null, onClick: () -> Unit): JComponent {
        val r = RoundPanel(8) { null }.apply {
            layout = BorderLayout()
            alignmentX = Component.LEFT_ALIGNMENT
            border = emptyBorder(10, 12, 10, 8)
            add(JLabel(text, VectorIcon(icon, 20) { highlight ?: Theme.subtext }, JLabel.LEFT).apply {
                iconTextGap = 14
                font = Theme.font(14f)
                foreground = highlight ?: Theme.text
            }, BorderLayout.CENTER)
            onClear?.let { add(iconButton(VectorIcon(Icons.Outlined.Close, 16) { Theme.subtext }, "Entfernen", it), BorderLayout.EAST) }
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
        if (enabled) r.onMouse(click = { onClick() }, hover = { h -> r.fill = { if (h) Theme.cardHover else null }; r.repaint() })
        return r
    }

    private fun linkRow(current: TaskItem, url: String, writable: Boolean): JComponent {
        val info = previews[url] ?: LinkInfo(null, LinkPreviews.siteOf(url), null)
        if (state.settings.linkPreviews && url !in previews && loading.add(url)) {
            scope.launch {
                val loaded = LinkPreviews.load(url)
                previews[url] = loaded
                loading.remove(url)
                item?.let { if (it.url == current.url) frame.refresh() }
            }
        }
        val web = TodoMapper.isWebLink(url)
        val thumb = RoundPanel(6) { Theme.field }.apply {
            layout = BorderLayout()
            preferredSize = Dimension(44, 44)
            val img = info.image
            add(
                if (img != null) JLabel(ImageIcon(img.getScaledInstance(44, 44, Image.SCALE_SMOOTH)))
                else JLabel(VectorIcon(Icons.Outlined.Link, 20) { Theme.subtext }, JLabel.CENTER),
            )
        }
        val texts = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = emptyBorder(0, 12, 0, 8)
            add(JLabel("<html><div style='width:200px'>${(info.title ?: url).take(150).escapeHtml()}</div></html>").apply { font = Theme.font(14f, bold = true); foreground = Theme.text })
            add(uiLabel(info.site, 12f) { Theme.subtext })
        }
        val r = RoundPanel(8) { null }.apply {
            layout = BorderLayout()
            alignmentX = Component.LEFT_ALIGNMENT
            border = emptyBorder(8, 10, 8, 4)
            add(thumb, BorderLayout.WEST)
            add(texts, BorderLayout.CENTER)
            val right = JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                if (web) add(JLabel(VectorIcon(Icons.AutoMirrored.Outlined.OpenInNew, 16) { Theme.subtext }))
                if (writable) add(iconButton(VectorIcon(Icons.Outlined.Close, 16) { Theme.subtext }, "Link entfernen") {
                    if (!state.settings.confirmRemovals || confirm(frame, "Link entfernen?", url.escapeHtml(), "Entfernen")) state.removeLink(current, url)
                })
            }
            add(right, BorderLayout.EAST)
            toolTipText = url
        }
        if (web) r.onMouse(click = { LinkPreviews.open(url) }, hover = { h -> r.fill = { if (h) Theme.cardHover else null }; r.repaint() })
        return r
    }
}

private fun String.escapeHtml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun listener(onChange: () -> Unit) = object : DocumentListener {
    override fun insertUpdate(e: DocumentEvent?) = onChange()
    override fun removeUpdate(e: DocumentEvent?) = onChange()
    override fun changedUpdate(e: DocumentEvent?) = onChange()
}
