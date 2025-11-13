package com.component.orders.models

sealed interface AvailableProductsResponse {
    data class FetchedProducts(val products: List<Product>): AvailableProductsResponse

    data class RequestTimedOut(val retryAfter: Int): AvailableProductsResponse
}
