FROM maven:3.8.8-eclipse-temurin-8 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:8-jre
WORKDIR /app
RUN mkdir -p /data/uploads && chown -R 10001:10001 /data
COPY --from=build /workspace/target/hm-dianping-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
