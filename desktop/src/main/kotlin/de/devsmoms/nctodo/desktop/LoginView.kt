package de.devsmoms.nctodo.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Login: browser (Login Flow v2) by default, username + app password as alternative. */
@Composable
fun LoginView(state: AppState) {
    var server by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val busy = state.loginBusy
    val flowUrl = state.loginFlowUrl

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 440.dp).padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("NC Todo", style = MaterialTheme.typography.headlineLarge)
            Text("Mit deiner Nextcloud verbinden.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = server, onValueChange = { server = it },
                label = { Text("Adresse deiner Nextcloud") }, placeholder = { Text("cloud.example.de") },
                singleLine = true, enabled = flowUrl == null, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (!busy && !manual) state.startBrowserLogin(server) }),
            )
            when {
                flowUrl != null -> Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(14.dp))
                            Text("Warte auf die Anmeldung im Browser …", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "Melde dich im Browser bei deiner Nextcloud an und klicke auf „Zugriff gewähren“. " +
                                "Danach geht es hier automatisch weiter.",
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { AppState.openInBrowser(flowUrl) }) { Text("Erneut öffnen") }
                            TextButton(onClick = state::cancelBrowserLogin) { Text("Abbrechen") }
                        }
                    }
                }
                !manual -> {
                    Button(onClick = { state.startBrowserLogin(server) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
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
                            "NC Todo bekommt dabei automatisch ein eigenes App-Passwort.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.loginError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    HorizontalDivider()
                    TextButton(onClick = { manual = true }, enabled = !busy) { Text("Stattdessen mit App-Passwort anmelden") }
                }
                else -> {
                    OutlinedTextField(
                        value = user, onValueChange = { user = it }, label = { Text("Benutzername") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, label = { Text("App-Passwort") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (!busy) state.login(server, user, password) }),
                    )
                    state.loginError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { state.login(server, user, password) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Anmelden")
                    }
                    TextButton(onClick = { manual = false }, enabled = !busy) { Text("Zurück zur Anmeldung im Browser") }
                }
            }
        }
    }
}
