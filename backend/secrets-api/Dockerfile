FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml pom.xml
COPY kms-service/pom.xml kms-service/pom.xml
COPY secrets-api/pom.xml secrets-api/pom.xml

RUN mvn -pl secrets-api -am -DskipTests dependency:go-offline

COPY kms-service/src kms-service/src
COPY secrets-api/src secrets-api/src

RUN mvn -pl secrets-api -am -DskipTests package

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /workspace/secrets-api/target/secrets-api-*.jar /app/secrets-api.jar

RUN addgroup --system app && adduser --system --ingroup app app \
    && chown -R app:app /app

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/secrets-api.jar"]
