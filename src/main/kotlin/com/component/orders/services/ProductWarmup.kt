package com.component.orders.services

import com.component.orders.backend.OrderService
import com.component.orders.models.NewProduct
import com.component.orders.models.ProductResponse
import com.component.orders.models.ProductType
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@ConditionalOnProperty(prefix = "app.warmup", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class ProductWarmup(
    private val orderBFFService: OrderBFFService,
    private val orderService: OrderService,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val warmupProduct = NewProduct(
            name = "warmup-product-${UUID.randomUUID()}",
            type = ProductType.gadget,
            inventory = 1,
        )

        try {
            when (val response = orderBFFService.createProduct(warmupProduct)) {
                is ProductResponse.ProductAdded -> {
                    orderService.deleteProduct(response.id)
                    println("[Warmup] Created and deleted product id ${response.id}")
                }
                is ProductResponse.RequestTimedOut -> {
                    println("[Warmup] Product creation timed out, skipping delete")
                }
            }
        } catch (e: Exception) {
            println("[Warmup] Product warmup failed: ${e.message}")
        }
    }
}
