FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src/ src/
RUN ./mvnw -B verify

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system smartqueue && useradd --system --gid smartqueue smartqueue
COPY --from=build --chown=smartqueue:smartqueue /build/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar
USER smartqueue
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55.0 -XX:+UseSerialGC -Xss512k"
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
