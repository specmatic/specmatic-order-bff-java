package com.component.orders.models

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class NewProduct(
    @field:NotNull val name: String = "",
    @field:NotNull val type: String = "gadget",
    @field:NotNull val inventory: Int? = 1
) {
    @JsonIgnore val idempotencyKey: String = UUID.randomUUID().toString()
}
