package dev.simonas.ballisticwizard

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.simonas.ballisticwizard.ui.theme.WizardAppTheme

@Composable
fun WizardScreen(
    state: State<WizardServiceState>,
) {
    WizardAppTheme {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 16.dp,
                    vertical = 32.dp,
                ),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val s = state.value) {
                is WizardServiceState.Connected -> {
                    WizardConnected(s)
                }
                is WizardServiceState.NotStarted -> {
                    WizardLogs()
                }
                is WizardServiceState.WaitingForDevice -> {
                    WizardLogs()
                }
            }
        }
    }
}

@Composable
private fun WizardConnected(
    state: WizardServiceState.Connected,
) {
    Row {
        Spacer(modifier = Modifier.weight(1f))
        WizardValueSlider(
            range = 1..100,
            value = state.deviceData1,
            onValueChange = {
                state.setData1(it)
            },
        )
        Spacer(modifier = Modifier.weight(1f))
        WizardValueSlider(
            range = 1..100,
            value = state.deviceData2,
            onValueChange = {
                state.setData2(it)
            },
        )
        Spacer(modifier = Modifier.weight(1f))
        WizardValueSlider(
            range = 1..100,
            value = state.deviceData3,
            onValueChange = {
                state.setData3(it)
            },
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WizardLogs() {
    val logs = LogsRepository.logs.collectAsState()
    Text(
        fontSize = 8.sp,
        lineHeight = 8.sp,
        text = "Logs:\n" + logs.value.reversed().joinToString("\n"),
    )
}


@SuppressLint("UnrememberedMutableState")
@Preview(showBackground = true)
@Composable
fun PreviewWizardScreen() {
    WizardScreen(
        state = mutableStateOf(
            WizardServiceState.Connected(
                deviceData1 = 1,
                setData1 = {},
                deviceData2 = 2,
                setData2 = {},
                deviceData3 = 3,
                setData3 = {},
            )
        )
    )
}