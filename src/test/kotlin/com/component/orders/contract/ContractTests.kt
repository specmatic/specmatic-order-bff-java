package com.component.orders.contract

import io.specmatic.enterprise.SpecmaticContractTest
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.net.URI

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class ContractTests : SpecmaticContractTest() {
    companion object {
        private const val KAFKA_MOCK_API_SERVER_PORT = 9999
        private const val EXPECTED_NUMBER_OF_MESSAGES = 11
        private val restTemplate: TestRestTemplate = TestRestTemplate()

        private fun setExpectations() {
            println("Setting expectations on kafka topic(s)..")
            val response: ResponseEntity<String> =
                restTemplate.exchange(
                    URI("http://localhost:$KAFKA_MOCK_API_SERVER_PORT/_expectations"),
                    HttpMethod.POST,
                    HttpEntity(
                        """
                                {
                                    "expectations": [
                                        {
                                          "topic": "product-queries",
                                          "count": $EXPECTED_NUMBER_OF_MESSAGES
                                        }
                                    ]
                                }
                                """.trimIndent(),
                        HttpHeaders().apply {
                            contentType = MediaType.APPLICATION_JSON
                        },
                    ),
                    String::class.java,
                )
            if (response.statusCode == HttpStatusCode.valueOf(200)) {
                println("Expectations set successfully!")
            } else {
                println("Expectations setting failed: ${response.body}")
            }
        }

        @JvmStatic
        @BeforeAll
        fun setUp() {
            Thread.sleep(20000)
            setExpectations()
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            println("Verifying expectations set on kafka topic(s)..")
            val response: Map<String, Any>? =
                restTemplate
                    .exchange(
                        URI("http://localhost:$KAFKA_MOCK_API_SERVER_PORT/_expectations/verification_status"),
                        HttpMethod.GET,
                        HttpEntity.EMPTY,
                        object : ParameterizedTypeReference<Map<String, Any>>() {},
                    ).body

            println("Expectations verification result:")
            when (response?.get("success")) {
                null -> fail<String>("Expectations verification failed. The expectations may not be set up correctly.")
                true -> println("Expectations were met successfully!")
                false -> {
                    val errors = response["errors"] as? List<*> ?: emptyList<String>()
                    fail<String>("Expectations were not met. Reason(s): ${errors.joinToString(", ")}")
                }
            }
        }
    }
}
