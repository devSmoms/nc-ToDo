package de.devsmoms.nctodo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import de.devsmoms.nctodo.data.ThemeMode
import de.devsmoms.nctodo.ui.AppRoot
import de.devsmoms.nctodo.ui.AppViewModel
import de.devsmoms.nctodo.ui.Selection
import de.devsmoms.nctodo.ui.theme.NcTodoTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleDeepLink(intent)
        setContent {
            val state by vm.state.collectAsState()
            val mode = state.settings.theme
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status/navigation bar icons follow the chosen theme, not only the system setting.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            NcTodoTheme(mode) {
                AppRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * Opens the account, list and task a widget or notification refers to.
     * Only values produced by [intent] are accepted; unknown accounts are ignored.
     */
    private fun handleDeepLink(intent: Intent?) {
        val account = intent?.getStringExtra(EXTRA_ACCOUNT)
        val selection = Selection.decode(intent?.getStringExtra(EXTRA_SELECTION))
        if (account == null && selection == null) return
        vm.openDeepLink(account, selection, intent?.getStringExtra(EXTRA_TASK))
    }

    companion object {
        private const val EXTRA_ACCOUNT = "account"
        private const val EXTRA_SELECTION = "selection"
        private const val EXTRA_TASK = "task"

        fun intent(context: Context, account: String?, selection: Selection? = null, taskUrl: String? = null): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply {
                    if (account != null) putExtra(EXTRA_ACCOUNT, account)
                    if (selection != null) putExtra(EXTRA_SELECTION, selection.encode())
                    if (taskUrl != null) putExtra(EXTRA_TASK, taskUrl)
                }
                // Unique data so the system does not merge the PendingIntents of different rows.
                .setData(
                    Uri.parse(
                        "nctodo://open/" + Uri.encode(account.orEmpty() + "|" + selection?.encode().orEmpty() + "|" + taskUrl.orEmpty()),
                    ),
                )
    }
}
