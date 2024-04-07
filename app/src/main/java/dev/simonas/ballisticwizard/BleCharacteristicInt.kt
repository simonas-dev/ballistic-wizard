package dev.simonas.ballisticwizard

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import no.nordicsemi.android.common.core.DataByteArray
import no.nordicsemi.android.common.core.IntFormat
import no.nordicsemi.android.kotlin.ble.server.main.service.ServerBleGattCharacteristic

internal class BleCharacteristicInt(
    private val char: ServerBleGattCharacteristic,
    private val scope: CoroutineScope,
) {

    private val _state = MutableSharedFlow<Int>(replay = 1)
    val state: SharedFlow<Int> = _state

    private val beat = flow<Unit> {
        while (true) {
            emit(Unit)
            delay(1000)
        }
    }

    init {
        combine(beat, state) { _, state ->
            char.setValueAndNotifyClient(state.toBytes())
        }.shareIn(scope, started = SharingStarted.Eagerly)
    }

    fun write(value: Int) {
        val valueArr = DataByteArray(byteArrayOf(value.toByte()))
        _state.tryEmit(value)
    }

    suspend fun read(): Int {
        val byteArr = char.value.take(1).single()
        val int = byteArr.toIntOrNull()
        return when {
            int == null -> {
                elog("unable to parse byte array to int: $byteArr")
                0
            }
            else -> {
                _state.tryEmit(int)
                int
            }
        }
    }

    private fun DataByteArray.toIntOrNull(): Int? =
        getIntValue(IntFormat.FORMAT_UINT8, 0)

    private fun Int.toBytes(): DataByteArray =
        DataByteArray(byteArrayOf(this.toByte()))
}