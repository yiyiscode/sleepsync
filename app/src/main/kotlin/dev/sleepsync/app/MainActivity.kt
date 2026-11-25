package dev.sleepsync.app

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
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dev.sleepsync.core.AppInfo
import dev.sleepsync.core.SyncStatus
import dev.sleepsync.core.message
import dev.sleepsync.core.syncAndRecord
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.material3.TextButton
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var status by mutableStateOf("Ready")
    private var busy by mutableStateOf(false)
    private var lastStatus by mutableStateOf<SyncStatus?>(null)
    private var failure by mutableStateOf<String?>(null)

    private val readSleep = HealthPermission.getReadPermission(SleepSessionRecord::class)

    private val healthPermission = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        if (readSleep in granted) authorizeDrive() else report("Health Connect sleep permission was not granted")
    }

    private val driveConsent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        try {
            val token = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(r.data).accessToken
            if (token != null) runSync(token) else report("Google Drive access was not granted")
        } catch (e: Exception) {
            report("Google sign-in failed: ${e.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastStatus = PrefsStatusStore(this).load()
        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(AppInfo.NAME, style = MaterialTheme.typography.headlineLarge)
                    Text(VersionLabel.format(BuildConfig.VERSION_NAME))
                    // Also serves as the Health Connect permission rationale.
                    Text(
                        "Reads your sleep sessions from Health Connect (sleep only) and writes " +
                            "sleep-YYYY-MM.csv files to a SleepSync folder in your Google Drive.",
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    Button(onClick = ::onSyncClicked, enabled = !busy) { Text("Sync") }
                    Text(status, modifier = Modifier.padding(top = 16.dp))
                    Text(
                        lastStatus?.let { "Last sync: ${it.lastSyncAt}; newest night: ${it.newestNight ?: "none"}" }
                            ?: "Never synced",
                    )
                    failure?.let { reason ->
                        TextButton(onClick = {
                            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("SleepSync error", reason))
                        }) { Text("Copy error reason") }
                    }
                }
            }
        }
    }

    private fun report(message: String, error: Boolean = true) {
        status = message
        failure = if (error) "SleepSync ${BuildConfig.VERSION_NAME}: $message" else null
        busy = false
    }

    private fun onSyncClicked() {
        if (busy) return
        busy = true
        status = "Syncing..."
        lifecycleScope.launch {
            try {
                if (HealthConnectClient.getSdkStatus(this@MainActivity) != HealthConnectClient.SDK_AVAILABLE) {
                    report("Health Connect is not available or needs an update")
                    return@launch
                }
                val granted = HealthConnectClient.getOrCreate(this@MainActivity).permissionController.getGrantedPermissions()
                if (readSleep in granted) authorizeDrive() else healthPermission.launch(setOf(readSleep))
            } catch (e: Exception) {
                report("Failed: ${e.message}")
            }
        }
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
                    else -> report("Google Drive access was not granted")
                }
            }
            .addOnFailureListener { report("Google sign-in failed: ${it.message}") }
    }

    private fun runSync(token: String) {
        lifecycleScope.launch {
            val statuses = PrefsStatusStore(this@MainActivity)
            try {
                val result = withContext(Dispatchers.IO) {
                    syncAndRecord(
                        HealthConnectSleepSource(HealthConnectClient.getOrCreate(this@MainActivity)),
                        DriveSleepStore(token),
                        statuses,
                        Instant.now(),
                    )
                }
                lastStatus = statuses.load()
                report(result.message(), error = false)
            } catch (e: Exception) {
                report("Failed: ${e.message}")
            }
        }
    }

    private companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
