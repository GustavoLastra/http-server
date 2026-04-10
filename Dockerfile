FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/httpserver-1.0-SNAPSHOT.jar httpserver.jar
RUN mkdir -p /srv/www

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "httpserver.jar", "8080", "/srv/www"]
