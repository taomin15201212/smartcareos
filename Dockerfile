FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system smartcareos && useradd --system --gid smartcareos smartcareos
COPY target/smartcareos-0.1.0-SNAPSHOT.jar /app/smartcareos.jar
USER smartcareos
EXPOSE 8080 9090
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/smartcareos.jar"]
