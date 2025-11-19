package com.component.orders.models

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.NotNull
import org.springframework.http.ResponseEntity

data class Monitor<T, U>(
    val request: MonitorRequest<T>? = null,
    val response: MonitorResponse<U>? = null,
    @JsonIgnore val callBack: (T) -> ResponseEntity<U>
)

data class MonitorRequest<T>(
    @field:NotNull val method: String = "POST",
    @field:NotNull val body: T,
    @field:NotNull val headers: List<HeaderItem> = emptyList(),
)

data class MonitorResponse<T>(
    @field:NotNull val statusCode: Int,
    @field:NotNull val body: T,
    @field:NotNull val headers: List<HeaderItem> = emptyList(),
)

data class HeaderItem(
    @field:NotNull val name: String,
    @field:NotNull val value: String,
)
