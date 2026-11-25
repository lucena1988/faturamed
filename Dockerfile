FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# O Maven Central limita downloads em rajada (HTTP 429) dos runners compartilhados do CI.
# O resolver tenta de novo com espera crescente, e o passo inteiro e repetido se ainda falhar.
ENV MAVEN_ARGS="-B -Daether.connector.http.retryHandler.count=8 -Daether.connector.http.retryHandler.interval=10000 -Daether.connector.http.retryHandler.serviceUnavailable=429,503 -Daether.connector.basic.threads=2"
COPY pom.xml .
RUN mvn dependency:go-offline \
    || (echo "Nova tentativa em 60s" && sleep 60 && mvn dependency:go-offline) \
    || (echo "Nova tentativa em 120s" && sleep 120 && mvn dependency:go-offline)
COPY src ./src
RUN mvn package -DskipTests \
    || (echo "Nova tentativa em 60s" && sleep 60 && mvn package -DskipTests)

FROM eclipse-temurin:21-jre
# OCR local das folhas de producao (Tesseract + idioma portugues)
RUN apt-get update && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-por && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/faturamed-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
