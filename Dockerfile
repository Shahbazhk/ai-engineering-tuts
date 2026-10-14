# Course web application image. Build context = repository root (the app reads content/ and lessons/ at runtime).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY app app
COPY lessons lessons
# The reactor needs every module's pom; only the app is built.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q -pl app package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 course
WORKDIR /course
COPY content content
COPY lessons lessons
COPY app/src/main/java app/src/main/java
COPY --from=build /src/app/target/course-app-1.0.0.jar /app/course-app.jar
RUN mkdir -p /course/data && chown -R course /course
USER course
ENV COURSE_ROOT=/course \
    COURSE_DB_URL=jdbc:h2:file:/course/data/course;AUTO_SERVER=TRUE
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/course-app.jar"]
