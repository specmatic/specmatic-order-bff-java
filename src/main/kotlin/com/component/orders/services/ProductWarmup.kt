package com.component.orders.services

import com.component.orders.backend.OrderService
import com.component.orders.models.NewProduct
import com.component.orders.models.ProductType
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@ConditionalOnProperty(prefix = "app.warmup", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class ProductWarmup(
    private val orderService: OrderService,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val warmupProduct = NewProduct(
            name = "warmup-product-${UUID.randomUUID()}",
            type = ProductType.gadget,
            inventory = 1,
        )

        val success = orderService.exerciseServiceChain(warmupProduct)
        if (success) {
            println("[Warmup] Successfully exercised service chain")
        } else {
            println("[Warmup] Service chain warmup failed")
        }
    }
}
