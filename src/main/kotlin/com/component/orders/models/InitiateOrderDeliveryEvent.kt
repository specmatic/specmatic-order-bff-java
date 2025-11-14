package com.component.orders.models

data class InitiateOrderDeliveryEvent(
    val orderId: Int,
    val deliveryAddress: String,
    val deliveryDate: String
) {
    companion object {
        fun from(orderId: Int): InitiateOrderDeliveryEvent {
            return InitiateOrderDeliveryEvent(
                orderId,
                "1234 Elm Street, Springfield",
                "2025-04-14"
            )
        }
    }
}
