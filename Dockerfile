FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy pre-built jar from target directory
COPY target/backend-1.0.0.jar app.jar

# Expose Spring Boot server port
EXPOSE 4000

# Run the application jar
ENTRYPOINT ["java", "-jar", "app.jar"]
