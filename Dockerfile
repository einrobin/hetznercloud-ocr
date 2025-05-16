# Build stage
FROM openjdk:21 AS build

# Copy files into the container
WORKDIR /gradle
COPY . .

# Build gradle project
RUN chmod +x gradlew
RUN ./gradlew build shadowJar --no-daemon --full-stacktrace

# Final stage
FROM alpine:latest

# Install JRE 21
RUN apk update && apk add openjdk21-jre

# Install the Jar from the Build stage
WORKDIR /app
COPY --from=build /gradle/build/libs/ocr.jar /app/app.jar

CMD ["java", "-jar", "app.jar"]
