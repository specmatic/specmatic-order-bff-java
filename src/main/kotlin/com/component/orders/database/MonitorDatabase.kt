package com.component.orders.database

import com.component.orders.models.Monitor
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Service
class MonitorDatabase {
    private val monitorCounter: AtomicInteger = AtomicInteger(1)
    private val monitors: MutableMap<Int, Monitor<*, *>> = ConcurrentHashMap()

    fun <T, U> addMonitor(monitor: Monitor<T, U>): Int {
        val nextId = monitorCounter.getAndIncrement()
        monitors[nextId] = monitor
        return nextId
    }

    fun retrieveMonitor(monitorId: Int): Monitor<*, *> {
        return monitors[monitorId] ?: throw IllegalArgumentException("Monitor with id $monitorId doesn't exist")
    }

    fun transformMonitors(transform: (Monitor<*, *>) -> Monitor<*, *>) {
        monitors.forEach { (id, monitor) ->
            val updatedMonitor = transform(monitor)
            monitors[id] = updatedMonitor
        }
    }

    @Synchronized
    fun resetDB() {
        monitors.clear()
        monitorCounter.set(0)
    }
}
