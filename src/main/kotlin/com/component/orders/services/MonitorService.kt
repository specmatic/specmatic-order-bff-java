package com.component.orders.services

import com.component.orders.database.MonitorDatabase
import com.component.orders.models.HeaderItem
import com.component.orders.models.Monitor
import com.component.orders.models.MonitorRequest
import com.component.orders.models.MonitorResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class MonitorService {
    @Autowired
    lateinit var monitorDatabase: MonitorDatabase

    fun retrieveMonitor(monitorId: Int): Monitor<*, *> = monitorDatabase.retrieveMonitor(monitorId)

    fun <T, U> addMonitor(request: MonitorRequest<T>, callBack: (T) -> ResponseEntity<U>): Int {
        val monitor = Monitor(request = request, callBack = callBack)
        return monitorDatabase.addMonitor(monitor)
    }

    @Scheduled(fixedRate = 2000)
    fun <T, U> scheduledMonitorCheck() {
        monitorDatabase.transformMonitors { monitorId, monitor ->
            @Suppress("UNCHECKED_CAST")
            monitor as Monitor<T, U>
            if (monitor.response != null) return@transformMonitors monitor
            if (monitor.request == null) return@transformMonitors monitor
            println("Invoking monitor with id $monitorId")
            val response = monitor.callBack.invoke(monitor.request.body)
            monitor.copy(
                response = MonitorResponse(
                    statusCode = response.statusCode.value(),
                    body = response.body!!,
                    headers = response.headers.flatMap { (key, values) ->
                        values.map { value -> HeaderItem(key, value.toString()) }
                    },
                ),
            )
        }
    }
}
