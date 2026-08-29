package com.vengala.app.mesh

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vengala.app.MainActivity
import com.vengala.app.R
import com.vengala.app.crypto.CryptoBox
import com.vengala.app.data.ChatMessage
import com.vengala.app.data.MeshRepository
import com.vengala.app.data.PeerLocation
import com.vengala.app.data.Settings
import com.vengala.app.location.LocationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Servicio en primer plano que mantiene vivo el mesh mientras bailas:
 * advertising + scan + servidor GATT + conexiones salientes + beacons de
 * ubicación y perfil.
 */
class MeshService : Service() {

    companion object {
        @Volatile
        var instance: MeshService? = null
            private set

        const val MAX_OUTGOING_LINKS = 5
        const val CONNECT_COOLDOWN_MS = 20_000L
        const val LOCATION_BEACON_MS = 30_000L
        const val PROFILE_BEACON_MS = 60_000L

        fun start(context: Context) {
            val intent = Intent(context, MeshService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MeshService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var settings: Settings
    private lateinit var crypto: CryptoBox
    private lateinit var router: MeshRouter

    private var advertiser: BleAdvertiser? = null
    private var scanner: BleScanner? = null
    private var gattServer: GattServer? = null
    private var locationEngine: LocationEngine? = null

    private val clients = ConcurrentHashMap<String, GattClient>()
    private val connectCooldown = ConcurrentHashMap<String, Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = Settings(this)
        crypto = CryptoBox(settings.partyCode)
        router = MeshRouter(::deliverPacket)

        startForeground(1, buildNotification())

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btManager.adapter
        if (adapter != null && adapter.isEnabled) {
            gattServer = GattServer(this, btManager, router).also { it.start() }
            advertiser = BleAdvertiser(adapter).also { it.start() }
            scanner = BleScanner(adapter, ::onNodeDiscovered).also { it.start() }
        }

        locationEngine = LocationEngine(this) { loc ->
            MeshRepository.setMyLocation(loc)
        }.also { it.start() }

        MeshRepository.updateStats { it.copy(running = true) }
        startBeacons()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        scanner?.stop()
        advertiser?.stop()
        gattServer?.stop()
        locationEngine?.stop()
        for (client in clients.values) client.disconnect()
        clients.clear()
        router.clear()
        MeshRepository.reset()
        instance = null
        super.onDestroy()
    }

    // ---------- Descubrimiento y conexiones ----------

    private fun onNodeDiscovered(device: BluetoothDevice, rssi: Int) {
        val address = device.address
        if (clients.containsKey(address)) return
        if (clients.size >= MAX_OUTGOING_LINKS) return
        val last = connectCooldown[address] ?: 0L
        val now = System.currentTimeMillis()
        if (now - last < CONNECT_COOLDOWN_MS) return
        connectCooldown[address] = now

        val client = GattClient(this, device, router) { addr ->
            clients.remove(addr)
            updateDirectPeerCount()
        }
        clients[address] = client
        client.connect()
        updateDirectPeerCount()
    }

    private fun updateDirectPeerCount() {
        MeshRepository.updateStats { it.copy(directPeers = router.linkCount) }
    }

    // ---------- Entrega de paquetes ----------

    private fun deliverPacket(packet: Protocol.Packet) {
        if (packet.senderId == settings.nodeId) return
        updateDirectPeerCount()

        val plain: ByteArray = if (packet.isEncrypted) {
            crypto.decrypt(packet.payload) ?: return  // otro código de fiesta: solo relay
        } else packet.payload

        val json = try {
            JSONObject(String(plain, Charsets.UTF_8))
        } catch (_: Exception) {
            return
        }
        val name = json.optString("n", "???")

        when (packet.type) {
            Protocol.TYPE_CHAT -> {
                val text = json.optString("t")
                if (text.isBlank()) return
                MeshRepository.upsertPeer(packet.senderId, name = name)
                MeshRepository.addMessage(
                    ChatMessage(
                        messageId = packet.messageId,
                        senderId = packet.senderId,
                        senderName = name,
                        text = text,
                        timestamp = packet.timestamp,
                        isMine = false,
                    ),
                )
            }

            Protocol.TYPE_LOCATION -> {
                val la = json.optDouble("la", Double.NaN)
                val lo = json.optDouble("lo", Double.NaN)
                if (la.isNaN() || lo.isNaN()) return
                MeshRepository.upsertPeer(
                    packet.senderId,
                    name = name,
                    location = PeerLocation(
                        latitude = la,
                        longitude = lo,
                        accuracyMeters = json.optDouble("ac", 0.0).toFloat(),
                        timestamp = packet.timestamp,
                    ),
                    battery = json.optInt("bat", -1).takeIf { it >= 0 },
                )
            }

            Protocol.TYPE_PROFILE -> {
                MeshRepository.upsertPeer(
                    packet.senderId,
                    name = name,
                    battery = json.optInt("bat", -1).takeIf { it >= 0 },
                )
            }
        }
    }

    // ---------- Envío ----------

    fun sendChat(text: String) {
        val trimmed = text.trim().take(300)
        if (trimmed.isEmpty()) return
        val payload = JSONObject()
            .put("n", settings.displayName)
            .put("t", trimmed)
            .toString().toByteArray(Charsets.UTF_8)
        val packet = Protocol.build(
            Protocol.TYPE_CHAT, settings.nodeId, crypto.encrypt(payload), encrypted = true,
        )
        router.sendLocal(packet)
        MeshRepository.addMessage(
            ChatMessage(
                messageId = packet.messageId,
                senderId = settings.nodeId,
                senderName = settings.displayName,
                text = trimmed,
                timestamp = packet.timestamp,
                isMine = true,
            ),
        )
    }

    private fun sendLocationBeacon() {
        if (!settings.shareLocation) return
        val loc = MeshRepository.myLocation.value ?: return
        val payload = JSONObject()
            .put("n", settings.displayName)
            .put("la", loc.latitude)
            .put("lo", loc.longitude)
            .put("ac", loc.accuracyMeters.toDouble())
            .put("bat", batteryPercent())
            .toString().toByteArray(Charsets.UTF_8)
        router.sendLocal(
            Protocol.build(
                Protocol.TYPE_LOCATION, settings.nodeId, crypto.encrypt(payload), encrypted = true,
            ),
        )
    }

    private fun sendProfileBeacon() {
        val payload = JSONObject()
            .put("n", settings.displayName)
            .put("bat", batteryPercent())
            .toString().toByteArray(Charsets.UTF_8)
        router.sendLocal(
            Protocol.build(
                Protocol.TYPE_PROFILE, settings.nodeId, crypto.encrypt(payload), encrypted = true,
            ),
        )
    }

    private fun batteryPercent(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    /** Llamar cuando cambian nombre o código de fiesta en Ajustes. */
    fun onSettingsChanged() {
        crypto = CryptoBox(settings.partyCode)
        scope.launch { sendProfileBeacon() }
    }

    private fun startBeacons() {
        scope.launch {
            while (true) {
                sendLocationBeacon()
                delay(LOCATION_BEACON_MS)
            }
        }
        scope.launch {
            delay(3_000)
            while (true) {
                sendProfileBeacon()
                MeshRepository.pruneExpiredPeers()
                updateDirectPeerCount()
                delay(PROFILE_BEACON_MS)
            }
        }
    }

    // ---------- Notificación ----------

    @SuppressLint("MissingPermission")
    private fun buildNotification(): Notification {
        val channelId = "vengala_mesh"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId, "Mesh Vengala", NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.mesh_running))
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }
}
