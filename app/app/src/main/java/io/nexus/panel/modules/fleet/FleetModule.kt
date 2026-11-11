package io.nexus.panel.modules.fleet

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import io.nexus.panel.core.Module
import io.nexus.panel.core.ModuleManager
import io.nexus.panel.core.Settings
import io.nexus.panel.modules.mqtt.MqttModule
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Fleet: announce this panel on the LAN so the host tooling **and Home
 * Assistant** can find it — with no addresses configured anywhere.
 *
 * - **mDNS/zeroconf** (`_nspanel-nexus._tcp`): the standard Android discovery
 *   mechanism. Home Assistant sees the service and offers one-tap setup.
 * - **UDP 5000**: the Nexus host tooling (`nspanel-nexus fleet`) broadcast.
 */
class FleetModule : Module {

    override val name = "fleet"

    private var socket: DatagramSocket? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private var nsdManager: NsdManager? = null
    private var nsdListener: NsdManager.RegistrationListener? = null

    private val mqtt get() = ModuleManager.get("mqtt") as? MqttModule

    override fun status(): String =
        if (running) "mdns + udp:5000" else "stopped"

    override fun start(context: Context) {
        running = true
        advertiseMdns(context)
        thread = Thread {
            val s = DatagramSocket(5000, InetAddress.getByName("0.0.0.0"))
            socket = s
            val buf = ByteArray(1024)
            while (running) {
                try {
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    if (String(p.data, 0, p.length) == "nexus-discover") {
                        val reply = JSONObject()
                            .put("name", Settings.deviceName)
                            .put("topic", "${Settings.topicPrefix}/${Settings.topicId}")
                            .put("mqtt_host", Settings.mqttHost)
                            .put("mqtt_port", Settings.mqttPort)
                            .put("adb_port", 5555)
                        val data = reply.toString().toByteArray()
                        s.send(DatagramPacket(data, data.size, p.address, p.port))
                    }
                } catch (_: Exception) { /* keep listening */ }
            }
        }.apply { isDaemon = true; start() }
        mqtt?.state("fleet", JSONObject().put("status", "listening"))
    }

    override fun stop() {
        running = false
        thread?.interrupt()
        socket?.close()
        socket = null
        val listener = nsdListener
        val manager = nsdManager
        if (listener != null && manager != null) {
            try { manager.unregisterService(listener) } catch (_: Exception) {}
        }
        nsdListener = null
    }

    /** Advertise the panel over mDNS — HA discovers it with zero configuration. */
    private fun advertiseMdns(context: Context) {
        val manager = try {
            context.getSystemService(Context.NSD_SERVICE) as NsdManager
        } catch (_: Exception) { return }
        nsdManager = manager

        val info = NsdServiceInfo().apply {
            serviceName = "NSPanel ${Settings.deviceName}"
            serviceType = "_nspanel-nexus._tcp."
            port = Settings.getInt("remote_desktop_port", 8080)
            setAttribute("name", Settings.deviceName)
            setAttribute("topic", "${Settings.topicPrefix}/${Settings.topicId}")
            setAttribute("mqtt_host", Settings.mqttHost)
            setAttribute("mqtt_port", Settings.mqttPort.toString())
            setAttribute("adb_port", "5555")
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(s: NsdServiceInfo) { /* advertised */ }
            override fun onRegistrationFailed(s: NsdServiceInfo, code: Int) { /* ignored */ }
            override fun onServiceUnregistered(s: NsdServiceInfo) { /* ignored */ }
            override fun onUnregistrationFailed(s: NsdServiceInfo, code: Int) { /* ignored */ }
        }
        nsdListener = listener
        try {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {
            nsdListener = null
        }
    }
}
