package com.component.orders.models

import java.time.LocalDate

data class Product(
    val id: Int,
    val name: String,
    val type: ProductType,
    val inventory: Int,
    val createdOn: LocalDate?,
)

enum class ProductType {
    book,
    food,
    gadget,
    other
}
