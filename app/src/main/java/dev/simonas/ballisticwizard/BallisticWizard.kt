package dev.simonas.ballisticwizard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import no.nordicsemi.android.common.core.DataByteArray
import no.nordicsemi.android.common.core.IntFormat
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
import java.nio.ByteBuffer
import java.util.UUID


private const val SERVER_UUID = "5732d41c-e40b-4ec9-8e17-bc61ba185486"
private const val DATA_1_UUID = "0609d529-b3a9-4d18-ac96-e09a02d14cdf"

class BallisticWizard(
    scope: CoroutineScope,
): CoroutineScope by scope {

    val logsState = MutableStateFlow(emptyList<String>())

    sealed class ServerStartResult {
        object Ok : ServerStartResult()
        data class PermissionRequired(
            val permissions: List<String>,
        ) : ServerStartResult()
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

        //Define button characteristic
        val data1 = ServerBleGattCharacteristicConfig(
            uuid = UUID.fromString(DATA_1_UUID),
            properties = listOf(BleGattProperty.PROPERTY_READ, BleGattProperty.PROPERTY_WRITE, BleGattProperty.PROPERTY_NOTIFY),
            permissions = listOf(BleGattPermission.PERMISSION_READ, BleGattPermission.PERMISSION_WRITE),
            initialValue = DataByteArray(byteArrayOf(50)),
        )

        //Put led and button characteristics inside a service
        val serviceConfig = ServerBleGattServiceConfig(
            uuid = UUID.fromString(SERVER_UUID),
            type = ServerBleGattServiceType.SERVICE_TYPE_PRIMARY,
            characteristicConfigs = listOf(data1),
        )

        launch {
            val server = ServerBleGatt.create(
                context = context,
                scope = this,
                serviceConfig,
                logger = object : BleLogger {
                    override fun log(priority: Int, log: String) {
//                        log(log)
                    }
                },
            )
            onServerStart(server)
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
        launch {
            advertiser.advertise(advertiserConfig) //Start advertising
                .cancellable()
                .catch {
//                    log("blue wizard ad error: $it")
                }
                .collect {
//                    log("blue wizard ad: $it")
                }
        }
        return ServerStartResult.Ok
    }

    private fun onServerStart(server: ServerBleGatt) {
        launch {
            server.connectionEvents.collectLatest {
//                log("blue wizard event: $it")
            }
        }
        launch {
            server.connections.collectLatest {
                val device = it.values.firstOrNull()
                log("conn")
                log("connected devices:")
                it.values.forEach {
                    log("\t${it.device}")
                    log("\t${it.device.name}")
                    val service = it.services.findService(UUID.fromString(SERVER_UUID))
                    log("\tservice cnt:${it.services.services.size}")
                    log("\tservice:${service?.uuid}")
                    log("")

                    if (service != null) {
                        setUpServices(service)
                    }
                }
            }
        }
    }

    private suspend fun setUpServices(services: ServerBleGattService) {
        log("setUpServices invoked")
        val data1 = requireNotNull(services.findCharacteristic(UUID.fromString(DATA_1_UUID)))

        var initValue: Int? = null
        launch {
            while(true) {
                if (initValue != null) {
                    data1.setValueAndNotifyClient(DataByteArray(byteArrayOf(3)))
                    delay(16)
                }
            }
        }

        data1.value
            .onEach {
                initValue = it.getIntValue(IntFormat.FORMAT_SINT32_LE, 0)
                log("data1: $initValue")
            }
            .launchIn(this)
        log("Listening for data 1...")
    }

    private fun log(msg: String) {
        logsState.value = logsState.value.plus(msg)
        ld(msg)
    }
}

private fun ByteArray.toInt(): Int {
    val buffer = ByteBuffer.allocate(Integer.BYTES)
    buffer.put(this)
    buffer.rewind()
    return buffer.int
}