FROM maven:3.8.4-openjdk-17-slim as build
 
 
ENV LANG en_US.UTF-8
# Update and install necessary packages
 
 
# RUN apt-get update -qq && apt-get upgrade -qq -y && \
#     apt-get install -qq -y git curl openjdk-17-jdk && apt-get install -qq -y maven && apt-get install -y tar gzip make
 
ARG KEYCLOAK_DIST=https://github.com/Deshcloud2020/identity_keycloak.git
 
 
ADD $KEYCLOAK_DIST /tmp/identity_keycloak/
 
#RUN chmod -R 777 /temp/identity_keycloak docker build -t custom_keycloak .
# Set the working directory to the cloned repository
WORKDIR /tmp/identity_keycloak
RUN mvn clean package -DskipTests
#RUN chmod -R g+rwX ./
# Set JAVA_HOME and PATH environment variables
# RUN JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java)))) && \
#     echo "export JAVA_HOME=$JAVA_HOME" >> /etc/bash.bashrc && \
#     echo "export PATH=$PATH:$JAVA_HOME/bin" >> /etc/bash.bashrc
 
# Install additional project dependencies if any
# For example, if there are any specific Maven or project dependencies
#RUN ./mvnw dependency:go-offline
 
# Run the build
# RUN ./mvnw clean package -DskipTests
 
# Expose any necessary ports
 
 
# Define any necessary runtime command
 CMD ["java", "-jar", "quarkus/server/target/lib/quarkus-run.jar","start-dev","--http-port=8050"]
 
 EXPOSE 8050