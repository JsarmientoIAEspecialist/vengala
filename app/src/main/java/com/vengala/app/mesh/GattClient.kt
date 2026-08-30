package com.vengala.app.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Rol central: nos conectamos a un nodo descubierto por el scanner.
 * Enviamos con write-without-response serializado y recibimos por notify.
 */
@SuppressLint("MissingPermission")
class GattClient(
    private val context: Context,
    private val device: BluetoothDevice,
    private val router: MeshRouter,
    private val onRssi: (address: String, rssi: Int) -> Unit = { _, _ -> },
    private val onDisconnected: (address: String) -> Unit,
) {
    private val rssiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val rssiPoller = object : Runnable {
        override fun run() {
            if (!ready) return
            try {
                gatt?.readRemoteRssi()
            } catch (_: Exception) {
            }
            rssiHandler.postDelayed(this, 2_000)
        }
    }
    private var gatt: BluetoothGatt? = null
    private var messageChar: BluetoothGattCharacteristic? = null
    private val writeQueue = ConcurrentLinkedQueue<ByteArray>()
    private val writeInFlight = AtomicBoolean(false)
    @Volatile private var ready = false

    val address: String = device.address
    private val linkId = "cli:$address"

    private inner class ClientLink : MeshLink {
        override val linkId = this@GattClient.linkId
        override fun send(bytes: ByteArray): Boolean {
            if (!ready) return false
            writeQueue.add(bytes)
            drainWriteQueue()
            return true
        }
    }

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // Si el stack no acepta la petición de MTU, seguimos igual:
                    // onMtuChanged nunca llegaría y la conexión quedaría muerta.
                    val requested = try { g.requestMtu(517) } catch (_: Exception) { false }
                    if (!requested) g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> teardown()
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(Protocol.SERVICE_UUID) ?: run { teardown(); return }
            val char = service.getCharacteristic(Protocol.MESSAGE_CHAR_UUID)
                ?: run { teardown(); return }
            messageChar = char
            g.setCharacteristicNotification(char, true)
            val cccd = char.getDescriptor(Protocol.CCCD_UUID) ?: run { teardown(); return }
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (d.uuid == Protocol.CCCD_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                ready = true
                router.addLink(ClientLink())
                rssiHandler.post(rssiPoller)
            } else if (d.uuid == Protocol.CCCD_UUID) {
                teardown()
            }
        }

        override fun onReadRemoteRssi(g: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) onRssi(address, rssi)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int,
        ) {
            writeInFlight.set(false)
            drainWriteQueue()
        }

        // API 33+
        override fun onCharacteristicChanged(
            g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray,
        ) {
            if (c.uuid == Protocol.MESSAGE_CHAR_UUID) router.onReceived(value, linkId)
        }

        // API < 33
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= 33) return
            @Suppress("DEPRECATION")
            val value = c.value ?: return
            if (c.uuid == Protocol.MESSAGE_CHAR_UUID) router.onReceived(value, linkId)
        }
    }

    private fun drainWriteQueue() {
        if (!writeInFlight.compareAndSet(false, true)) return
        val bytes = writeQueue.poll()
        if (bytes == null) {
            writeInFlight.set(false)
            return
        }
        val g = gatt
        val char = messageChar
        if (g == null || char == null) {
            writeInFlight.set(false)
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(
                    char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
                )
            } else {
                @Suppress("DEPRECATION")
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                char.value = bytes
                @Suppress("DEPRECATION")
                g.writeCharacteristic(char)
            }
        } catch (e: Exception) {
            Log.w("Vengala", "Write falló", e)
            writeInFlight.set(false)
        }
    }

    fun connect() {
        try {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            // Si en 20 s no llegamos a "ready" (suscripción de notify hecha),
            // la conexión se atascó en algún paso: se libera para reintentar.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!ready && gatt != null) {
                    Log.w("Vengala", "Conexión a $address atascada; se reinicia")
                    teardown()
                }
            }, 20_000)
        } catch (e: Exception) {
            Log.w("Vengala", "connectGatt falló", e)
            teardown()
        }
    }

    fun disconnect() = teardown()

    private fun teardown() {
        ready = false
        rssiHandler.removeCallbacks(rssiPoller)
        router.removeLink(linkId)
        writeQueue.clear()
        writeInFlight.set(false)
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        messageChar = null
        onDisconnected(address)
    }
}
