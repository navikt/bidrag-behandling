package no.nav.bidrag.behandling.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.util.StdDateFormat
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.deser.YearMonthDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import com.fasterxml.jackson.module.kotlin.KotlinModule
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.commons.security.api.EnableSecurityConfiguration
import no.nav.bidrag.commons.service.AppContext
import no.nav.bidrag.commons.service.organisasjon.EnableSaksbehandlernavnProvider
import no.nav.bidrag.commons.web.config.RestOperationsAzure
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.retry.annotation.EnableRetry
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Configuration
@EnableSecurityConfiguration
@EnableSaksbehandlernavnProvider
@EnableRetry
@Import(RestOperationsAzure::class, AppContext::class, BeregnForskuddApi::class)
class RestConfig {
    @Bean
    fun clientRequestObservationConvention() = DefaultClientRequestObservationConvention()

    @Bean
    @Primary
    fun restTemplateBuilder(restTemplate: RestTemplateBuilder): RestTemplateBuilder =
        restTemplate
            .setConnectTimeout(Duration.of(30, ChronoUnit.SECONDS))
            .setReadTimeout(Duration.of(30, ChronoUnit.SECONDS))

    @Bean
    fun jackson2ObjectMapperBuilder(): Jackson2ObjectMapperBuilder =
        Jackson2ObjectMapperBuilder()
            .modules(
                KotlinModule.Builder().build(),
                JavaTimeModule()
                    .addDeserializer(
                        YearMonth::class.java,
                        // Denne trengs for å parse år over 9999 riktig.
                        YearMonthDeserializer(DateTimeFormatter.ofPattern("u-MM")),
                    ).addSerializer(
                        LocalDate::class.java,
                        // Denne trengs for å skrive ut år over 9999 riktig.
                        LocalDateSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    ),
            ).dateFormat(StdDateFormat())
            .failOnUnknownProperties(false)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
}
