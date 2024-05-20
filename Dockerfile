# FROM maven:3.8.4-openjdk-17-slim as build
 
 
# ENV LANG en_US.UTF-8
# Update and install necessary packages
 
 
# RUN apt-get update -qq && apt-get upgrade -qq -y && \
#     apt-get install -qq -y git curl openjdk-17-jdk && apt-get install -qq -y maven && apt-get install -y tar gzip make
 
# ARG KEYCLOAK_DIST=https://github.com/Deshcloud2020/identity_keycloak.git
 
 
 
# EXPOSE 9000
# ADD ./ /tmp/identity_keycloak/
 
#RUN chmod -R 777 /temp/identity_keycloak docker build -t custom_keycloak .
# Set the working directory to the cloned repository
# WORKDIR /tmp/identity_keycloak
# RUN mvn clean package -DskipTests
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
 

 
 



FROM amd64/debian:bookworm-slim

# Update and upgrade packages
RUN apt-get update -qq
RUN apt-get upgrade -qq -y

# Install OpenJDK 17
RUN apt-get install -qq -y openjdk-17-jdk

# Set JAVA_HOME and add it to PATH
RUN JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java)))) \
&& echo "export JAVA_HOME=$JAVA_HOME" >> /etc/bash.bashrc \
&& echo "export PATH=$PATH:$JAVA_HOME/bin" >> /etc/bash.bashrc

ENV LANG en_US.UTF-8
# Prepare the directory and Hello.java
RUN mkdir -p /temp \
&& chmod 777 --preserve-root /temp \
&& echo 'public class HelloWorld { public static void main(String[] args) { System.out.println("Hello World from Java!"); } }' > /temp/Hello.java

# Check Java version and run Hello.java to verify setup
RUN java -version \
&& java /temp/Hello.java

# Install Git and clone repository
RUN apt-get install -y git \
&& cd /temp \
&& git clone https://github.com/Deshcloud2020/identity_keycloak.git

# Navigate to the project directory and build the project
WORKDIR /temp/identity_keycloak
RUN ./mvnw clean package -DskipTests

# Define any necessary runtime command
CMD ["java", "-jar", "quarkus/server/target/lib/quarkus-run.jar","start-dev","--http-port=8050"]
# Expose any necessary ports
EXPOSE 8050