package com.component.orders.models

data class Product(
    val name: String,
    val type: ProductType,
    val inventory: Int,
    val id: Int,
)

enum class ProductType {
    book,
    food,
    gadget,
    other
}
