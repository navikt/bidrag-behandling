package no.nav.bidrag.behandling.config

import io.getunleash.DefaultUnleash
import io.getunleash.UnleashContext
import io.getunleash.UnleashContextProvider
import io.getunleash.util.UnleashConfig
import io.micrometer.core.aop.TimedAspect
import io.micrometer.core.instrument.MeterRegistry
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import no.nav.bidrag.beregn.barnebidrag.BeregnBarnebidragApi
import no.nav.bidrag.beregn.barnebidrag.service.AldersjusteringOrchestrator
import no.nav.bidrag.beregn.barnebidrag.service.BidragsberegningOrkestrator
import no.nav.bidrag.commons.service.forsendelse.EnableForsendelseService
import no.nav.bidrag.commons.util.EnableSjekkForNyIdent
import no.nav.bidrag.commons.web.CorrelationIdFilter
import no.nav.bidrag.commons.web.DefaultCorsFilter
import no.nav.bidrag.commons.web.MdcConstants.MDC_ENHET
import no.nav.bidrag.commons.web.UserMdcFilter
import no.nav.bidrag.inntekt.InntektApi
import no.nav.security.token.support.client.spring.oauth2.EnableOAuth2Client
import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.context.annotation.Scope
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import javax.sql.DataSource

@EnableAspectJAutoProxy
@OpenAPIDefinition(
    info = Info(title = "bidrag-behandling", version = "v1"),
    security = [SecurityRequirement(name = "bearer-key")],
)
@SecurityScheme(
    bearerFormat = "JWT",
    name = "bearer-key",
    scheme = "bearer",
    type = SecuritySchemeType.HTTP,
)
@Configuration
@EnableJwtTokenValidation
@EnableOAuth2Client(cacheEnabled = true)
@Import(
    CorrelationIdFilter::class,
    DefaultCorsFilter::class,
    UserMdcFilter::class,
    InntektApi::class,
    BeregnBarnebidragApi::class,
    AldersjusteringOrchestrator::class,
    BidragsberegningOrkestrator::class,
)
@EnableSjekkForNyIdent
@EnableScheduling
@EnableForsendelseService
@EnableSchedulerLock(defaultLockAtMostFor = "30m")
class DefaultConfiguration {
    @Bean
    fun lockProvider(dataSource: DataSource): LockProvider =
        JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration
                .builder()
                .withJdbcTemplate(JdbcTemplate(dataSource))
                .usingDbTime()
                .build(),
        )

    @Bean
    fun unleashConfig(
        @Value("\${NAIS_APP_NAME}") appName: String,
        @Value("\${UNLEASH_INSTANCE_ID}") instanceId: String,
        @Value("\${UNLEASH_SERVER_API_URL}") apiUrl: String,
        @Value("\${UNLEASH_SERVER_API_TOKEN}") apiToken: String,
        @Value("\${UNLEASH_SERVER_API_ENV}") environment: String,
        @Value("\${UNLEASH_FETCH_SYNC:true}") fetchInSync: Boolean,
    ) = UnleashConfig
        .builder()
        .appName(appName)
        .unleashAPI("$apiUrl/api/")
        .instanceId(instanceId)
        .environment(environment)
        .synchronousFetchOnInitialisation(true)
        .apiKey(apiToken)
        .unleashContextProvider(DefaultUnleashContextProvider())
        .build()

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
    @Profile("nais")
    fun unleashInstance(unleashConfig: UnleashConfig) = DefaultUnleash(unleashConfig)

    @Bean
    fun timedAspect(registry: MeterRegistry): TimedAspect = TimedAspect(registry)
}

class DefaultUnleashContextProvider : UnleashContextProvider {
    override fun getContext(): UnleashContext {
        val userId = MDC.get("user")
        return UnleashContext
            .builder()
            .userId(userId)
            .addProperty("enhet", MDC.get(MDC_ENHET))
            .appName(MDC.get("applicationKey"))
            .build()
    }
}
