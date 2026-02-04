package com.component.orders.contract

import io.specmatic.async.mock.model.Expectation
import io.specmatic.async.mock.model.ExpectationsRequest
import io.specmatic.enterprise.SpecmaticContractTest
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@EnabledIf(value = "isNonCIOrLinux", disabledReason = "Run only on Linux in CI; all platforms allowed locally")
@Disabled("Enable once fixed")
class ContractTestUsingCLITest {
    @Test
    @Throws(Exception::class)
    fun specmaticContractTest() {
        test.start()
        test.verifySuccessfulExecutionWithNoFailures()
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

    companion object {
        private const val EXPECTED_NUMBER_OF_MESSAGES = 11

        @JvmStatic
        fun isNonCIOrLinux(): Boolean =
            System.getenv("CI") != "true" || System.getProperty("os.name").lowercase().contains("linux")

        private val mock: SpecmaticExecutor = SpecmaticExecutor("mock")
        private val test: SpecmaticExecutor = SpecmaticExecutor("test")

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
        @Throws(Exception::class)
        fun setup() {
            mock.start()
            Thread.sleep(20000)
            setExpectations()
        }

        @JvmStatic
        @AfterAll
        @Throws(Exception::class)
        fun teardown() {
            test.stop()
            mock.stop()
        }
    }
}
