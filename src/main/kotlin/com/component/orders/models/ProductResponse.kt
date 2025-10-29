package com.component.orders.models

sealed interface ProductResponse {
    data class ProductAdded(val id: Int): ProductResponse

    data class RequestTimedOut(val monitorId: Int, val retryAfter: Int): ProductResponse {
        val monitorLink: String = "</monitor/$monitorId>;rel=related;title=monitor"
    }
}
