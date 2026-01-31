package com.component.orders.models

import org.springframework.http.HttpMethod

enum class API(val method: HttpMethod, val url: String) {
    CREATE_ORDER(HttpMethod.POST, "/orders"),
    LIST_PRODUCTS(HttpMethod.GET, "/products"),
    CREATE_PRODUCTS(HttpMethod.POST, "/products"),
    DELETE_PRODUCT(HttpMethod.DELETE, "/products/{id}")
}
