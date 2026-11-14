FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/rule-management-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=60", "-jar", "/app/app.jar"]
