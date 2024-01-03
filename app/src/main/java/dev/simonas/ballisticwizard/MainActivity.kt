package dev.simonas.ballisticwizard

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import dev.simonas.ballisticwizard.ui.theme.MyApplicationTheme
import kotlinx.coroutines.GlobalScope

class MainActivity : ComponentActivity() {

    val wizard = BallisticWizard(GlobalScope)

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.all { it == true }) {
            callWizard()
        }
    }

    private fun callWizard() {
        val result = wizard.startServer(applicationContext)

        when (result) {
            is BallisticWizard.ServerStartResult.Ok -> {
                Toast.makeText(this, "Server running", Toast.LENGTH_LONG).show()
            }
            is BallisticWizard.ServerStartResult.PermissionRequired -> {
                Toast.makeText(this, "Needs Bluetooth permissions: ${result.permissions}", Toast.LENGTH_LONG).show()
                permissionRequest.launch(result.permissions.toTypedArray())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        callWizard()

        setContent {
            MyApplicationTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val logs = wizard.logsState.collectAsState()
                    Text(
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                        text = "Logs:\n" + logs.value.reversed().joinToString("\n"),
                    )
                }
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {

    }
}