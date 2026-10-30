FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY .git ./.git
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:21.0.11_10-jre-alpine AS truststore
WORKDIR /truststore
ADD https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem global-bundle.pem
RUN cp -L "$JAVA_HOME/lib/security/cacerts" truststore.jks \
    && awk '/-----BEGIN CERTIFICATE-----/ { if (arquivo) close(arquivo); n++; arquivo = sprintf("rds-ca-%03d.pem", n) } arquivo { print > arquivo }' global-bundle.pem \
    && for certificado in rds-ca-*.pem; do \
         keytool -importcert -noprompt -keystore truststore.jks -storepass changeit \
           -alias "${certificado%.pem}" -file "$certificado" > /dev/null 2>&1; \
       done \
    && test "$(keytool -list -keystore truststore.jks -storepass changeit | grep -c '^rds-ca-')" -eq "$(ls rds-ca-*.pem | wc -l)"

FROM newrelic/newrelic-java-init:9.4.0 AS newrelic

FROM eclipse-temurin:21.0.11_10-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar
COPY --from=newrelic --chown=spring:spring /newrelic-agent.jar /newrelic/newrelic.jar
COPY --chown=spring:spring newrelic.yml /newrelic/newrelic.yml
COPY --from=truststore --chown=spring:spring /truststore/truststore.jks /opt/docdb/truststore.jks
USER spring
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS=""
ENV NEW_RELIC_APPLICATION_LOGGING_FORWARDING_ENABLED=false

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "\
if [ -n \"$NEW_RELIC_LICENSE_KEY\" ]; then \
  exec java -javaagent:/newrelic/newrelic.jar -jar app.jar; \
else \
  exec java -jar app.jar; \
fi"]
