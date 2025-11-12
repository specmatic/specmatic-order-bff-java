package com.component.orders.models

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class OrderRequest(@field:NotNull val productid: Int, @field:NotNull val count: Int) {
    @JsonIgnore val idempotencyKey: String = UUID.randomUUID().toString()
}
