FROM openjdk:17-slim
CMD ["/usr/local/openjdk-17/bin/java", "-jar", "/opt/ssdc-rm-notify-service.jar"]

RUN groupadd --gid 999 notifyservice && \
    useradd --create-home --system --uid 999 --gid notifyservice notifyservice

RUN apt-get update && \
apt-get -yq install curl && \
apt-get -yq clean && \
rm -rf /var/lib/apt/lists/*

USER notifyservice

ARG JAR_FILE=ssdc-rm-notify-service*.jar
COPY target/$JAR_FILE /opt/ssdc-rm-notify-service.jar
