package com.component.orders.models

import java.time.OffsetDateTime

data class Product(
    val id: Int,
    val name: String,
    val type: ProductType,
    val inventory: Int,
    val createdOn: OffsetDateTime?,
)

enum class ProductType {
    book,
    food,
    gadget,
    other
}
