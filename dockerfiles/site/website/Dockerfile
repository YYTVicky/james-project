# Build James
#
# VERSION	1.0

FROM adoptopenjdk:11-jdk-hotspot

# Install git
RUN apt-get update
RUN apt-get install -y git wget unzip

# Install Maven
WORKDIR /root
RUN wget https://archive.apache.org/dist/maven/maven-3/3.6.2/binaries/apache-maven-3.6.2-bin.tar.gz
RUN tar -xvf apache-maven-3.6.2-bin.tar.gz
RUN ln -s /root/apache-maven-3.6.2/bin/mvn /usr/bin/mvn

# Copy the script
COPY compile.sh /root/compile.sh

# Define the entrypoint
WORKDIR /james-project
ENTRYPOINT ["/root/compile.sh"]
