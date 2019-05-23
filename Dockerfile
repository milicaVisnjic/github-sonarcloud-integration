# Start with standard Maven docker image
FROM maven:3.5.2-jdk-8-alpine AS MAVEN_TOOL_CHAIN

# Copy just the pom.xml file to the container
COPY pom.xml /tmp/

# Runs a mvn command to download all dependencies found in the pom.xml
RUN mvn -B dependency:go-offline -f /tmp/pom.xml -s /usr/share/maven/ref/settings-docker.xml

# Copy the rest of the source code in the container
COPY src /tmp/src/

# Compile the code, run unit tests and then integration tests
WORKDIR /tmp/
RUN mvn -B -s /usr/share/maven/ref/settings-docker.xml verify

# Discard the Maven image with all the compiled classes/unit test results etc.
FROM java:8-jre-alpine

RUN mkdir /app
COPY --from=MAVEN_TOOL_CHAIN /tmp/target/sonarcloud-github-integration-1.0.0-jar-with-dependencies.jar /app/sonarcloud-github-integration.jar
