package de.devsmoms.nctodo.desktop.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import org.jetbrains.skia.PathVerb
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities
import javax.swing.Timer

// ---------------------------------------------------------------- state bridge

/**
 * The app state ([de.devsmoms.nctodo.desktop.AppState]) uses observable snapshot state.
 * This calls [onChange] on the Swing thread (coalesced) whenever any of it changes.
 */
class StateWatcher(private val onChange: () -> Unit) {
    private val pending = AtomicBoolean(false)

    init {
        Snapshot.registerApplyObserver { _, _ -> schedule() }
        // Without Compose UI nobody else publishes state writes made outside a snapshot.
        Timer(40) { Snapshot.sendApplyNotifications() }.start()
    }

    fun schedule() {
        if (pending.compareAndSet(false, true)) {
            SwingUtilities.invokeLater {
                pending.set(false)
                onChange()
            }
        }
    }
}

// ---------------------------------------------------------------- icons

/** Material icon (the same as in the Android app) drawn as vector shapes, sharp at any scale. */
class VectorIcon(private val vector: ImageVector, private val size: Int = 20, private val color: () -> Color) : Icon {
    constructor(vector: ImageVector, size: Int, color: Color) : this(vector, size, { color })

    private val shapes: List<Shape> = synchronized(cache) { cache.getOrPut(vector) { shapesOf(vector) } }

    override fun getIconWidth() = size
    override fun getIconHeight() = size

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g2.translate(x, y)
            g2.scale(size / vector.viewportWidth.toDouble(), size / vector.viewportHeight.toDouble())
            g2.color = if (c != null && !c.isEnabled) Theme.subtext else color()
            shapes.forEach(g2::fill)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        private val cache = HashMap<ImageVector, List<Shape>>()

        private fun shapesOf(v: ImageVector): List<Shape> = mutableListOf<Shape>().also { collect(v.root, it) }

        private fun collect(group: VectorGroup, out: MutableList<Shape>) {
            for (node in group) when (node) {
                is VectorGroup -> collect(node, out)
                is VectorPath -> out += toShape(node)
            }
        }

        // The path data (incl. arcs) is converted to curves by Compose/Skia and copied into Java2D.
        private fun toShape(p: VectorPath): Shape {
            val out = Path2D.Float(if (p.pathFillType == PathFillType.EvenOdd) Path2D.WIND_EVEN_ODD else Path2D.WIND_NON_ZERO)
            // Skia's iterator yields nullable segments.
            for (segment in p.pathData.toPath().asSkiaPath()) {
                val seg = segment ?: continue
                when (seg.verb) {
                    PathVerb.MOVE -> out.moveTo(seg.p0!!.x, seg.p0!!.y)
                    PathVerb.LINE -> out.lineTo(seg.p1!!.x, seg.p1!!.y)
                    PathVerb.QUAD -> out.quadTo(seg.p1!!.x, seg.p1!!.y, seg.p2!!.x, seg.p2!!.y)
                    // Conics do not occur in Material icons; approximated as a quad just in case.
                    PathVerb.CONIC -> out.quadTo(seg.p1!!.x, seg.p1!!.y, seg.p2!!.x, seg.p2!!.y)
                    PathVerb.CUBIC -> out.curveTo(seg.p1!!.x, seg.p1!!.y, seg.p2!!.x, seg.p2!!.y, seg.p3!!.x, seg.p3!!.y)
                    PathVerb.CLOSE -> out.closePath()
                    else -> Unit
                }
            }
            return out
        }
    }
}

// ---------------------------------------------------------------- building blocks

/** Scroll pane without frame (FlatLaf would draw one again after a theme change). */
fun scroller(view: java.awt.Component) = object : javax.swing.JScrollPane(view) {
    override fun updateUI() {
        super.updateUI()
        border = null
        viewportBorder = null
        isOpaque = false
        viewport?.isOpaque = false
    }
}.apply {
    horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    verticalScrollBar.unitIncrement = 16
}

/** Panel with a rounded background (no background when [fill] returns null). */
open class RoundPanel(private val arc: Int = 8, var fill: () -> Color? = { null }) : JPanel() {
    init {
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        fill()?.let { color ->
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), arc.toFloat(), arc.toFloat()))
            g2.dispose()
        }
        super.paintComponent(g)
    }
}

/** Round avatar with the first letter of a name. */
class Avatar(private val name: () -> String, private val size: Int = 40) : JComponent() {
    init {
        preferredSize = Dimension(size, size)
        minimumSize = preferredSize
        maximumSize = preferredSize
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = Theme.accent.withAlpha(if (Theme.dark) 70 else 46)
        g2.fillOval(0, 0, size - 1, size - 1)
        val letter = name().trim().take(1).uppercase().ifEmpty { "?" }
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.font = Theme.semibold(size * 0.4f / Theme.scale)
        g2.color = Theme.accent
        val fm = g2.fontMetrics
        g2.drawString(letter, (size - fm.stringWidth(letter)) / 2, (size - fm.height) / 2 + fm.ascent)
    }
}

/** Small on/off switch (Swing has none). */
class Switch(var checked: Boolean, private val onChange: (Boolean) -> Unit) : JComponent() {
    init {
        preferredSize = Dimension(36, 20)
        maximumSize = preferredSize
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = toggle()
        })
    }

    fun toggle() {
        checked = !checked
        repaint()
        onChange(checked)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val w = 36f
        val h = 20f
        if (checked) {
            g2.color = Theme.accent
            g2.fill(RoundRectangle2D.Float(0f, 0f, w, h, h, h))
            g2.color = if (Theme.dark) Color(0x1C1C1C) else Color.WHITE
            g2.fillOval(w.toInt() - 17, 3, 14, 14)
        } else {
            g2.color = Theme.subtext
            g2.stroke = BasicStroke(1.2f)
            g2.draw(RoundRectangle2D.Float(0.6f, 0.6f, w - 1.2f, h - 1.2f, h, h))
            g2.fillOval(5, 5, 10, 10)
        }
    }
}

fun Color.withAlpha(a: Int) = Color(red, green, blue, a)

/** Label whose colour follows the theme ([tint] is read on every paint). */
class ThemedLabel(text: String, private val size: Float, private val bold: Boolean, tint: () -> Color) : JLabel(text) {
    // JLabel's constructor already calls updateUI/getForeground, before this class is set up.
    private var ready = false
    var tint: () -> Color = tint

    init {
        ready = true
        font = Theme.font(size, bold)
    }

    override fun updateUI() {
        super.updateUI()
        if (ready) font = Theme.font(size, bold)
    }

    // Nullable: while JLabel is being constructed there is no colour yet.
    override fun getForeground(): Color? = if (ready) tint() else super.getForeground()
}

fun uiLabel(text: String, size: Float = 14f, bold: Boolean = false, color: () -> Color = { Theme.text }) = ThemedLabel(text, size, bold, color)

/** Vertical list for scroll panes: always as wide as the visible area, so long texts get cut with "…". */
class VerticalList : JPanel(), javax.swing.Scrollable {
    init {
        layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
        isOpaque = false
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(r: java.awt.Rectangle?, o: Int, d: Int) = 16
    override fun getScrollableBlockIncrement(r: java.awt.Rectangle, o: Int, d: Int) = r.height - 32
    override fun getScrollableTracksViewportWidth() = true
    override fun getScrollableTracksViewportHeight() = false
}

/** Borderless button showing only an icon, with a tooltip. */
fun iconButton(icon: Icon, tooltip: String, onClick: () -> Unit): JButton = JButton(icon).apply {
    toolTipText = tooltip
    putClientProperty("JButton.buttonType", "toolBarButton")
    isFocusable = false
    margin = java.awt.Insets(4, 4, 4, 4)
    addActionListener { onClick() }
}

/** Mouse helper: click, right click (context menu) and hover. */
fun JComponent.onMouse(
    click: ((MouseEvent) -> Unit)? = null,
    popup: (() -> JPopupMenu?)? = null,
    hover: ((Boolean) -> Unit)? = null,
) {
    val l = object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) = maybePopup(e)
        override fun mouseReleased(e: MouseEvent) = maybePopup(e)
        override fun mouseClicked(e: MouseEvent) {
            if (SwingUtilities.isLeftMouseButton(e)) click?.invoke(e)
        }
        override fun mouseEntered(e: MouseEvent) {
            hover?.invoke(true)
        }
        override fun mouseExited(e: MouseEvent) {
            // Leaving into a child component is not leaving the row.
            val p = SwingUtilities.convertPoint(e.component, e.point, this@onMouse)
            if (!this@onMouse.contains(p)) hover?.invoke(false)
        }
        private fun maybePopup(e: MouseEvent) {
            if (e.isPopupTrigger) {
                popup?.invoke()?.show(e.component, e.x, e.y)
                e.consume()
            }
        }
    }
    // Children (labels, icons) must forward the mouse to the row; buttons and switches act on their own.
    fun install(c: java.awt.Component) {
        if (c is JButton || c is Switch) return
        c.addMouseListener(l)
        if (c is java.awt.Container) c.components.forEach(::install)
    }
    install(this)
    if (click != null) cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
}

fun emptyBorder(top: Int, left: Int, bottom: Int, right: Int) = BorderFactory.createEmptyBorder(top, left, bottom, right)

fun popupMenu(vararg items: Pair<String, () -> Unit>): JPopupMenu = JPopupMenu().apply {
    items.forEach { (text, action) -> add(text).addActionListener { action() } }
}
