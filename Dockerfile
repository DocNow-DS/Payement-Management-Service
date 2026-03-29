FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY target/payment-management-service-*.jar /app/app.jar

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
