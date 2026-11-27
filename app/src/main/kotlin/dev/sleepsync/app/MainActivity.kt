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
import androidx.health.connect.client.HealthConnectFeatures
import android.util.Log
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
    private var status by mutableStateOf("Ready")
    private var busy by mutableStateOf(false)

    private val readSleep = HealthPermission.getReadPermission(SleepSessionRecord::class)
    private val readBackground = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    private val healthPermission = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        // spike: show what Health Connect granted.
        report("SPIKE granted: sleep=${readSleep in granted} background=${readBackground in granted}")
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
                }
            }
        }
    }

    private fun report(message: String) {
        status = message
        busy = false
    }

    private fun onSyncClicked() {
        busy = true
        status = "Syncing..."
        lifecycleScope.launch {
            try {
                if (HealthConnectClient.getSdkStatus(this@MainActivity) != HealthConnectClient.SDK_AVAILABLE) {
                    report("Health Connect is not available or needs an update")
                    return@launch
                }
                // spike (#27): is background read available, and can it be granted?
                val client = HealthConnectClient.getOrCreate(this@MainActivity)
                val feature = client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)
                val available = feature == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
                val granted = client.permissionController.getGrantedPermissions()
                Log.d("SleepSyncBG", "feature=$feature available=$available granted=$granted")
                if (!available) report("SPIKE background feature status=$feature (not available)")
                else if (readBackground in granted) report("SPIKE background available and already granted")
                else healthPermission.launch(setOf(readSleep, readBackground))
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
            val message = try {
                val result = withContext(Dispatchers.IO) {
                    sync(
                        HealthConnectSleepSource(HealthConnectClient.getOrCreate(this@MainActivity)),
                        DriveSleepStore(token),
                    )
                }
                describe(result)
            } catch (e: Exception) {
                "Failed: ${e.message}"
            }
            report(message)
        }
    }

    private fun describe(result: SyncResult): String = when (result) {
        is SyncResult.Synced -> "Synced ${result.nightsAdded} nights into ${result.months.joinToString()}"
        SyncResult.UpToDate -> "Already up to date"
    }

    private companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
