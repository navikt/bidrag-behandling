package no.nav.bidrag.behandling.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.util.StdDateFormat
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.commons.security.api.EnableSecurityConfiguration
import no.nav.bidrag.commons.service.AppContext
import no.nav.bidrag.commons.service.organisasjon.EnableSaksbehandlernavnProvider
import no.nav.bidrag.commons.web.config.RestOperationsAzure
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention
import org.springframework.retry.annotation.EnableRetry
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.time.Duration
import java.time.temporal.ChronoUnit

@Configuration
@EnableSecurityConfiguration
@EnableSaksbehandlernavnProvider
@EnableRetry
@Import(RestOperationsAzure::class, AppContext::class, BeregnForskuddApi::class)
class RestConfig(
    private val behandlingIdMdcInterceptor: BehandlingIdMdcInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry
            .addInterceptor(behandlingIdMdcInterceptor)
            .addPathPatterns(API_PATH_PATTERN)
    }

    companion object {
        private const val API_PATH_PATTERN = "/api/**"
    }

    @Bean
    fun customOpenAPI(): OpenAPI =
        OpenAPI()
            .components(Components())
            .info(
                io.swagger.v3.oas.models.info
                    .Info()
                    .title("Bidrag Behandling API")
                    .version("1.0")
                    .description("API documentation for bidrag-behandling"),
            )

    @Bean
    fun openApiCustomizer(): OpenApiCustomizer =
        OpenApiCustomizer { openApi: OpenAPI ->
            // Remove KotlinDeprecatedPropertyCustomizer if present
            openApi.components?.schemas?.values?.forEach { schema ->
                // Additional schema adjustments if needed
            }
        }

    @Bean
    fun clientRequestObservationConvention() = DefaultClientRequestObservationConvention()

    @Bean
    @Primary
    fun restTemplateBuilder(restTemplate: RestTemplateBuilder): RestTemplateBuilder =
        restTemplate
            .connectTimeout(Duration.of(30, ChronoUnit.SECONDS))
            .readTimeout(Duration.of(30, ChronoUnit.SECONDS))

    @Bean
    fun objectMapper(): ObjectMapper =
        ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
            .setDateFormat(StdDateFormat())
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}
