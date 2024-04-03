package no.nav.bidrag.behandling

import com.nimbusds.jose.JOSEObjectType
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import no.nav.bidrag.behandling.utils.testdata.SAKSBEHANDLER_IDENT
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter

@Configuration
@Profile("test")
class TestRestTemplateConfiguration {
    @Autowired
    private lateinit var mockOAuth2Server: MockOAuth2Server

    @Value("\${AZURE_APP_CLIENT_ID}")
    private lateinit var clientId: String

    @Bean
    @Primary
    fun unleash(): Unleash = FakeUnleash()

    @Bean
    fun httpHeaderTestRestTemplate(): TestRestTemplate {
        return TestRestTemplate(
            RestTemplateBuilder()
                .additionalMessageConverters(MappingJackson2HttpMessageConverter())
                .additionalInterceptors({ request, body, execution ->
                    request.headers.add(HttpHeaders.AUTHORIZATION, generateBearerToken())
                    execution.execute(request, body)
                }),
        )
    }

    protected fun generateBearerToken(): String {
        val iss = mockOAuth2Server.issuerUrl("aad")
        val newIssuer = iss.newBuilder().host("localhost").build()
        val token =
            mockOAuth2Server.issueToken(
                "aad",
                "aud-localhost",
                DefaultOAuth2TokenCallback(
                    issuerId = "aad",
                    subject = SAKSBEHANDLER_IDENT,
                    typeHeader = JOSEObjectType.JWT.type,
                    audience = listOf("aud-localhost"),
                    claims = mapOf("iss" to newIssuer.toString()),
                    3600,
                ),
            )
        return "Bearer " + token.serialize()
    }
}
