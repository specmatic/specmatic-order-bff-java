package com.component.orders.models

data class NewOrderEvent(
    val id: Int,
    val orderItems: List<Any>
) {
    data class OrderItem(
        val id: Int,
        val name: String,
        val quantity: Int,
        val price: Int
    )

    companion object {
        fun from(orderId: Int): NewOrderEvent {
            return NewOrderEvent(
                id = orderId,
                orderItems = emptyList()
            )
        }
    }
}
