package com.component.orders.controllers

import com.component.orders.models.Monitor
import com.component.orders.services.MonitorService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Validated
class MonitorController {
    @Autowired
    lateinit var monitorService: MonitorService

    @GetMapping("/monitor/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun retrieveMonitorStatus(@PathVariable("id") monitorId: Int): ResponseEntity<Monitor<*, *>> {
        val monitor = monitorService.retrieveMonitor(monitorId)
        return ResponseEntity(monitor, HttpStatus.OK)
    }
}
