package dev.simonas.ballisticwizard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import no.nordicsemi.android.common.logger.BleLogger
import no.nordicsemi.android.kotlin.ble.advertiser.BleAdvertiser
import no.nordicsemi.android.kotlin.ble.core.advertiser.BleAdvertisingConfig
import no.nordicsemi.android.kotlin.ble.core.advertiser.BleAdvertisingData
import no.nordicsemi.android.kotlin.ble.core.advertiser.BleAdvertisingSettings
import no.nordicsemi.android.kotlin.ble.core.data.BleGattPermission
import no.nordicsemi.android.kotlin.ble.core.data.BleGattProperty
import no.nordicsemi.android.kotlin.ble.server.main.ServerBleGatt
import no.nordicsemi.android.kotlin.ble.server.main.service.ServerBleGattCharacteristicConfig
import no.nordicsemi.android.kotlin.ble.server.main.service.ServerBleGattService
import no.nordicsemi.android.kotlin.ble.server.main.service.ServerBleGattServiceConfig
import no.nordicsemi.android.kotlin.ble.server.main.service.ServerBleGattServiceType
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException


private const val SERVER_UUID = "5732d41c-e40b-4ec9-8e17-bc61ba185486"
private const val DATA_1_UUID = "0609d529-b3a9-4d18-ac96-e09a02d14cdf"
private const val DATA_2_UUID = "315fb3e2-9c5a-4784-8d30-8dddca5b9625"
private const val DATA_3_UUID = "fd3d3f21-ab45-4f0d-b0c7-de1d392df061"

public fun CoroutineContext.recursiveCancel(cause: CancellationException? = null) {
    this[Job]?.children?.forEach {
        if (it.children.count() > 0) {
            it.recursiveCancel(cause)
        }
        it.cancel(cause)
    }
}

class BallisticWizard(
    private val wizardScope: CoroutineScope,
) {

    private var advertiserJob: Job? = null
    private var serverJob: Job? = null
    private var lastServer: ServerBleGatt? = null
    private val _state = MutableStateFlow<WizardServiceState>(WizardServiceState.NotStarted)
    val state: StateFlow<WizardServiceState> = _state

    sealed class ServerStartResult {
        data object Ok : ServerStartResult()
        data class PermissionRequired(
            val permissions: List<String>,
        ) : ServerStartResult()
    }

    @SuppressLint("MissingPermission")
    fun stopServer() {
        lastServer?.connections?.value?.keys?.forEach {
            lastServer?.cancelConnection(it)
        }
        lastServer?.stopServer()
        wizardScope.cancel()
    }

    fun startServer(context: Context): ServerStartResult {
        val permissions = listOf(
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )

        val permissionsState = permissions
            .map { it to ActivityCompat.checkSelfPermission(context, it) }

        val nonGrantedPermissions = permissionsState
            .filter { it.second != PackageManager.PERMISSION_GRANTED }

        if (nonGrantedPermissions.isNotEmpty()) {
            return ServerStartResult.PermissionRequired(
                permissions = nonGrantedPermissions.map { it.first },
            )
        }

        val data1 = ServerBleGattCharacteristicConfig(
            uuid = UUID.fromString(DATA_1_UUID),
            properties = listOf(BleGattProperty.PROPERTY_READ, BleGattProperty.PROPERTY_WRITE, BleGattProperty.PROPERTY_NOTIFY),
            permissions = listOf(BleGattPermission.PERMISSION_READ, BleGattPermission.PERMISSION_WRITE),
            initialValue = null,
        )
        val data2 = ServerBleGattCharacteristicConfig(
            uuid = UUID.fromString(DATA_2_UUID),
            properties = listOf(BleGattProperty.PROPERTY_READ, BleGattProperty.PROPERTY_WRITE, BleGattProperty.PROPERTY_NOTIFY),
            permissions = listOf(BleGattPermission.PERMISSION_READ, BleGattPermission.PERMISSION_WRITE),
            initialValue = null,
        )
        val data3 = ServerBleGattCharacteristicConfig(
            uuid = UUID.fromString(DATA_3_UUID),
            properties = listOf(BleGattProperty.PROPERTY_READ, BleGattProperty.PROPERTY_WRITE, BleGattProperty.PROPERTY_NOTIFY),
            permissions = listOf(BleGattPermission.PERMISSION_READ, BleGattPermission.PERMISSION_WRITE),
            initialValue = null,
        )

        //Put led and button characteristics inside a service
        val serviceConfig = ServerBleGattServiceConfig(
            uuid = UUID.fromString(SERVER_UUID),
            type = ServerBleGattServiceType.SERVICE_TYPE_PRIMARY,
            characteristicConfigs = listOf(
                data1,
                data2,
                data3,
            ),
        )

        serverJob = wizardScope.launch(Dispatchers.IO) {
            try {
                val server = ServerBleGatt.create(
                    context = context,
                    scope = wizardScope,
                    serviceConfig,
                    logger = object : BleLogger {
                        override fun log(priority: Int, log: String) {
                            dlog(log)
                        }
                    },
                )
                lastServer = server
                onServerStart(server)
            } catch (e: Throwable) {
                elog(e.toString())
            }
        }

        val advertiser = BleAdvertiser.create(context)
        val advertiserConfig = BleAdvertisingConfig(
            settings = BleAdvertisingSettings(
                deviceName = "w",
                legacyMode = true,
                connectable = true,
                scannable = true,
            ),
            advertiseData = BleAdvertisingData(
                serviceUuid = ParcelUuid(UUID.fromString(SERVER_UUID)),
                includeDeviceName = true,
            )
        )
        advertiserJob = wizardScope.launch(Dispatchers.IO) {
            advertiser.advertise(advertiserConfig) //Start advertising
                .cancellable()
                .catch {
                    elog("blue wizard ad error: $it")
                }
                .collect {
                    dlog("blue wizard ad: $it")
                }
        }

        return ServerStartResult.Ok
    }

    private fun CoroutineScope.onServerStart(server: ServerBleGatt) {
        _state.value = WizardServiceState.WaitingForDevice
        launch {
            server.connections.collectLatest {
                dlog("conn")
                dlog("connected devices:")
                val gunMainframeService = it.values.flatMap { it.services.services }
                    .find { service ->
                        service.uuid == UUID.fromString(SERVER_UUID)
                    }
                if (gunMainframeService != null) {
                    setUpServices(
                        service = gunMainframeService,
                        connectionScope = this,
                    )
                } else {
                    _state.value = WizardServiceState.WaitingForDevice
                }
            }
        }
    }

    private fun setUpServices(
        service: ServerBleGattService,
        connectionScope: CoroutineScope,
    ) {
        dlog("setUpServices invoked")

        val channel1 = BleCharacteristicInt(
            scope = connectionScope,
            char = requireNotNull(service.findCharacteristic(UUID.fromString(DATA_1_UUID))),
        )
        val channel2 = BleCharacteristicInt(
            scope = connectionScope,
            char = requireNotNull(service.findCharacteristic(UUID.fromString(DATA_2_UUID))),
        )
        val channel3 = BleCharacteristicInt(
            scope = connectionScope,
            char = requireNotNull(service.findCharacteristic(UUID.fromString(DATA_3_UUID))),
        )

        connectionScope.launch {
            dlog("TTT: 1: ${channel1.read()}")
            dlog("TTT: 2: ${channel2.read()}")
            dlog("TTT: 3: ${channel3.read()}")

            combine(
                channel1.state,
                channel2.state,
                channel3.state,
            ) { newValue1, newValue2, newValue3 ->
                _state.value = WizardServiceState.Connected(
                    deviceData1 = newValue1,
                    setData1 = { value ->
                        channel1.write(value)
                    },
                    deviceData2 = newValue2,
                    setData2 = { value ->
                        channel2.write(value)
                    },
                    deviceData3 = newValue3,
                    setData3 = { value ->
                        channel3.write(value)
                    },
                )
            }.launchIn(this)
        }
    }
}
