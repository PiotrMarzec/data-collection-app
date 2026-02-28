## Stage 1: Build Angular frontend
FROM node:22-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

## Stage 2: Build Java backend
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
# Replace legacy static HTML with Angular build output
RUN rm -f src/main/resources/META-INF/resources/index.html \
          src/main/resources/META-INF/resources/admin.html
COPY --from=frontend-build /app/frontend/dist/frontend/browser/ ./src/main/resources/META-INF/resources/
RUN mvn package -DskipTests -B

## Stage 3: Run
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

ENV JAVA_OPTS="-Djava.util.logging.manager=org.jboss.logmanager.LogManager"

COPY --from=build /app/target/quarkus-app/lib/ ./lib/
COPY --from=build /app/target/quarkus-app/*.jar ./
COPY --from=build /app/target/quarkus-app/app/ ./app/
COPY --from=build /app/target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 9666

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
