FROM amazoncorretto:17.0.17-alpine

RUN apk add --no-cache git curl bash jq && \
    rm -rf /var/cache/apk/*

SHELL ["/bin/bash", "-c"]

WORKDIR /app

EXPOSE 8080

COPY build/libs/specmatic-order-bff-java-all.jar /app/order-bff.jar

CMD java -jar order-bff.jar
