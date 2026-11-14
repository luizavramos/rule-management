# Imagem de execução do rule-management. G1 explícito (em container pequeno a JVM escolheria o Serial GC). O jar é construído fora:
#   ./mvnw -DskipTests package
#   docker build -t motor-fraude/rule-management .  (o docker-compose do repositório motor-fraude usa esta pasta como contexto)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/rule-management-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=60", "-jar", "/app/app.jar"]
