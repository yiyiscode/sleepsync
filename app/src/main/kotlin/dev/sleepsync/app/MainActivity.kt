package dev.sleepsync.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dev.sleepsync.core.AppInfo
import dev.sleepsync.core.SyncResult
import dev.sleepsync.core.sync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private class Recovery(val label: String, val run: () -> Unit)

    private var status by mutableStateOf("Ready")
    private var busy by mutableStateOf(false)
    private var recovery by mutableStateOf<Recovery?>(null)
    private var introSeen by mutableStateOf(false)

    private val readSleep = HealthPermission.getReadPermission(SleepSessionRecord::class)

    private val grantPermission = Recovery("Grant permission") { healthPermission.launch(setOf(readSleep)) }
    private val signIn = Recovery("Sign in with Google") { onSyncClicked() }
    private val getHealthConnect = Recovery("Get Health Connect") {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE")))
    }

    private val healthPermission = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        if (readSleep in granted) {
            onSyncClicked()
        } else {
            report("Sleep permission was not granted. SleepSync needs it to read your sleep.", grantPermission)
        }
    }

    private val driveConsent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        // Cancelling the consent screen also ends up as a null token or an exception.
        val token = try {
            Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(r.data).accessToken
        } catch (e: Exception) {
            null
        }
        if (token != null) runSync(token) else report(SIGN_IN_MESSAGE, signIn)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        introSeen = getPreferences(MODE_PRIVATE).getBoolean(INTRO_SEEN, false)
        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!introSeen) {
                        // Explainer shown before the first permission prompt.
                        Text("Before you start", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "SleepSync will ask for two things: permission to read your sleep sessions " +
                                "(sleep only) from Health Connect, and access to Google Drive limited to " +
                                "files this app creates. It writes one sleep-YYYY-MM.csv per month to a " +
                                "SleepSync folder in your Drive.",
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                        Button(onClick = {
                            getPreferences(MODE_PRIVATE).edit().putBoolean(INTRO_SEEN, true).apply()
                            introSeen = true
                        }) { Text("Continue") }
                    } else {
                        Text(AppInfo.NAME, style = MaterialTheme.typography.headlineLarge)
                        Text(VersionLabel.format(BuildConfig.VERSION_NAME))
                        Text(
                            "Reads your sleep sessions from Health Connect (sleep only) and writes " +
                                "sleep-YYYY-MM.csv files to a SleepSync folder in your Google Drive.",
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                        Button(onClick = ::onSyncClicked, enabled = !busy) { Text("Sync") }
                        Text(status, modifier = Modifier.padding(top = 16.dp))
                        recovery?.let { r -> Button(onClick = r.run) { Text(r.label) } }
                    }
                }
            }
        }
    }

    private fun report(message: String, next: Recovery? = null) {
        status = message
        recovery = next
        busy = false
    }

    private fun onSyncClicked() {
        busy = true
        recovery = null
        status = "Syncing..."
        authorizeDrive()
    }

    private fun authorizeDrive() {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                val token = result.accessToken
                when {
                    result.hasResolution() && pending != null ->
                        driveConsent.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    token != null -> runSync(token)
                    else -> report(SIGN_IN_MESSAGE, signIn)
                }
            }
            .addOnFailureListener { report(SIGN_IN_MESSAGE, signIn) }
    }

    private fun runSync(token: String) {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    sync(HealthConnectSleepSource(this@MainActivity), DriveSleepStore(token))
                }
                describe(result)
            } catch (e: Exception) {
                report("Failed: ${e.message}")
            }
        }
    }

    private fun describe(result: SyncResult) = when (result) {
        is SyncResult.Synced -> report("Synced ${result.nightsAdded} nights into ${result.months.joinToString()}")
        SyncResult.NeedsHealthConnect -> report("Health Connect is missing or needs an update.", getHealthConnect)
        SyncResult.NeedsHealthConnectPermission ->
            report("SleepSync needs permission to read your sleep from Health Connect.", grantPermission)
        SyncResult.NeedsSignIn -> report(SIGN_IN_MESSAGE, signIn)
    }

    private companion object {
        const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
        const val SIGN_IN_MESSAGE = "Google sign-in is needed to write to your Drive."
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val INTRO_SEEN = "intro_seen"
    }
}
