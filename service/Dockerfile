FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY db ./db
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar /app/paymentapp.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/paymentapp.jar"]
