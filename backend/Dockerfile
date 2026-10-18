# Multi-stage build: Maven + JDK 21 to package, JRE 21 to run as a non-root user.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 codetrace
COPY --from=build /app/target/codetrace-backend.jar app.jar
USER codetrace
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
