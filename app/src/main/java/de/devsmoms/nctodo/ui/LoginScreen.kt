package de.devsmoms.nctodo.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Login: by default via the browser (Nextcloud Login Flow v2, works with 2FA and SSO and creates
 * an app password automatically); manual entry of username and app password stays available.
 * With [onCancel] it is the "Konto hinzufügen" form shown on top of an existing account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    busy: Boolean,
    error: String?,
    flowUrl: String?,
    onCancel: (() -> Unit)? = null,
    onBrowserLogin: (String) -> Unit,
    onCancelBrowserLogin: () -> Unit,
    onLogin: (String, String, String) -> Unit,
) {
    // Block screenshots / screen recording / recents preview while credentials are on screen.
    val context = LocalContext.current
    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var manual by rememberSaveable { mutableStateOf(false) }
    // The password is deliberately not saved in the instance state bundle.
    var password by androidx.compose.runtime.remember { mutableStateOf("") }
    var showPassword by androidx.compose.runtime.remember { mutableStateOf(false) }

    fun openBrowser(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    // Open the Nextcloud login page once as soon as the server handed it out (not again after rotation).
    var openedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(flowUrl) {
        if (flowUrl != null && flowUrl != openedUrl) {
            openedUrl = flowUrl
            openBrowser(flowUrl)
        }
    }

    Scaffold(
        topBar = {
            if (onCancel != null) {
                TopAppBar(
                    title = {},
                    navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, "Abbrechen") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (onCancel != null) "Konto hinzufügen" else "NC Todo", style = MaterialTheme.typography.headlineLarge)
            Text(
                if (onCancel != null) "Ein weiteres Nextcloud-Konto verbinden – auch auf einem anderen Server. Es bekommt eigene Listen und Einstellungen."
                else "Mit deiner Nextcloud verbinden.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = server, onValueChange = { server = it },
                label = { Text("Adresse deiner Nextcloud") }, placeholder = { Text("cloud.example.de") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = flowUrl == null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri, imeAction = if (manual) ImeAction.Next else ImeAction.Go, autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onGo = { if (!busy) onBrowserLogin(server) }),
            )

            if (flowUrl != null) {
                // Waiting for the user to grant access in the browser.
                Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(14.dp))
                            Text("Warte auf die Anmeldung im Browser …", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "Melde dich im Browser bei deiner Nextcloud an und tippe auf „Zugriff gewähren“. " +
                                "Danach geht es hier automatisch weiter.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { openBrowser(flowUrl) }) {
                                Icon(Icons.Outlined.OpenInBrowser, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Erneut öffnen")
                            }
                            TextButton(onClick = onCancelBrowserLogin) { Text("Abbrechen") }
                        }
                    }
                }
            } else if (!manual) {
                Button(onClick = { onBrowserLogin(server) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.OpenInBrowser, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Im Browser anmelden")
                    }
                }
                Text(
                    "Du meldest dich auf der Seite deiner Nextcloud an – auch mit Zwei-Faktor oder Single Sign-On. " +
                        "NC Todo bekommt dabei automatisch ein eigenes App-Passwort, dein Passwort sieht die App nie.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                HorizontalDivider()
                TextButton(onClick = { manual = true }, enabled = !busy) { Text("Stattdessen mit App-Passwort anmelden") }
            } else {
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text("Benutzername") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("App-Passwort") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { if (!busy) onLogin(server, user, password) }),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = "Passwort anzeigen")
                        }
                    },
                )
                Text(
                    "Ein App-Passwort erstellst du in Nextcloud unter Einstellungen → Sicherheit → Geräte & Sitzungen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = { onLogin(server, user, password) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(if (onCancel != null) "Konto hinzufügen" else "Anmelden")
                }
                TextButton(onClick = { manual = false }, enabled = !busy) { Text("Zurück zur Anmeldung im Browser") }
            }
        }
    }
}
