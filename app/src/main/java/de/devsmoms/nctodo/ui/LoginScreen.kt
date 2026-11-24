package de.devsmoms.nctodo.ui

import android.app.Activity
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Login form; with [onCancel] it is the "Konto hinzufügen" form shown on top of an existing account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(busy: Boolean, error: String?, onCancel: (() -> Unit)? = null, onLogin: (String, String, String) -> Unit) {
    // Block screenshots / screen recording / recents preview while credentials are on screen.
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    // The password is deliberately not saved in the instance state bundle.
    var password by androidx.compose.runtime.remember { mutableStateOf("") }
    var showPassword by androidx.compose.runtime.remember { mutableStateOf(false) }

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
                else "Mit deinem Nextcloud-Server per CalDAV verbinden.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = server, onValueChange = { server = it },
                label = { Text("Server") }, placeholder = { Text("https://cloud.example.de") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            )
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
                "Tipp: In Nextcloud unter Einstellungen → Sicherheit ein eigenes App-Passwort für diese App erstellen. " +
                    "Es lässt sich jederzeit widerrufen und ist bei Zwei-Faktor-Anmeldung Pflicht.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            Button(onClick = { onLogin(server, user, password) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(if (onCancel != null) "Konto hinzufügen" else "Anmelden")
            }
        }
    }
}
