FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY mascota-service/pom.xml mascota-service/pom.xml
COPY cita-service/pom.xml cita-service/pom.xml
COPY expediente-service/pom.xml expediente-service/pom.xml

RUN mvn -B -q -N install
RUN mvn -B -q -pl auth-service -am dependency:go-offline -DskipTests

COPY auth-service/src auth-service/src
RUN mvn -B -q -pl auth-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=build /build/auth-service/target/auth-service-*.jar app.jar
RUN chown -R app:app /app
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]