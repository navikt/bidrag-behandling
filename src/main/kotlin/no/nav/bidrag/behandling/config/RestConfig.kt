package no.nav.bidrag.behandling.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.util.StdDateFormat
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.deser.YearMonthDeserializer
import com.fasterxml.jackson.module.kotlin.KotlinModule
import no.nav.bidrag.commons.security.api.EnableSecurityConfiguration
import no.nav.bidrag.commons.web.config.RestOperationsAzure
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Configuration
@EnableSecurityConfiguration
@Import(RestOperationsAzure::class)
class RestConfig {

    @Bean
    fun jackson2ObjectMapperBuilder(): Jackson2ObjectMapperBuilder {
        return Jackson2ObjectMapperBuilder()
            .modules(
                KotlinModule.Builder().build(),
                JavaTimeModule()
                    .addDeserializer(
                        YearMonth::class.java,
                        // Denne trengs for å parse år over 9999 riktig.
                        YearMonthDeserializer(DateTimeFormatter.ofPattern("u-MM")),
                    ),
            )
            .dateFormat(StdDateFormat())
            .failOnUnknownProperties(false)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
    }
}
