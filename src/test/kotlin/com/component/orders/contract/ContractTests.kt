package com.component.orders.contract

import io.specmatic.enterprise.SpecmaticContractTest
import io.specmatic.enterprise.core.VersionInfo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContractTests : SpecmaticContractTest() {
    companion object {
        //        private lateinit var httpStub: ContractStub
//        private lateinit var kafkaMock: AsyncMock
//        private const val APPLICATION_HOST = "localhost"
//        private const val APPLICATION_PORT = "8080"

        //        private const val HTTP_STUB_HOST = "localhost"
//        private const val HTTP_STUB_PORT = 8090
//        private const val KAFKA_MOCK_HOST = "localhost"
//        private const val KAFKA_MOCK_PORT = 9092
//        private const val EXPECTED_NUMBER_OF_MESSAGES = 11
//        private const val EXCLUDED_ENDPOINTS = "'/health,/monitor/{id}'"

        //
//        @JvmStatic
//        @BeforeAll
//        fun setUp() {
//            System.setProperty("host", APPLICATION_HOST)
//            System.setProperty("port", APPLICATION_PORT)
//            System.setProperty("filter", "PATH!=$EXCLUDED_ENDPOINTS")
//        }
//            // Start Specmatic Http Stub and set the expectations
//            httpStub = createStub(listOf("./src/test/resources/domain_service"), HTTP_STUB_HOST, HTTP_STUB_PORT)
//
//            // Start Specmatic Kafka Mock and set the expectations
//            kafkaMock = AsyncMock.startInMemoryBroker(KAFKA_MOCK_HOST, KAFKA_MOCK_PORT)
//            Thread.sleep(2000)
//            kafkaMock.setExpectations(listOf(Expectation("product-queries", EXPECTED_NUMBER_OF_MESSAGES)))
//        }
//
//        @JvmStatic
//        @AfterAll
//        fun tearDown() {
//            // Shutdown Specmatic Http Stub
//            httpStub.close()
//
//            val result = kafkaMock.stop()
//            assertThat(result.success).withFailMessage(result.errors.joinToString()).isTrue
//        }
//    }
    }
}
