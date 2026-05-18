package no.nav.bidrag.behandling.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import no.nav.bidrag.beregn.forskudd.BeregnForskuddApi
import no.nav.bidrag.commons.security.api.EnableSecurityConfiguration
import no.nav.bidrag.commons.service.AppContext
import no.nav.bidrag.commons.service.organisasjon.EnableSaksbehandlernavnProvider
import no.nav.bidrag.commons.util.CustomJacksonHttpMessageConverter
import no.nav.bidrag.commons.web.config.RestOperationsAzure
import no.nav.bidrag.transport.felles.commonObjectmapper
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention
import org.springframework.http.converter.HttpMessageConverters
import org.springframework.retry.annotation.EnableRetry
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.charset.Charset
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

    override fun configureMessageConverters(converters: HttpMessageConverters.ServerBuilder) {
        converters.addCustomConverter(CustomJacksonHttpMessageConverter(commonObjectmapper))
    }

    companion object {
        private const val API_PATH_PATTERN = "/api/**"
        private const val POJO_NODE_MODULE_NAME = "pojoNodeCompatibilityModule"
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
            openApi.components?.schemas?.values?.forEach { _ ->
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
}
