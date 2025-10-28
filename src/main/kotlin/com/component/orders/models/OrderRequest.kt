package com.component.orders.models

import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.UUID

data class OrderRequest(val productid: Int?, val count: Int?) {
    @JsonIgnore val idempotencyKey: String = UUID.randomUUID().toString()
}
