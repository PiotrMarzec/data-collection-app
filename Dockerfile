## Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

## Stage 2: Run
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Quarkus config
ENV JAVA_OPTS="-Djava.util.logging.manager=org.jboss.logmanager.LogManager"

COPY --from=build /app/target/quarkus-app/lib/ ./lib/
COPY --from=build /app/target/quarkus-app/*.jar ./
COPY --from=build /app/target/quarkus-app/app/ ./app/
COPY --from=build /app/target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 9666

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
