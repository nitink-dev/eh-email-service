# Use the official OpenJDK 17 image as the base image
FROM amazoncorretto:17-alpine3.22

# Set the working directory inside the container
WORKDIR /app

# Copy the Spring Boot jar file into the container
COPY target/*.jar /app/email-svc.jar

EXPOSE 8093

ENTRYPOINT ["java", "-jar", "/app/email-svc.jar"]