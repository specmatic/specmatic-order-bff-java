package com.component.orders.models

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.util.concurrent.atomic.AtomicInteger

data class Product(
    @field:NotNull val name: String = "",
    @field:NotNull val type: String = "gadget",
    @field:Positive val inventory: Int = 0,
    val id: Int = idGenerator.getAndIncrement()
) {
    companion object {
        val idGenerator: AtomicInteger = AtomicInteger()
    }
}
