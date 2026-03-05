package com.component.orders.contract

import com.fasterxml.jackson.databind.ObjectMapper
import io.specmatic.async.mock.model.Expectation
import io.specmatic.async.mock.model.ExpectationsRequest
import io.specmatic.enterprise.SpecmaticContractTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import java.net.URI

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class ContractTests : SpecmaticContractTest {
    companion object {
        private const val EXPECTED_NUMBER_OF_MESSAGES = 11
        private const val STUB_BASE_URL = "http://localhost:8090"
        private val restTemplate = TestRestTemplate()
        private val objectMapper = ObjectMapper()

        private fun setExpectations() {
            println("Setting expectations on kafka topic(s)..")
            val expectationsRequest = ExpectationsRequest(
                expectations = listOf(
                    Expectation("product-queries", EXPECTED_NUMBER_OF_MESSAGES)
                )
            )
            SpecmaticContractTest.setExpectationsOnAsyncMock(expectationsRequest)
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
            assertTimeoutRequestsForwarding()
            assertKafkaExpectations()
        }

        private fun assertKafkaExpectations() {
            println("Verifying expectations set on kafka topic(s)..")
            val asyncMockVerificationResult = SpecmaticContractTest.verifyExpectationsSetOnAsyncMock()

            println("Expectations verification result:")
            when (asyncMockVerificationResult.success) {
                true -> println("Expectations were met successfully!")
                false -> {
                    val errors = asyncMockVerificationResult.errors
                    fail<String>("Expectations were not met. Reason(s): ${errors.joinToString(", ")}")
                }
            }
        }

        private fun assertTimeoutRequestsForwarding() {
            val response = restTemplate.exchange(
                URI("$STUB_BASE_URL/_specmatic/messages?REQUEST.PATH=/products&REQUEST.METHOD=GET"),
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String::class.java
            )

            assertThat(response.body)
                .withFailMessage("Expected _specmatic/messages response body to be present")
                .isNotNull

            val messagesRoot = objectMapper.readTree(response.body)
            val timeOutRequestsCount = messagesRoot.count { message ->
                val request = message.path("http-request")
                val headers = request.path("headers")
                val hasPageSize20 = headers.fieldNames().asSequence().any { key -> key.equals("pageSize", ignoreCase = true) && headers.path(key).asText() == "20" }
                val typeValue = request.path("query").path("type").asText()
                val hasTypeOther = typeValue == "other" || typeValue.contains("exact:other")
                hasPageSize20 && hasTypeOther
            }

            assertThat(timeOutRequestsCount)
                .withFailMessage("Expected 3 /products GET messages to have header pageSize=20 and query.type=other but found $timeOutRequestsCount")
                .isEqualTo(3)
        }
    }
}
