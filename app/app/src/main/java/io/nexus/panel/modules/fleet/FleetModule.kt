package io.nexus.panel.modules.fleet

import android.content.Context
import io.nexus.panel.core.Module
import io.nexus.panel.core.ModuleManager
import io.nexus.panel.core.Settings
import io.nexus.panel.modules.mqtt.MqttModule
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Fleet: announce this panel on the LAN and answer UDP discovery so the host
 * tooling can find and manage every panel in the house from one place.
 */
class FleetModule : Module {

    override val name = "fleet"

    private var socket: DatagramSocket? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    private val mqtt get() = ModuleManager.get("mqtt") as? MqttModule

    override fun status(): String = if (running) "listening :5000" else "stopped"

    override fun start(context: Context) {
        running = true
        thread = Thread {
            val s = DatagramSocket(5000, InetAddress.getByName("0.0.0.0"))
            socket = s
            val buf = ByteArray(1024)
            while (running) {
                try {
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    val msg = String(p.data, 0, p.length)
                    if (msg == "nexus-discover") {
                        val reply = JSONObject()
                            .put("name", Settings.deviceName)
                            .put("host", p.address.hostAddress)
                            .put("adb_port", 5555)
                            .put("mqtt", "${Settings.mqttHost}:${Settings.mqttPort}")
                            .put("topic", "${Settings.topicPrefix}/${Settings.deviceName}")
                            .put("modules", Settings.all().toString())
                        val data = reply.toString().toByteArray()
                        s.send(DatagramPacket(data, data.size, p.address, p.port))
                    }
                } catch (_: Exception) { /* keep listening */ }
            }
        }.apply { isDaemon = true; start() }
        // publish a "fleet ready" state so HA can see the panel joined
        mqtt?.state("fleet", JSONObject().put("status", "listening"))
    }

    override fun stop() {
        running = false
        thread?.interrupt()
        socket?.close()
        socket = null
    }
}
