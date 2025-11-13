package com.component.orders.backend

import com.component.orders.models.*
import com.component.orders.models.messages.ProductMessage
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.lang.IllegalStateException
import java.util.*

@Service
class OrderService(private val jacksonObjectMapper: ObjectMapper) {
    private val authToken = "API-TOKEN-SPEC"

    @Value("\${order.api.url}")
    lateinit var orderAPIUrl: String

    @Value("\${kafka.bootstrap-servers}")
    lateinit var kafkaBootstrapServers: String

    @Value("\${kafka.topic}")
    lateinit var kafkaTopic: String

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
        return response
    }

    fun findProducts(type: ProductType, pageSize: Int): List<Product> {
        val products = fetchProductsFromBackendAPI(type, pageSize).take(1)
        val producer = getKafkaProducer()

        products.forEach {
            val productMessage = ProductMessage(it.id, it.name, it.inventory)
            producer.send(ProducerRecord(
                kafkaTopic,
                jacksonObjectMapper.writeValueAsString(productMessage),
            ))
        }

        producer.close()
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

    private fun getKafkaProducer(): KafkaProducer<String, String> {
        val props = Properties()
        println("kafkaBootstrapServers: $kafkaBootstrapServers")
        props["bootstrap.servers"] = kafkaBootstrapServers
        props["key.serializer"] = "org.apache.kafka.common.serialization.StringSerializer"
        props["value.serializer"] = "org.apache.kafka.common.serialization.StringSerializer"
        return KafkaProducer<String, String>(props)
    }
}
