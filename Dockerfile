# Pull base image.
FROM ubuntu:14.04

MAINTAINER Omar Karim Martín Cornejo <omarkhd.mx@gmail.com>

# Update repos and install basic stuff.
RUN \
	apt-get update && \
	apt-get install -y software-properties-common

# Install Java.
RUN \
	echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
	add-apt-repository -y ppa:webupd8team/java && \
	apt-get update && \
	apt-get install -y oracle-java8-installer && \
	apt-get install -y oracle-java8-set-default && \
	rm -rf /var/lib/apt/lists/* && \
	rm -rf /var/cache/oracle-jdk8-installer

# Define commonly used JAVA_HOME variable.
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

# Install Gradle.
RUN \
	add-apt-repository -y ppa:cwchien/gradle && \
	apt-get update && \
	apt-get install -y gradle

# Install Tomcat 8.
ADD http://www.us.apache.org/dist/tomcat/tomcat-8/v8.0.28/bin/apache-tomcat-8.0.28.zip /opt/.downloads/
RUN apt-get update && apt-get install -y unzip
RUN cd /opt && unzip .downloads/apache-tomcat-8.0.28.zip && \
	mv apache-tomcat-8.0.28 tomcat8 && chmod +x tomcat8/bin/*.sh

# Copy and compile source code.
ADD build.gradle gretty.plugin settings.gradle /opt/prestify/
ADD src /opt/prestify/src/
RUN cd /opt/prestify && gradle war

# Deploy war into the tomcat7 container.
RUN rm -rf /opt/tomcat8/webapps/* && \
	unzip /opt/prestify/build/libs/net.omarkhd.prestify-0.1.0.war -d /opt/tomcat8/webapps/ROOT

# Define entrypoint.
WORKDIR /opt/tomcat8
VOLUME ["/root/reports"]
EXPOSE 8080
ENTRYPOINT ["bin/catalina.sh"]
CMD ["run"]
