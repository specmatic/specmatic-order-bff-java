package com.component.orders

import com.fasterxml.jackson.databind.ObjectMapper
import io.specmatic.async.mock.AsyncMock
import io.specmatic.enterprise.core.VersionInfo
import io.specmatic.stub.ContractStub
import io.specmatic.stub.createStub
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.*
import java.io.File

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class ApiTests {
    companion object {
        private lateinit var httpStub: ContractStub
        private lateinit var kafkaMock: AsyncMock

        private const val STUB_PORT = 8090

        @BeforeAll
        @JvmStatic
        fun setUp() {
            println("Using specmatic enterprise - ${VersionInfo.describe()}")
            // Start Specmatic Http Stub
            httpStub = createStub("localhost", STUB_PORT, strict = true)

            // Start kafka mock
            kafkaMock = AsyncMock.startInMemoryBroker("localhost", 9092)
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            // Shutdown Specmatic Http Stub
            httpStub.close()

            // Stop kafka mock
            kafkaMock.stop()
        }
    }

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private fun setExpectations(expectation: String) {
        val url = "http://localhost:$STUB_PORT/_specmatic/expectations"
        val response: ResponseEntity<String> = restTemplate.exchange(
            url,
            HttpMethod.POST,
            HttpEntity(expectation.toMap()),
            String::class.java
        )
        assert(response.statusCode == HttpStatus.OK)
    }

    @Test
    fun `should search for available products`() {
        val expectation = File("src/test/resources/domain_service/stub_products_200.json").readText()
        setExpectations(expectation)

        val url = "http://localhost:8080/findAvailableProducts?type=gadget&from-date=2025-11-23&to-date=2025-11-25"
        val headers = HttpHeaders().apply {
            set("pageSize", "10")
        }
        val response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            HttpEntity<String>(headers),
            List::class.java
        )

        assert(response.statusCode == HttpStatus.OK)

        val actualResponseBody = response.body as List<Map<String, Any>>
        val expectedProduct = mapOf("name" to "iPhone", "type" to "gadget", "id" to 10)
        val actualProduct = actualResponseBody.single()

        assertThat(actualProduct["name"]).isEqualTo(expectedProduct["name"])
        assertThat(actualProduct["type"]).isEqualTo(expectedProduct["type"])
        assertThat(actualProduct["id"]).isEqualTo(expectedProduct["id"])
    }

    private fun String.toMap(): Map<*, *> {
        return ObjectMapper().readValue(this, Map::class.java)
    }
}
