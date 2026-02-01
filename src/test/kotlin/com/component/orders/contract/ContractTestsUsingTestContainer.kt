package com.component.orders.contract

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.*
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.time.Duration

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@EnabledIf(value = "isNonCIOrLinux", disabledReason = "Run only on Linux in CI; all platforms allowed locally")
class ContractTestsUsingTestContainer {
    companion object {
        private const val APP_URL = "http://localhost:8080"
        private const val KAFKA_MOCK_API_SERVER_PORT = 9999
        private const val EXPECTED_NUMBER_OF_MESSAGES = 11
        private val restTemplate: TestRestTemplate = TestRestTemplate()

        @JvmStatic
        fun isNonCIOrLinux(): Boolean =
            System.getenv("CI") != "true" || System.getProperty("os.name").lowercase().contains("linux")

        @Container
        private val mockContainer: GenericContainer<*> =
            object : GenericContainer<Nothing>(
                "specmatic/enterprise",
            ) {
                override fun start() {
                    super.start()
                    // wait for container to stabilize and then set expectations
                    Thread.sleep(20000)
                    setExpectations()
                }

                override fun stop() {
                    dumpReports()
                    super.stop()
                }

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

                private fun dumpReports() {
                    println("Dumping kafka mock reports..")
                    val response: ResponseEntity<String> =
                        restTemplate.exchange(
                            URI("http://localhost:$KAFKA_MOCK_API_SERVER_PORT/stop"),
                            HttpMethod.POST,
                            HttpEntity(""),
                            String::class.java,
                        )
                    if (response.statusCode == HttpStatusCode.valueOf(200)) {
                        println("Reports dumped successfully!")
                    } else {
                        println("Error occurred while dumping the reports")
                    }
                }
            }.apply {
                withCommand("mock")
                withFileSystemBind(".", "/usr/src/app", BindMode.READ_WRITE)
                withNetworkMode("host")
                waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200))
                withLogConsumer { print(it.utf8String) }
            }

        private val testContainer: GenericContainer<*> =
            GenericContainer("specmatic/enterprise")
                .withCommand("test")
                .withEnv("APP_URL", APP_URL)
                .withFileSystemBind(".", "/usr/src/app", BindMode.READ_WRITE)
                .withNetworkMode("host")
                .waitingFor(
                    Wait.forLogMessage(".*Tests run:.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(2))
                )
                .withLogConsumer { print(it.utf8String) }

        @AfterAll
        @JvmStatic
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

    @Test
    fun specmaticContractTest() {
        testContainer.start()
        val hasSucceeded = testContainer.logs.contains("Failures: 0")
        assertThat(hasSucceeded).isTrue()
    }
}
