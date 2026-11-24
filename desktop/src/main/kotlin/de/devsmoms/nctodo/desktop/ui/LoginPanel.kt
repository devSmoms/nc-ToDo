package de.devsmoms.nctodo.desktop.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import de.devsmoms.nctodo.desktop.AppState
import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JProgressBar
import javax.swing.JSeparator
import javax.swing.JTextField

/** Login: in the browser (Login Flow v2) or manually with an app password. */
class LoginPanel(private val state: AppState) : JPanel(GridBagLayout()) {
    private val column = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }
    private var manual = false

    private val server = JTextField().apply {
        putClientProperty("JTextField.placeholderText", "cloud.example.de")
        addActionListener { if (!state.loginBusy && !manual) state.startBrowserLogin(text) }
    }
    private val user = JTextField().apply { putClientProperty("JTextField.placeholderText", "Benutzername") }
    private val password = JPasswordField().apply {
        putClientProperty("JTextField.placeholderText", "App-Passwort")
        addActionListener { if (!state.loginBusy) state.login(server.text, user.text, String(this.password)) }
    }

    init {
        add(column)
    }

    fun refresh() {
        column.removeAll()
        fun add(c: java.awt.Component, gap: Int = 12) {
            (c as? javax.swing.JComponent)?.alignmentX = Component.LEFT_ALIGNMENT
            if (c is JTextField || c is JButton || c is JSeparator) c.maximumSize = Dimension(Int.MAX_VALUE, c.preferredSize.height)
            column.add(c)
            column.add(Box.createVerticalStrut(gap))
        }
        add(uiLabel("NC Todo", 28f, bold = true), 4)
        add(uiLabel("Mit deiner Nextcloud verbinden.", 14f) { Theme.subtext }, 20)
        add(uiLabel("Adresse deiner Nextcloud", 12f) { Theme.subtext }, 4)
        server.isEnabled = state.loginFlowUrl == null
        add(server, 16)

        val flowUrl = state.loginFlowUrl
        when {
            flowUrl != null -> {
                add(JProgressBar().apply { isIndeterminate = true }, 12)
                add(uiLabel("Warte auf die Anmeldung im Browser …", 14f, bold = true), 6)
                add(wrapped("Melde dich im Browser bei deiner Nextcloud an und klicke auf „Zugriff gewähren“. Danach geht es hier automatisch weiter."), 12)
                val row = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.X_AXIS)
                    add(JButton("Erneut öffnen").apply { addActionListener { AppState.openInBrowser(flowUrl) } })
                    add(Box.createHorizontalStrut(8))
                    add(JButton("Abbrechen").apply { addActionListener { state.cancelBrowserLogin() } })
                }
                add(row)
            }
            !manual -> {
                add(
                    JButton(if (state.loginBusy) "Verbinde …" else "Im Browser anmelden", VectorIcon(Icons.Outlined.OpenInBrowser, 18) { java.awt.Color.WHITE }).apply {
                        putClientProperty("JButton.buttonType", "default")
                        isEnabled = !state.loginBusy
                        addActionListener { state.startBrowserLogin(server.text) }
                    },
                    8,
                )
                add(wrapped("Du meldest dich auf der Seite deiner Nextcloud an – auch mit Zwei-Faktor oder Single Sign-On. NC Todo bekommt dabei automatisch ein eigenes App-Passwort.", 12f), 12)
                state.loginError?.let { add(wrapped(it, color = Theme.error)) }
                add(JSeparator())
                add(JButton("Stattdessen mit App-Passwort anmelden").apply {
                    putClientProperty("JButton.buttonType", "borderless")
                    isEnabled = !state.loginBusy
                    addActionListener { manual = true; refresh() }
                })
            }
            else -> {
                add(user, 8)
                add(password, 12)
                state.loginError?.let { add(wrapped(it, color = Theme.error)) }
                add(JButton(if (state.loginBusy) "Verbinde …" else "Anmelden").apply {
                    putClientProperty("JButton.buttonType", "default")
                    isEnabled = !state.loginBusy
                    addActionListener { state.login(server.text, user.text, String(password.password)) }
                })
                add(JButton("Zurück zur Anmeldung im Browser").apply {
                    putClientProperty("JButton.buttonType", "borderless")
                    isEnabled = !state.loginBusy
                    addActionListener { manual = false; refresh() }
                })
            }
        }
        column.revalidate()
        column.repaint()
    }

    private fun wrapped(text: String, size: Float = 14f, color: java.awt.Color = Theme.text) =
        javax.swing.JLabel("<html><div style='width:400px'>${text.replace("<", "&lt;")}</div></html>").apply {
            font = Theme.font(size)
            foreground = color
        }
}
