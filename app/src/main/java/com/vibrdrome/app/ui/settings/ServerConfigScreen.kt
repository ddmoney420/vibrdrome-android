package com.vibrdrome.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vibrdrome.app.network.SubsonicError
import com.vibrdrome.app.ui.AppState
import kotlinx.coroutines.launch

@Composable
fun ServerConfigScreen(
    appState: AppState,
    onSignedIn: () -> Unit,
) {
    val isConfigured by appState.isConfigured.collectAsState()
    val currentUrl by appState.serverURL.collectAsState()
    val currentUsername by appState.username.collectAsState()

    var url by remember { mutableStateOf(if (isConfigured) currentUrl else "") }
    var username by remember { mutableStateOf(if (isConfigured) currentUsername else "") }
    var password by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
    ) {
        Spacer(Modifier.height(48.dp))

        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Vibrdrome",
            style = MaterialTheme.typography.headlineLarge,
        )

        Text(
            text = "Connect to your Navidrome server",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Server URL") },
            placeholder = { Text("https://music.example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                appState.saveCredentials(url, username, password)
                if (appState.isConfigured.value) {
                    onSignedIn()
                } else {
                    testResult = "Invalid server URL. Please enter a valid URL."
                }
            },
            enabled = url.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign In")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                isTesting = true
                testResult = null
                scope.launch {
                    try {
                        // Temporarily configure and test
                        appState.configure(url, username, password)
                        val ok = appState.subsonicClient.ping()
                        testResult = if (ok) "Success! Connected to server." else "Server responded but ping failed."
                    } catch (e: Throwable) {
                        testResult = "Failed: ${SubsonicError.userMessage(e)}"
                    } finally {
                        isTesting = false
                    }
                }
            },
            enabled = url.isNotBlank() && username.isNotBlank() && password.isNotBlank() && !isTesting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Test Connection")
            if (isTesting) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(start = 8.dp).size(16.dp),
                )
            }
        }

        testResult?.let { result ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = result,
                color = if (result.contains("Success")) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
