# Build Stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy the parent pom and all module poms to cache dependencies
COPY pom.xml .
COPY common/pom.xml common/
COPY api-service/pom.xml api-service/
COPY batch-worker/pom.xml batch-worker/

# Build only the common module first to ensure it is in the local repo
COPY common/src common/src
RUN mvn install -pl common -am -DskipTests

# Build the API service
COPY api-service/src api-service/src
RUN mvn package -pl api-service -am -DskipTests

# Run Stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/api-service/target/*.jar app.jar
USER 1000:1000
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]