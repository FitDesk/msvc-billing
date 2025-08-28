FROM openjdk:21-jdk-slim AS build

WORKDIR /app
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
COPY src ./src

RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

FROM openjdk:21-jre-slim

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 9090

CMD ["java", "-jar", "app.jar"]