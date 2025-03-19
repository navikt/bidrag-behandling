FROM gcr.io/distroless/java21
LABEL maintainer="Team Bidrag" \
      email="bidrag@nav.no"

COPY --from=busybox /bin/sh /bin/sh
COPY --from=busybox /bin/printenv /bin/printenv

COPY --from=ubuntu:22.04 /usr/lib/locale/locale-archive /usr/lib/locale/locale-archive
COPY --from=ubuntu:22.04 /usr/share/i18n/locales/nb_NO /usr/share/i18n/locales/nb_NO

WORKDIR /app

COPY ./target/app.jar app.jar

EXPOSE 8080
ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"
ENV SPRING_PROFILES_ACTIVE=nais
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"

CMD ["app.jar"]