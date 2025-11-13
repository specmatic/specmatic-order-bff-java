package com.component.orders.models

data class Order(val productid: Int, val count: Int, val status: OrderStatus)

enum class OrderStatus {
    pending,
    fulfilled,
    cancelled
}
