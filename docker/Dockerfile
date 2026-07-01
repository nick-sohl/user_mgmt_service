# Build Stage
# We first use an Image containing the JDK, because Gradle is a Java Programm and we need the javac (compiler) to compile the java code
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
# We seperate the COPY phase into two steps to make use of layer caching
# We first copy all the gradle stuff to resolve all the dependencies
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# gradlew checks our build.gradle file and searches for our dependencies and downloads them
# We don't want running background processes in our docker container -> --no-daemon
RUN ./gradlew dependencies --no-daemon

# In the next step we copy the source code of the Java app
# Now if we edit the java code, only this step gets rerun and all the expensive gradle stuff is cached and reused
COPY src src
# We build our code with bootJar -> skips running tests and directly build the fat jar for production ( test should run before all of that )
RUN ./gradlew bootJar --no-daemon

# Runtime Stage
FROM eclipse-temurin:25-jre-alpine AS final
WORKDIR /app

# Run as an unprivileged user
# -S creates a system user/group (no password, no aging), -G assigns the group
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /app/build/libs/*.jar app.jar
USER app

ENTRYPOINT ["java", "-jar", "app.jar"]
