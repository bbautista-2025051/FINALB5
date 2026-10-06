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
RUN mvn -B -q -pl api-gateway -am dependency:go-offline -DskipTests

COPY api-gateway/src api-gateway/src
RUN mvn -B -q -pl api-gateway -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=build /build/api-gateway/target/api-gateway-*.jar app.jar
RUN chown -R app:app /app
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]