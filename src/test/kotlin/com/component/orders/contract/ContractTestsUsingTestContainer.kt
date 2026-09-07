package com.component.orders.contract

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.boot.test.context.SpringBootTest
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@EnabledIf(value = "isNonCIOrLinux", disabledReason = "Run only on Linux in CI; all platforms allowed locally")
class ContractTestsUsingTestContainer {
    companion object {
        @JvmStatic
        fun isNonCIOrLinux(): Boolean =
            System.getenv("CI") != "true" || System.getProperty("os.name").lowercase().contains("linux")

        private fun hostId(option: String): String {
            val process = ProcessBuilder("id", option).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            check(process.waitFor() == 0 && output.matches(Regex("\\d+"))) {
                "Could not determine host identity using id $option: $output"
            }
            return output
        }

        private val hostUser = "${hostId("-u")}:${hostId("-g")}"

        private fun mockContainerWithSetExpectations(): GenericContainer<*> = object : GenericContainer<Nothing>(
                "specmatic/enterprise"
        ) {
            override fun start() {
                super.start()
            }

            override fun stop() {
                // CLI equivalent of - docker stop --time 300 <containerId>
                dockerClient.stopContainerCmd(containerId).withTimeout(300).exec()
                super.stop()
            }
        }

        @Container
        private val mockContainer: GenericContainer<*> =
            mockContainerWithSetExpectations()
                .withEnv(System.getenv())
                .withReuse(false)
                .withCommand("mock", "--metadata=run_mode=docker")
                .withFileSystemBind("${System.getProperty("user.home")}/.specmatic", "/specmatic", BindMode.READ_ONLY)
                .withFileSystemBind(".", "/usr/src/app", BindMode.READ_WRITE)
                .withWorkingDirectory("/usr/src/app")
                .withCreateContainerCmdModifier { it.withUser(hostUser) }
                .withEnv("SPECMATIC_LICENSE_PATH", "/specmatic/specmatic-license.txt")
                .withNetworkMode("host")
                .withEnv("JAVA_OPTS", "-Dspecmatic.logging.level=trace -Dspecmatic.logging.stdout.enabled=true")
                .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200))
                .withLogConsumer { print(it.utf8String) }


        private val testContainer: GenericContainer<*> =
            GenericContainer("specmatic/enterprise")
                .withEnv(System.getenv())
                .withCommand("test", "--metadata=run_mode=docker")
                .withFileSystemBind("${System.getProperty("user.home")}/.specmatic", "/specmatic", BindMode.READ_ONLY)
                .withFileSystemBind(".", "/usr/src/app", BindMode.READ_WRITE)
                .withWorkingDirectory("/usr/src/app")
                .withCreateContainerCmdModifier { it.withUser(hostUser) }
                .withEnv("SPECMATIC_LICENSE_PATH", "/specmatic/specmatic-license.txt")
                .withEnv("JAVA_OPTS", "-Dspecmatic.logging.level=trace -Dspecmatic.logging.stdout.enabled=true")
                .withNetworkMode("host")
                .waitingFor(
                    Wait.forLogMessage(".*Tests run:.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(2))
                )
                .withLogConsumer { print(it.utf8String) }
    }

    @Test
    fun specmaticContractTest() {
        testContainer.start()
        val hasSucceeded = testContainer.logs.contains("Failures: 0")
        assertThat(hasSucceeded).isTrue()
    }
}
