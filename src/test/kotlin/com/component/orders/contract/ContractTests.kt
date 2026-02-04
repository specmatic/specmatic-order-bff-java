package com.component.orders.contract

import io.specmatic.async.mock.model.Expectation
import io.specmatic.async.mock.model.ExpectationsRequest
import io.specmatic.enterprise.SpecmaticContractTest
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.springframework.boot.test.context.SpringBootTest


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class ContractTests : SpecmaticContractTest {
    companion object {
        private const val EXPECTED_NUMBER_OF_MESSAGES = 11

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
    }
}
