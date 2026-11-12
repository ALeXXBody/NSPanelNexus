package io.nexus.panel.core

import android.content.Context

/**
 * A Nexus module. Every module is independently start/stop-able so the user
 * can enable or disable exactly what the panel should do.
 */
interface Module {
    val name: String
    fun start(context: Context)
    fun stop()
    /** Human-readable status for the UI and MQTT diagnostics. */
    fun status(): String
}

object ModuleManager {
    private val lock = Any()
    private val modules = LinkedHashMap<String, Module>()
    private val running = mutableSetOf<String>()

    fun register(m: Module) = synchronized(lock) { modules[m.name] = m }

    fun all(): List<Module> = synchronized(lock) { modules.values.toList() }
    fun get(name: String): Module? = synchronized(lock) { modules[name] }
    fun isRunning(name: String) = synchronized(lock) { name in running }

    fun startDesired(context: Context) {
        val todo = synchronized(lock) {
            modules.values.toList().filter { m ->
                Settings.moduleEnabled(m.name) && m.name !in running
            }
        }
        todo.forEach { m ->
            try { m.start(context); synchronized(lock) { running += m.name } }
            catch (t: Throwable) { try { m.stop() } catch (_: Throwable) {} }
        }
    }

    fun stopAll() {
        val all = synchronized(lock) { modules.values.toList().also { running.clear() } }
        all.forEach { try { it.stop() } catch (_: Throwable) {} }
    }

    fun stop(name: String) = synchronized(lock) {
        modules[name]?.stop()
        running -= name
    }

    fun start(name: String, context: Context) = synchronized(lock) {
        modules[name]?.let { if (it.name !in running) { it.start(context); running += it.name } }
    }

    fun applyDesired(context: Context, name: String) = synchronized(lock) {
        val want = Settings.moduleEnabled(name)
        if (want) start(name, context) else stop(name)
    }
}
