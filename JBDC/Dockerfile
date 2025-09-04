FROM maven:3.9.9-eclipse-temurin-21-noble AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/app.jar app.jar

ENV TCP_PORT=9090 \
    DB_HOST=mysql \
    DB_PORT=3306 \
    DB_NAME=testdb \
    DB_USER=root \
    DB_PASS=123456 \
    BIND_ADDR=0.0.0.0

EXPOSE 9090
CMD ["java", "-jar", "app.jar"]
