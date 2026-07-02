FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY build/libs/*.jar /app/app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
