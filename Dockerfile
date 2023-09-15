FROM ghcr.io/navikt/baseimages/temurin:20
LABEL maintainer="Team Bidrag" \
      email="bidrag@nav.no"

COPY ./target/bidrag-behandling-*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=nais
EXPOSE 8080
