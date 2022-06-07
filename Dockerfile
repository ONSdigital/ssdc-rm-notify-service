FROM eclipse-temurin:17-jdk-alpine

CMD ["java", "-jar","/opt/ssdc-rm-notify-service.jar"]
COPY healthcheck.sh /opt/healthcheck.sh
RUN addgroup --gid 1000 notifyservice && \
    adduser --system --uid 1000 notifyservice notifyservice
USER notifyservice

COPY target/ssdc-rm-notify-service*.jar /opt/ssdc-rm-notify-service.jar
