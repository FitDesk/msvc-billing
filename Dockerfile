FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp
RUN apk add --no-cache curl
COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 9093

CMD ["java", "-jar", "app.jar"]