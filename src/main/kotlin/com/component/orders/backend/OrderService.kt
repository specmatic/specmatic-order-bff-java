package com.component.orders.backend

import com.component.orders.models.*
import com.component.orders.models.messages.ProductMessage
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.*
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

    private val restTemplateWithWarmupTimeout: RestTemplate by lazy {
        val restTemplate = RestTemplate()
        val requestFactory = SimpleClientHttpRequestFactory()
        requestFactory.setConnectTimeout(15000)
        requestFactory.setReadTimeout(15000)
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

    fun findProducts(type: ProductType, pageSize: Int, fromDate: LocalDate?, toDate: LocalDate?): List<Product> {
        val effectiveToDate = toDate ?: LocalDate.now().plusWeeks(1)
        val effectiveFromDate = fromDate ?: effectiveToDate.minusWeeks(1)

        val products = fetchProductsFromBackendAPI(type, pageSize, effectiveFromDate, effectiveToDate)

        products.take(1).forEach {
            val productMessage = ProductMessage(it.id, it.name, it.inventory)
            kafkaTemplate.send(productQueriesTopic, jacksonObjectMapper.writeValueAsString(productMessage))
        }
        return products
    }

    fun createProduct(newProduct: NewProduct): ResponseEntity<Id> {
        val response = createProductWith(restTemplateWithDefaultTimeout, newProduct)
        if (response.body == null) error("No product id received in Product API response.")
        return response
    }

    fun deleteProduct(productId: Int): ResponseEntity<Void> {
        val apiUrl = orderAPIUrl + "/" + API.DELETE_PRODUCT.url.replace("{id}", productId.toString())
        val requestEntity = HttpEntity<Void>(getHeaders())
        return restTemplateWithDefaultTimeout.exchange(
            apiUrl,
            API.DELETE_PRODUCT.method,
            requestEntity,
            Void::class.java,
        )
    }

    fun exerciseServiceChain(warmupProduct: NewProduct): Boolean {
        return try {
            // Create product using warmup timeout
            val createResponse = createProductWith(restTemplateWithWarmupTimeout, warmupProduct)
            val productId = requireProductId(createResponse, "No product id received in warmup response")

            // Delete product using warmup timeout
            deleteProductWith(restTemplateWithWarmupTimeout, productId)

            true
        } catch (e: Exception) {
            println("[OrderService] Service chain warmup failed: ${e.message}")
            false
        }
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

    private fun fetchProductsFromBackendAPI(type: ProductType, pageSize: Int, fromDate: LocalDate, toDate: LocalDate): List<Product> {
        val formattedFromDate = fromDate.format(DateTimeFormatter.ISO_DATE)
        val formattedToDate = toDate.format(DateTimeFormatter.ISO_DATE)
        val apiUrl = orderAPIUrl + "/" + API.LIST_PRODUCTS.url + "?type=$type&from-date=$formattedFromDate&to-date=$formattedToDate"
        val headers = getHeaders().apply { add("pageSize", pageSize.toString()) }
        val entity = HttpEntity<Any>(headers)
        val response =
            try {
                restTemplateWithDefaultTimeout.exchange(
                    apiUrl,
                    HttpMethod.GET,
                    entity,
                    object : ParameterizedTypeReference<List<Product>>() {},
                )
            } catch (e: Throwable) {
                println("Error while deserializing response from backend")
                println(e)
                println(e.stackTraceToString())
                throw e
            }

        val responseBody = response.body.orEmpty()

        val unexpectedTypes = responseBody.map { it.type }.filter { it != type }.distinct().map { it.name }

        if (unexpectedTypes.isNotEmpty()) {
            val unexpectedTypesCsv = unexpectedTypes.joinToString(", ")
            val message = "Expected products of type $type but received products with unexpected types: $unexpectedTypesCsv"

            println("[OrderService] $message")

            throw IllegalStateException(message)
        }

        responseBody.forEach { product ->
            product.createdOn?.let { createdOn ->
                if (createdOn.isBefore(fromDate) || createdOn.isAfter(toDate)) {
                    val message =
                        "The createdOn field is outside the specified date range: " +
                        "product id=${product.id}, createdOn=$createdOn, " +
                        "expected range: [$fromDate, $toDate]"

                    println("[OrderService] $message")

                    throw IllegalStateException(
                        message
                    )
                }
            }
        }

        return responseBody
    }

    private fun createProductWith(restTemplate: RestTemplate, newProduct: NewProduct): ResponseEntity<Id> {
        val apiUrl = orderAPIUrl + "/" + API.CREATE_PRODUCTS.url
        val headers = getHeaders().apply { add("Idempotency-Key", newProduct.idempotencyKey) }
        val requestEntity = HttpEntity(newProduct, headers)
        return restTemplate.exchange(
            apiUrl,
            API.CREATE_PRODUCTS.method,
            requestEntity,
            Id::class.java,
        )
    }

    private fun deleteProductWith(restTemplate: RestTemplate, productId: Int): ResponseEntity<Void> {
        val apiUrl = orderAPIUrl + "/" + API.DELETE_PRODUCT.url.replace("{id}", productId.toString())
        val requestEntity = HttpEntity<Void>(getHeaders())
        return restTemplate.exchange(
            apiUrl,
            API.DELETE_PRODUCT.method,
            requestEntity,
            Void::class.java,
        )
    }

    private fun requireProductId(response: ResponseEntity<Id>, errorMessage: String): Int {
        return response.body?.id ?: error(errorMessage)
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
