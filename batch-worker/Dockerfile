# Build Stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy the parent pom and all module poms to cache dependencies
COPY pom.xml .
COPY common/pom.xml common/
COPY api-service/pom.xml api-service/
COPY batch-worker/pom.xml batch-worker/

# Build only the common module first
COPY common/src common/src
RUN mvn install -pl common -am -DskipTests

# Build the Batch worker
COPY batch-worker/src batch-worker/src
RUN mvn package -pl batch-worker -am -DskipTests

# Run Stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/batch-worker/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]