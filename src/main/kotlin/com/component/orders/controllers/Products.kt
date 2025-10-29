package com.component.orders.controllers

import com.component.orders.models.AvailableProductsResponse
import com.component.orders.models.NewProduct
import com.component.orders.models.ProductResponse
import com.component.orders.services.OrderBFFService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders

@RestController
@Validated
class Products(@Autowired val orderBFFService: OrderBFFService) {
    @GetMapping("/findAvailableProducts", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findAvailableProducts(
        @RequestParam(name = "type", required = false, defaultValue = "gadget") type: String,
        @RequestHeader(name = "pageSize", required = true) pageSize: Int,
    ): ResponseEntity<*> {
        if (pageSize < 0) throw IllegalArgumentException("pageSize must be positive")
        return when (val productsResponse = orderBFFService.findProducts(type)) {
            is AvailableProductsResponse.FetchedProducts -> ResponseEntity(productsResponse.products, HttpStatus.OK)
            is AvailableProductsResponse.RequestTimedOut -> {
                val headers = HttpHeaders().apply {
                    add(HttpHeaders.RETRY_AFTER, productsResponse.retryAfter.toString())
                }
                ResponseEntity<Void>(headers, HttpStatus.TOO_MANY_REQUESTS)
            }
        }
    }

    @PostMapping("/products", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun createProduct(@Valid @RequestBody newProduct: NewProduct): ResponseEntity<*> {
        return when (val productResponse = orderBFFService.createProduct(newProduct)) {
            is ProductResponse.ProductAdded -> ResponseEntity(productResponse, HttpStatus.CREATED)
            is ProductResponse.RequestTimedOut -> {
                val headers = HttpHeaders().apply {
                    add(HttpHeaders.RETRY_AFTER, productResponse.retryAfter.toString())
                    add(HttpHeaders.LINK, productResponse.monitorLink)
                }
                ResponseEntity<Void>(headers, HttpStatus.ACCEPTED)
            }
        }
    }
}
