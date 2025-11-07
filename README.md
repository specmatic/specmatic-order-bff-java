# Specmatic Sample: SpringBoot BFF calling Domain API

* [Specmatic Website](https://specmatic.io)
* [Specmatic Documentation](https://docs.specmatic.io)

This sample project demonstrates how we can practice contract-driven development and contract testing in a SpringBoot (Kotlin) application that depends on an external domain service and Kafka. Here, Specmatic is used to stub calls to domain API service based on its OpenAPI spec and mock Kafka based on its AsyncAPI spec.

Here is the domain api [contract/open api spec](https://github.com/specmatic/specmatic-order-contracts/blob/main/io/specmatic/examples/store/openapi/api_order_v3.yaml)

Here is the [AsyncAPI spec](https://github.com/specmatic/specmatic-order-contracts/blob/main/io/specmatic/examples/store/asyncapi/kafka.yaml) of Kafka that defines the topics and message schema.

## Definitions
* BFF: Backend for Front End
* Domain API: API managing the domain model
* Specmatic Stub/Mock Server: Create a server that can act as a real service using its OpenAPI or AsyncAPI spec

## Background
A typical web application might look like this. We can use Specmatic to practice contract-driven development and test all the components mentioned below. In this sample project, we look at how to do this for nodejs BFF which is dependent on Domain API Service and Kafka demonstrating both OpenAPI and AsyncAPI support in specmatic.

![HTML client talks to client API which talks to backend API](assets/specmatic-order-bff-architecture.gif)

## Tech
1. Spring boot
2. Specmatic
3. Specmatic-Kafka
4. Docker Desktop (to run contract test and mock servers using test containers)

## Run Tests

This will start the specmatic stub server for domain api and kafka mock using the information in specmatic.yaml and run contract tests using Specmatic.

### 1. Using Gradle:

For Unix based systems and Windows Powershell:
```shell
./gradlew test
```

For Windows Command Prompt:
```shell
gradlew test
```

### 2. Using Docker:

For Unix based systems and Windows Powershell, execute the following one-by-one in separate terminals:
```shell
# Start the backend service
./gradlew bootRun

# Start the domain api mock server
docker run --rm -p 8090:9000 -v "$(pwd)/src/test/resources/specmatic.yaml:/usr/src/app/specmatic.yaml" -v "$(pwd)/src/test/resources/domain_service:/usr/src/app/domain_service" specmatic/specmatic virtualize --examples /usr/src/app/domain_service

# Start the kafka mock server
docker run --rm -p 9092:9092 -p 2181:2181 -v "$(pwd)/src/test/resources/specmatic.yaml:/usr/src/app/specmatic.yaml" specmatic/specmatic-kafka virtualize

# Run contract tests
docker run --rm --network host -v "$(pwd)/src/test/resources/specmatic.yaml:/usr/src/app/specmatic.yaml" -v "$(pwd)/src/test/resources/bff:/usr/src/app/bff" -v "$(pwd)/build/reports/specmatic:/usr/src/app/build/reports/specmatic" specmatic/specmatic test --port=8080 --examples /usr/src/app/bff
```

For Windows Command Prompt, execute the following one-by-one in separate terminals:
```shell
# Start the backend service
gradlew bootRun

# Start the domain api mock server
docker run --rm -p 8090:9000 -v "%cd%/src/test/resources/specmatic.yaml:/usr/src/app/specmatic.yaml" -v "%cd%/src/test/resources/domain_service:/usr/src/app/domain_service" specmatic/specmatic virtualize --examples /usr/src/app/domain_service

# Start the kafka mock server
docker run --rm -p 9092:9092 -p 2181:2181 -v "%cd%/src/test/resources/specmatic.yaml:/usr/src/app/specmatic.yaml" specmatic/specmatic-kafka virtualize

# Run contract tests
docker run --rm --network host -v "%cd%/src/test/resources/specmatic.yaml:/usr/src/app/specmatic.yaml" -v "%cd%/src/test/resources/bff:/usr/src/app/bff" -v "%cd%/build/reports/specmatic:/usr/src/app/build/reports/specmatic" specmatic/specmatic test --port=8080 --examples /usr/src/app/bff
```

## Starting Service and Mocks for manual testing
 
### Start the dependent components

Follow the instructions provided in the [Run Tests](#2-using-docker) using Docker, but skip the `Run contract tests` step

### Test if everything is working
```shell
curl -H "pageSize: 10" "http://localhost:8080/findAvailableProducts"
```

Your result should look like:
```json
[{"id":698,"name":"NUBYR","type":"book","inventory":278}]
```

Also observe the logs in the Specmatic HTTP Stub Server and Specmatic Kafka Mock Server.
