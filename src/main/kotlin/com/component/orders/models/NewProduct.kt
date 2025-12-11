package com.component.orders.models

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class NewProduct(
    @field:NotNull val name: String? = null,
    @field:NotNull val type: ProductType? = null,
    @field:NotNull @field:Min(1) @field:Max(101) val inventory: Int? = null
) {
    @JsonIgnore val idempotencyKey: String = UUID.randomUUID().toString()
}
