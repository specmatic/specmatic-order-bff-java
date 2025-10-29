package com.component.orders.services

import com.component.orders.backend.OrderService
import com.component.orders.models.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.ResourceAccessException

@Service
class OrderBFFService {
    @Autowired
    lateinit var orderService: OrderService

    @Autowired
    lateinit var monitorService: MonitorService

    fun createOrder(orderRequest: OrderRequest): OrderResponse {
        return try {
            val orderId = createOrderInternal(orderRequest)
            OrderResponse.OrderConfirmed(id = orderId.body!!.id)
        } catch (_: ResourceAccessException) {
            val monitorRequest = MonitorRequest(body = orderRequest)
            val monitorId = monitorService.addMonitor(monitorRequest, ::createOrderInternal)
            println("[BFF] Order Creation Request timed out, starting a background monitor with id $monitorId")
            OrderResponse.RequestTimedOut(monitorId = monitorId)
        }
    }

    private fun createOrderInternal(orderRequest: OrderRequest): ResponseEntity<Id> {
        return orderService.createOrder(orderRequest)
    }

    fun findProducts(type: String): AvailableProductsResponse{
        return try {
            val products = orderService.findProducts(type)
            AvailableProductsResponse.FetchedProducts(products = products)
        } catch (_: ResourceAccessException) {
            println("[BFF] Products Fetch Request timed out, setting Retry-After to 3")
            AvailableProductsResponse.RequestTimedOut(retryAfter = 3)
        }
    }

    fun createProduct(newProduct: NewProduct): ProductResponse {
        return try {
            val productResponse = createProductInternal(newProduct)
            ProductResponse.ProductAdded(id = productResponse.body!!.id)
        } catch (_: ResourceAccessException) {
            val monitorRequest = MonitorRequest(body = newProduct)
            val monitorId = monitorService.addMonitor(monitorRequest, ::createProductInternal)
            println("[BFF] Product Creation Request timed out, starting a background monitor with id $monitorId")
            ProductResponse.RequestTimedOut(monitorId = monitorId)
        }
    }

    private fun createProductInternal(newProduct: NewProduct): ResponseEntity<Id> {
        return orderService.createProduct(newProduct)
    }
}
