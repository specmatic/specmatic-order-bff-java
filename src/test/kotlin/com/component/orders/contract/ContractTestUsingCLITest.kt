package com.component.orders.contract

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@EnabledIf(value = "isNonCIOrLinux", disabledReason = "Run only on Linux in CI; all platforms allowed locally")
class ContractTestUsingCLITest {
    @Test
    @Throws(Exception::class)
    fun specmaticContractTest() {
        test.start()
        test.verifySuccessfulExecutionWithNoFailures()
    }

    companion object {
        @JvmStatic
        fun isNonCIOrLinux(): Boolean =
            System.getenv("CI") != "true" || System.getProperty("os.name").lowercase().contains("linux")

        private val mock: SpecmaticExecutor = SpecmaticExecutor("mock")
        private val test: SpecmaticExecutor = SpecmaticExecutor("test")

        @JvmStatic
        @BeforeAll
        @Throws(Exception::class)
        fun setup() {
            mock.start()
            Thread.sleep(20000)
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
