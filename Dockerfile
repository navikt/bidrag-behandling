FROM ghcr.io/navikt/baseimages/temurin:21
LABEL maintainer="Team Bidrag" \
      email="bidrag@nav.no"

COPY ./target/bidrag-behandling-*.jar app.jar
EXPOSE 8080
