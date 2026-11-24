package de.devsmoms.nctodo.desktop.ui

import com.formdev.flatlaf.FlatLaf
import de.devsmoms.nctodo.desktop.AppState
import de.devsmoms.nctodo.desktop.ThemeMode
import de.devsmoms.nctodo.desktop.UiMessage
import de.devsmoms.nctodo.desktop.forDisplay
import de.devsmoms.nctodo.sync.LocalData
import de.devsmoms.nctodo.sync.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.Timer

/** The window: loading, login or the three columns (menu, tasks, details). */
class MainFrame(private val state: AppState, private val scope: CoroutineScope) : JFrame("NC Todo") {
    private val cards = CardLayout()
    private val root = JPanel(cards)
    private val login = LoginPanel(state)
    private val sidebar = Sidebar(state, this)
    private val tasks = TaskPanel(state, this)
    private val detail = DetailPanel(state, this)
    private val settings = SettingsPanel(state, this)
    private val content = JPanel(CardLayout())
    private val taskArea = JPanel(BorderLayout())
    private val detailWrap = JPanel(BorderLayout())
    private val toast = Toast()

    private var watchedRepo: TaskRepository? = null
    private var dataJob: Job? = null
    private var themeMode: ThemeMode = state.settings.theme
    private var fontSize: Int = state.settings.fontSize
    private val watcher = StateWatcher(::refresh)

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        minimumSize = Dimension(760, 480)
        size = Dimension(1200, 800)
        setLocationRelativeTo(null)
        javaClass.classLoader.getResource("icon.png")?.let { iconImage = javax.imageio.ImageIO.read(it) }

        root.add(JLabel("Lädt …", SwingConstants.CENTER), "loading")
        root.add(login, "login")

        detailWrap.add(JSeparator(SwingConstants.VERTICAL), BorderLayout.WEST)
        detailWrap.add(detail, BorderLayout.CENTER)
        detailWrap.preferredSize = Dimension(380, 0)
        taskArea.add(tasks, BorderLayout.CENTER)
        taskArea.add(detailWrap, BorderLayout.EAST)
        content.add(taskArea, "tasks")
        content.add(settings, "settings")

        val main = JPanel(BorderLayout())
        main.add(sidebar, BorderLayout.WEST)
        main.add(content, BorderLayout.CENTER)
        root.add(main, "main")
        contentPane = root

        layeredPane.add(toast, JLayeredPane.POPUP_LAYER)
        addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent?) = toast.place(layeredPane)
        })

        shortcut(KeyEvent.VK_N) { if (state.credentials != null) { state.showSettings = false; tasks.focusAdd() } }
        shortcut(KeyEvent.VK_F) { if (state.credentials != null) sidebar.focusSearch() }
        shortcut(KeyEvent.VK_R) { state.syncNow() }
        // "Entf" deletes the selected task – unless the user is typing somewhere.
        rootPane.registerKeyboardAction({
            val typing = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner is javax.swing.text.JTextComponent
            val repo = state.repository
            val item = state.selectedTask?.let { repo?.data?.value?.task(it) }
            val writable = item?.let { repo?.data?.value?.list(it.listUrl)?.canWrite } == true
            if (!typing && item != null && writable && !state.showSettings) state.delete(item)
        }, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW)

        scope.launch { state.messages.collect { toast.show(it, layeredPane) } }
        refresh()
    }

    private fun shortcut(key: Int, action: () -> Unit) {
        rootPane.registerKeyboardAction({ action() }, KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW)
    }

    /** Re-reads the state and updates what is visible. Called (coalesced) after every change. */
    fun refresh() {
        if (state.settings.theme != themeMode || state.settings.fontSize != fontSize) {
            themeMode = state.settings.theme
            fontSize = state.settings.fontSize
            Theme.install(themeMode, fontSize)
            FlatLaf.updateUI()
        }
        watchRepository()
        val card = when {
            !state.ready -> "loading"
            state.credentials == null -> "login"
            else -> "main"
        }
        (root.layout as CardLayout).show(root, card)
        when (card) {
            "login" -> login.refresh()
            "main" -> {
                val repo = state.repository ?: return
                val data: LocalData = repo.data.value.forDisplay(state.settings.hideDeckLists)
                sidebar.refresh(data)
                if (state.showSettings) {
                    (content.layout as CardLayout).show(content, "settings")
                    settings.refresh(repo.status.value)
                } else {
                    (content.layout as CardLayout).show(content, "tasks")
                    tasks.refresh(data, repo.status.value)
                    val selected = state.selectedTask?.let { data.task(it) }
                    detailWrap.isVisible = selected != null
                    if (selected != null) detail.refresh(selected, data)
                }
            }
        }
        root.revalidate()
        root.repaint()
    }

    /** Data and sync status of the repository are flows; watch them like the snapshot state. */
    private fun watchRepository() {
        val repo = state.repository
        if (repo === watchedRepo) return
        watchedRepo = repo
        dataJob?.cancel()
        if (repo != null) {
            dataJob = scope.launch {
                launch { repo.data.collect { watcher.schedule() } }
                launch { repo.status.collect { watcher.schedule() } }
            }
        }
    }
}

/** Message at the bottom of the window, optionally with "Rückgängig". */
class Toast : RoundPanel(10, { if (Theme.dark) java.awt.Color(0xE6E6E6) else java.awt.Color(0x2B2B2B) }) {
    private val text = JLabel()
    private val action = JButton("Rückgängig")
    private var current: UiMessage? = null
    private var undone = false
    private val hideTimer = Timer(5000) { finish() }.apply { isRepeats = false }

    init {
        layout = FlowLayout(FlowLayout.LEFT, 12, 8)
        add(text)
        add(action)
        action.putClientProperty("JButton.buttonType", "borderless")
        action.addActionListener {
            undone = true
            current?.undo?.invoke()
            finish()
        }
        isVisible = false
    }

    fun show(m: UiMessage, layer: JLayeredPane) {
        finish()
        current = m
        undone = false
        text.text = m.text
        text.foreground = if (Theme.dark) java.awt.Color(0x111111) else java.awt.Color.WHITE
        text.font = Theme.font(14f)
        action.foreground = if (Theme.dark) Theme.blue else java.awt.Color(0x8AB4F8)
        action.isVisible = m.undo != null
        isVisible = true
        place(layer)
        hideTimer.restart()
    }

    private fun finish() {
        hideTimer.stop()
        val m = current ?: return
        current = null
        isVisible = false
        if (!undone) m.onTimeout?.invoke()
    }

    fun place(layer: JLayeredPane) {
        if (!isVisible) return
        val d = preferredSize
        setBounds((layer.width - d.width) / 2, layer.height - d.height - 24, d.width, d.height)
        revalidate()
        repaint()
    }
}
