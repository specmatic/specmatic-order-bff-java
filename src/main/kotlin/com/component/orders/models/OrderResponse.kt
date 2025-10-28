package com.component.orders.models

sealed interface OrderResponse {
    data class OrderConfirmed(val id: Int): OrderResponse

    data class RequestTimedOut(val monitorId: Int): OrderResponse {
        val monitorLink: String = "</monitor/$monitorId>;rel=related;title=monitor"
    }
}
