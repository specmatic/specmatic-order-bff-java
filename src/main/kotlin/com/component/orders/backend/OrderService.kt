package com.component.orders.backend

import com.component.orders.models.*
import com.component.orders.models.messages.ProductMessage
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.lang.IllegalStateException
import java.util.*

@Service
class OrderService(
    private val jacksonObjectMapper: ObjectMapper,
    private val kafkaTemplate: KafkaTemplate<String, String>
) {
    private val authToken = "API-TOKEN-SPEC"

    @Value("\${order.api.url}")
    lateinit var orderAPIUrl: String

    @Value("\${kafka.topic.product-queries}")
    lateinit var productQueriesTopic: String

    fun createOrder(orderRequest: OrderRequest): Int {
        val apiUrl = orderAPIUrl + "/" + API.CREATE_ORDER.url
        val order = Order(orderRequest.productid, orderRequest.count, "pending")
        val headers = getHeaders()
        val requestEntity = HttpEntity(order, headers)
        val response = RestTemplate().exchange(
            apiUrl,
            API.CREATE_ORDER.method,
            requestEntity,
            String::class.java
        )
        if (response.body == null) {
            error("No order id received in Order API response.")
        }
        val responseBody = jacksonObjectMapper.readValue(response.body, Map::class.java) as Map<String, Any>
        val orderId = (responseBody["id"] as Number).toInt()
        sendNewOrdersEvent(orderId)
        return orderId
    }

    fun findProducts(type: String): List<Product> {
        val products = fetchFirstProductFromBackendAPI(type)
        products.forEach {
            val productMessage = ProductMessage(it.id, it.name, it.inventory)
            kafkaTemplate.send(productQueriesTopic, jacksonObjectMapper.writeValueAsString(productMessage))
        }
        return products
    }

    fun createProduct(newProduct: NewProduct): Int {
        val apiUrl = orderAPIUrl + "/" + API.CREATE_PRODUCTS.url
        val headers = getHeaders()
        val requestEntity = HttpEntity(newProduct, headers)
        val response = RestTemplate().exchange(
            apiUrl,
            API.CREATE_PRODUCTS.method,
            requestEntity,
            String::class.java
        )
        if (response.body == null) {
            error("No product id received in Product API response.")
        }
        val responseBody = jacksonObjectMapper.readValue(response.body, Map::class.java) as Map<String, Any>
        return (responseBody["id"] as Number).toInt()
    }

    @KafkaListener(topics = ["wip-orders"])
    fun run(wipOrder: String) {
        println("[OrderService] Received message on topic 'wip-orders' - $wipOrder")

        val wipOrderMap = try {
            ObjectMapper().apply {
                configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
            }.readValue(wipOrder, Map::class.java)
        } catch(e: Exception) {
            throw e
        }
        initiateOrderDelivery(wipOrderMap.get("id") as Int)
    }

    private fun getHeaders(): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set("Authenticate", authToken)
        return headers
    }

    private fun fetchFirstProductFromBackendAPI(type: String): List<Product> {
        val apiUrl = orderAPIUrl + "/" + API.LIST_PRODUCTS.url + "?type=$type"
        val restTemplate = RestTemplate()
        val requestFactory = SimpleClientHttpRequestFactory()
        requestFactory.setConnectTimeout(4000)
        requestFactory.setReadTimeout(4000)
        restTemplate.setRequestFactory(requestFactory)
        val response = restTemplate.getForEntity(apiUrl, List::class.java)
        (response.body as List<*>).any { (it as Map<String, *>)["type"] != type }.let {
            if (it) {
                throw IllegalStateException("Product type mismatch")
            }
        }
        return response.body.take(1).map {
            val product = it as Map<*, *>
            Product(
                product["name"].toString(),
                product["type"].toString(),
                product["inventory"].toString().toInt(),
                product["id"].toString().toInt(),
            )
        }
    }

    private fun sendNewOrdersEvent(orderId: Int) {
        println("[OrderService] Sending NewOrdersEvent for orderId '$orderId' on new-orders topic")
        kafkaTemplate.send(
            "new-orders",
            ObjectMapper().writeValueAsString(
                NewOrderEvent.from(orderId)
            )
        )
    }

    private fun initiateOrderDelivery(orderId: Int) {
        println("[OrderService] Initiating order delivery  for orderId '$orderId'")
        kafkaTemplate.send(
            "out-for-delivery-orders",
            ObjectMapper().writeValueAsString(
                InitiateOrderDeliveryEvent.from(orderId)
            )
        )
    }
}
