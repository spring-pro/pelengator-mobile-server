FROM ubuntu:latest

ENV DEBIAN_FRONTEND=noninteractive \
    JAVA_HOME=/usr/lib/jvm/java-8-oracle \
    JRE_HOME=/usr/lib/jvm/java-8-oracle/jre

RUN VERSION=8 && UPDATE=201 && BUILD=09 && SIG=42970487e3af4f5aa5bca3f542482c60
RUN apt-get update && apt-get dist-upgrade -y && \
    apt-get install -y curl software-properties-common && \
    apt-get install apt-utils ca-certificates curl -y --no-install-recommends

COPY jdk-8u201-linux-x64.tar.gz /tmp/jdk-8u201-linux-x64.tar.gz

RUN tar -xvf /tmp/jdk-8u201-linux-x64.tar.gz -C /tmp

RUN mkdir -p /usr/lib/jvm && mv /tmp/jdk1.8.0_201 "${JAVA_HOME}" && \
    apt-get autoclean && apt-get --purge -y autoremove && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* && \
    update-alternatives --install "/usr/bin/java" "java" "${JRE_HOME}/bin/java" 1 && \
    update-alternatives --install "/usr/bin/javaws" "javaws" "${JRE_HOME}/bin/javaws" 1 && \
    update-alternatives --install "/usr/bin/javac" "javac" "${JAVA_HOME}/bin/javac" 1 && \
    update-alternatives --set java "${JRE_HOME}/bin/java" && \
    update-alternatives --set javaws "${JRE_HOME}/bin/javaws" && \
    update-alternatives --set javac "${JAVA_HOME}/bin/javac"

# Install Tomcat
COPY apache-tomcat-9.0.29.tar.gz /tmp/apache-tomcat-9.0.29.tar.gz
RUN mkdir -p /opt/tomcat
RUN tar -xvf /tmp/apache-tomcat-9.0.29.tar.gz -C /opt/tomcat --strip-components=1
RUN rm -rf /opt/tomcat/webapps/ROOT

# Set certificates for HTTPS
COPY ./tomcat_conf/server.xml /opt/tomcat/conf/server.xml
COPY ./tomcat_conf/cert.pem /opt/tomcat/conf/cert.pem
COPY ./tomcat_conf/private_key.pem /opt/tomcat/conf/private_key.pem
COPY ./tomcat_conf/chain.pem /opt/tomcat/conf/chain.pem

# copy app and conf
COPY ./app/resources /opt/tomcat/webapps/resources
COPY ./app/ROOT.war /opt/tomcat/webapps/ROOT.war
COPY ./app/conf  /opt/pelengator/mobile-server/conf

# Expose Tomcat
EXPOSE 8443

ENV JAVA_OPTS -server -XX:+DisableExplicitGC -XX:+UseConcMarkSweepGC \
   -Xms1G -Xmx4G -XX:PermSize=1G -XX:MaxPermSize=4G

WORKDIR /opt/tomcat
CMD ["bin/catalina.sh","run"]