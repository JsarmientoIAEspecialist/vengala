package com.bengala.app.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Rol periférico: recibe escrituras de otros nodos y les empuja paquetes
 * por notificaciones. Las notificaciones se serializan (una en vuelo a la vez).
 */
@SuppressLint("MissingPermission")
class GattServer(
    private val context: Context,
    private val manager: BluetoothManager,
    private val router: MeshRouter,
) {
    private var server: BluetoothGattServer? = null
    private var messageChar: BluetoothGattCharacteristic? = null

    /** Centrales suscritos a notificaciones. */
    private val subscribers = ConcurrentHashMap<String, BluetoothDevice>()
    private val notifyQueue = ConcurrentLinkedQueue<Pair<BluetoothDevice, ByteArray>>()
    private val notifyInFlight = AtomicBoolean(false)

    private inner class ServerLink(private val device: BluetoothDevice) : MeshLink {
        override val linkId = "srv:${device.address}"
        override fun send(bytes: ByteArray): Boolean {
            if (!subscribers.containsKey(device.address)) return false
            notifyQueue.add(device to bytes)
            drainNotifyQueue()
            return true
        }
    }

    private val callback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                subscribers.remove(device.address)
                router.removeLink("srv:${device.address}")
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray,
        ) {
            if (characteristic.uuid == Protocol.MESSAGE_CHAR_UUID) {
                router.onReceived(value, "srv:${device.address}")
            }
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray,
        ) {
            if (descriptor.uuid == Protocol.CCCD_UUID) {
                val enabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                if (enabled) {
                    subscribers[device.address] = device
                    router.addLink(ServerLink(device))
                } else {
                    subscribers.remove(device.address)
                    router.removeLink("srv:${device.address}")
                }
            }
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            notifyInFlight.set(false)
            drainNotifyQueue()
        }
    }

    private fun drainNotifyQueue() {
        if (!notifyInFlight.compareAndSet(false, true)) return
        val next = notifyQueue.poll()
        if (next == null) {
            notifyInFlight.set(false)
            return
        }
        val (device, bytes) = next
        val srv = server
        val char = messageChar
        if (srv == null || char == null) {
            notifyInFlight.set(false)
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                srv.notifyCharacteristicChanged(device, char, false, bytes)
            } else {
                @Suppress("DEPRECATION")
                char.value = bytes
                @Suppress("DEPRECATION")
                srv.notifyCharacteristicChanged(device, char, false)
            }
        } catch (e: Exception) {
            Log.w("Bengala", "Notify falló", e)
            notifyInFlight.set(false)
        }
    }

    fun start() {
        if (server != null) return
        try {
            server = manager.openGattServer(context, callback) ?: return
        } catch (e: Exception) {
            Log.w("Bengala", "No se pudo abrir GATT server", e)
            return
        }
        val service = BluetoothGattService(
            Protocol.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )
        val char = BluetoothGattCharacteristic(
            Protocol.MESSAGE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        char.addDescriptor(
            BluetoothGattDescriptor(
                Protocol.CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
            ),
        )
        service.addCharacteristic(char)
        messageChar = char
        server?.addService(service)
    }

    fun stop() {
        try {
            server?.close()
        } catch (_: Exception) {
        }
        server = null
        messageChar = null
        subscribers.clear()
        notifyQueue.clear()
        notifyInFlight.set(false)
    }
}
