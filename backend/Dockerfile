FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /app

COPY pom.xml ./

RUN mvn -q -DskipTests dependency:go-offline

COPY src src

RUN mvn -q -DskipTests package && cp target/*.jar app.jar

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /app/app.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
