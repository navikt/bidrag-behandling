FROM ubuntu:22.04 as locales
RUN apt-get update && apt-get install -y locales
RUN locale-gen nb_NO.UTF-8 && \
    update-locale LANG=nb_NO.UTF-8 LANGUAGE="nb_NO:nb" LC_ALL=nb_NO.UTF-8

FROM gcr.io/distroless/java21
LABEL maintainer="Team Bidrag" \
      email="bidrag@nav.no"

COPY --from=busybox /bin/sh /bin/sh
COPY --from=busybox /bin/printenv /bin/printenv

# Copy locale files from the locales stage
COPY --from=locales /usr/lib/locale/ /usr/lib/locale/

WORKDIR /app

COPY ./target/app.jar app.jar

EXPOSE 8080
ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"
ENV SPRING_PROFILES_ACTIVE=nais
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"

CMD ["app.jar"]