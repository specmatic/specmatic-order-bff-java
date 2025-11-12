package com.component.orders.models

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class OrderRequest(
    @field:NotNull val productid: Int? = null,
    @field:NotNull val count: Int? = null,
) {
    @JsonIgnore
    val orderIdempotencyKey: String = UUID.randomUUID().toString()
}
