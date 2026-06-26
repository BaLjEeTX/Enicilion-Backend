# Build stage
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /workspace/app

COPY pom.xml .
COPY src src

RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/app/target/backend-1.0.0.jar app.jar
EXPOSE 4000
ENTRYPOINT ["java", "-jar", "app.jar"]
