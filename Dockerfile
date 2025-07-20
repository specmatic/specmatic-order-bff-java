FROM amazoncorretto:17.0.16-alpine

RUN apk add --no-cache git curl bash && \
    rm -rf /var/cache/apk/*

SHELL ["/bin/bash", "-c"]

WORKDIR /app

EXPOSE 8080

COPY build/libs/specmatic-order-bff-java-all.jar /app/order-bff.jar

CMD java -jar order-bff.jar
