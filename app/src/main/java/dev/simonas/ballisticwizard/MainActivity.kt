package dev.simonas.ballisticwizard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class MainActivity : ComponentActivity() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val wizard = BallisticWizard(scope)

    override fun onResume() {
        super.onResume()
        startWizard()
    }

    override fun onPause() {
        wizard.stopServer()
        super.onPause()
    }

    override fun onDestroy() {
        wizard.stopServer()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val wizardState = wizard.state.collectAsState()
            WizardScreen(
                wizardState
            )
        }
    }

    private fun startWizard() {
        val result = wizard.startServer(this)

        when (result) {
            is BallisticWizard.ServerStartResult.Ok -> {
                dlog("Server running")
            }
            is BallisticWizard.ServerStartResult.PermissionRequired -> {
                dlog("Needs Bluetooth permissions: ${result.permissions}")
                permissionRequest.launch(result.permissions.toTypedArray())
            }
        }
    }

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.all { it == true }) {
            startWizard()
        }
    }
}
