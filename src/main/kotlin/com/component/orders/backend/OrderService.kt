package com.component.orders.backend

import com.component.orders.models.*
import com.component.orders.models.messages.ProductMessage
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
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

    private val restTemplateWithDefaultTimeout: RestTemplate by lazy {
        val restTemplate = RestTemplate()
        val requestFactory = SimpleClientHttpRequestFactory()
        requestFactory.setConnectTimeout(1000)
        requestFactory.setReadTimeout(1000)
        restTemplate.setRequestFactory(requestFactory)
        restTemplate
    }

    fun createOrder(orderRequest: OrderRequest): ResponseEntity<Id> {
        val apiUrl = orderAPIUrl + "/" + API.CREATE_ORDER.url
        val headers = getHeaders().apply { add("Idempotency-Key", orderRequest.orderIdempotencyKey) }
        val requestEntity = HttpEntity(orderRequest, headers)
        val response = restTemplateWithDefaultTimeout.exchange(
            apiUrl,
            API.CREATE_ORDER.method,
            requestEntity,
            Id::class.java,
        )

        if (response.body == null) error("No order id received in Order API response.")
        sendNewOrdersEvent(response.body.id)
        return response
    }

    fun findProducts(type: ProductType, pageSize: Int): List<Product> {
        val products = fetchProductsFromBackendAPI(type, pageSize)
        products.take(1).forEach {
            val productMessage = ProductMessage(it.id, it.name, it.inventory)
            kafkaTemplate.send(productQueriesTopic, jacksonObjectMapper.writeValueAsString(productMessage))
        }
        return products
    }

    fun createProduct(newProduct: NewProduct): ResponseEntity<Id> {
        val apiUrl = orderAPIUrl + "/" + API.CREATE_PRODUCTS.url
        val headers = getHeaders().apply { add("Idempotency-Key", newProduct.idempotencyKey) }
        val requestEntity = HttpEntity(newProduct, headers)
        val response = restTemplateWithDefaultTimeout.exchange(
            apiUrl,
            API.CREATE_PRODUCTS.method,
            requestEntity,
            Id::class.java,
        )

        if (response.body == null) error("No product id received in Product API response.")
        return response
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

    private fun fetchProductsFromBackendAPI(type: ProductType, pageSize: Int): List<Product> {
        val apiUrl = orderAPIUrl + "/" + API.LIST_PRODUCTS.url + "?type=$type"
        val headers = getHeaders().apply { add("pageSize", pageSize.toString()) }
        val entity = HttpEntity<Any>(headers)
        val response = restTemplateWithDefaultTimeout.exchange(
            apiUrl,
            HttpMethod.GET,
            entity,
            object : ParameterizedTypeReference<List<Product>>() {},
        )

        response.body?.forEach {
            if (it.type != type) throw IllegalStateException("Product type mismatch, expected all products to have type: $type")
        }

        return response.body
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
