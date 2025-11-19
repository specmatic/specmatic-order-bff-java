package com.component.orders.contract

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
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
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.PullPolicy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.time.Duration

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@EnabledIf(value = "isNonCIOrLinux", disabledReason = "Run only on Linux in CI; all platforms allowed locally")
class ContractTestsUsingTestContainer {
    companion object {
        private const val APPLICATION_HOST = "host.docker.internal"
        private const val APPLICATION_PORT = 8080
        private const val HTTP_STUB_PORT = 8090
        private const val ACTUATOR_MAPPINGS_ENDPOINT = "http://$APPLICATION_HOST:$APPLICATION_PORT/actuator/mappings"
        private const val EXCLUDED_ENDPOINTS = "'/health,/monitor/{id},/swagger/v1/swagger,/swagger-ui.html'"
        private const val KAFKA_PORT = 9092
        private const val KAFKA_MOCK_API_SERVER_PORT = 9999
        private const val EXPECTED_NUMBER_OF_MESSAGES = 10
        private val restTemplate: TestRestTemplate = TestRestTemplate()

        @JvmStatic
        fun isNonCIOrLinux(): Boolean = System.getenv("CI") != "true" || System.getProperty("os.name").lowercase().contains("linux")

        @Container
        private val stubContainer: GenericContainer<*> =
            GenericContainer("specmatic/specmatic-openapi")
                .withImagePullPolicy (PullPolicy.alwaysPull())
                .withCommand(
                    "virtualize",
                    "--examples=examples",
                    "--port=$HTTP_STUB_PORT",
                ).withCreateContainerCmdModifier { cmd ->
                    cmd.hostConfig?.withPortBindings(
                        PortBinding(Ports.Binding.bindPort(HTTP_STUB_PORT), ExposedPort(HTTP_STUB_PORT)),
                    )
                }.withExposedPorts(HTTP_STUB_PORT)
                .withFileSystemBind(
                    "./src/test/resources/domain_service",
                    "/usr/src/app/examples",
                    BindMode.READ_ONLY,
                ).withFileSystemBind(
                    "./src/test/resources/specmatic.yaml",
                    "/usr/src/app/specmatic.yaml",
                    BindMode.READ_ONLY,
                ).waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200))
                .withLogConsumer { print(it.utf8String) }

        @Container
        private val kafkaMockContainer: GenericContainer<*> =
            object : GenericContainer<Nothing>(
                "specmatic/specmatic-kafka",
            ) {
                override fun start() {
                    super.start()
                    // wait for container to stabilize and then set expectations
                    Thread.sleep(200)
                    setExpectations()
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
            }.apply {
                withImagePullPolicy (PullPolicy.alwaysPull())
                withCommand("virtualize")
                withCreateContainerCmdModifier { cmd ->
                    cmd.hostConfig?.withPortBindings(
                        PortBinding(
                            Ports.Binding.bindPort(KAFKA_MOCK_API_SERVER_PORT),
                            ExposedPort(KAFKA_MOCK_API_SERVER_PORT),
                        ),
                        PortBinding(Ports.Binding.bindPort(KAFKA_PORT), ExposedPort(KAFKA_PORT)),
                    )
                }
                withExposedPorts(KAFKA_MOCK_API_SERVER_PORT, KAFKA_PORT)
                withFileSystemBind(
                    "./src/test/resources/specmatic.yaml",
                    "/usr/src/app/specmatic.yaml",
                    BindMode.READ_ONLY,
                )
                waitingFor(
                    LogMessageWaitStrategy()
                        .withRegEx("(?i).*KafkaMock has started.*")
                        .withStartupTimeout(Duration.ofSeconds(30)),
                )
                withLogConsumer { print(it.utf8String) }
            }

        private val testContainer: GenericContainer<*> =
            GenericContainer("specmatic/specmatic-openapi")
                .withImagePullPolicy (PullPolicy.alwaysPull())
                .withCommand(
                    "test",
                    "--host=$APPLICATION_HOST",
                    "--port=$APPLICATION_PORT",
                    "--filter=PATH!=$EXCLUDED_ENDPOINTS",
                ).withEnv("endpointsAPI", ACTUATOR_MAPPINGS_ENDPOINT)
                .withFileSystemBind(
                    "./src/test/resources/bff",
                    "/usr/src/app/src/test/resources/bff",
                    BindMode.READ_ONLY,
                ).withFileSystemBind(
                    "./src/test/resources/specmatic.yaml",
                    "/usr/src/app/specmatic.yaml",
                    BindMode.READ_ONLY,
                ).withFileSystemBind(
                    "./build/reports/specmatic",
                    "/usr/src/app/build/reports/specmatic",
                    BindMode.READ_WRITE,
                ).waitingFor(Wait.forLogMessage(".*Tests run:.*", 1))
                .withExtraHost("host.docker.internal", "host-gateway")
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
