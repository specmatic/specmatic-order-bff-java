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
import java.time.LocalDateTime
import java.time.ZoneId
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

    fun findProducts(type: ProductType, pageSize: Int, fromDate: LocalDateTime?, toDate: LocalDateTime?): List<Product> {
        val effectiveToDate = toDate ?: LocalDateTime.now().plusWeeks(1)
        val effectiveFromDate = fromDate ?: effectiveToDate.minusWeeks(1)

        val products = fetchProductsFromBackendAPI(type, pageSize, effectiveFromDate, effectiveToDate).take(1)

        products.forEach {
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

    private fun fetchProductsFromBackendAPI(type: ProductType, pageSize: Int, fromDate: LocalDateTime, toDate: LocalDateTime): List<Product> {
        val formattedFromDate = fromDate.format(DateTimeFormatter.ISO_DATE_TIME)
        val formattedToDate = toDate.format(DateTimeFormatter.ISO_DATE_TIME)
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
                println(e)
                throw e
            }

        response.body?.forEach {
            if (it.type != type)
                throw IllegalStateException("Product type mismatch, expected all products to have type: $type")
        }

        response.body?.forEach { product ->
            product.createdOn?.let { createdOn ->
                val offsetToDate = toDate.atZone(ZoneId.systemDefault()).toOffsetDateTime();
                val offsetFromDate = fromDate.atZone(ZoneId.systemDefault()).toOffsetDateTime();

                if (createdOn.isBefore(offsetFromDate) || createdOn.isAfter(offsetToDate)) {
                    throw IllegalStateException(
                        "Product createdOn is outside the specified date range: " +
                                "product id=${product.id}, createdOn=$createdOn, " +
                                "expected range: [$fromDate, $toDate]"
                    )
                }
            }
        }


        return response.body ?: emptyList()
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
