package com.component.orders.controllers

import com.component.orders.models.OrderRequest
import com.component.orders.models.OrderResponse
import com.component.orders.services.OrderBFFService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class Orders(@Autowired val orderBFFService: OrderBFFService) {
    @PostMapping("/orders", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun createOrder(@RequestBody orderRequest: OrderRequest): ResponseEntity<*> {
        return when (val orderResponse = orderBFFService.createOrder(orderRequest)) {
            is OrderResponse.OrderConfirmed -> ResponseEntity(orderResponse, HttpStatus.CREATED)
            is OrderResponse.RequestTimedOut -> {
                val headers = HttpHeaders().apply {
                    add(HttpHeaders.RETRY_AFTER, "3")
                    add(HttpHeaders.LINK, orderResponse.monitorLink)
                }
                ResponseEntity<Void>(headers, HttpStatus.ACCEPTED)
            }
        }
    }
}
