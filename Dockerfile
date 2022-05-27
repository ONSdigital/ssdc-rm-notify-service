FROM eclipse-temurin:17-jdk-alpine

ARG JAR_FILE=ssdc-rm-notify-service*.jar
CMD ["/opt/java/openjdk/bin/java", "-jar","/opt/ssdc-rm-notify-service.jar"]
COPY healthcheck.sh /opt/healthcheck.sh
RUN addgroup --gid 1000 notifyservice && \
    adduser --system --uid 1000 notifyservice notifyservice
USER notifyservice


COPY target/$JAR_FILE /opt/ssdc-rm-notify-service.jar
