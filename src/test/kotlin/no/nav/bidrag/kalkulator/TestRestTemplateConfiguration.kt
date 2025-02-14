/*
package no.nav.bidrag.kalkulator

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.common.FileSource
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.http.ResponseDefinition
import com.nimbusds.jose.JOSEObjectType
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.kalkulator.utils.testdata.SAKSBEHANDLER_IDENT
import no.nav.bidrag.kalkulator.utils.testdata.testdataBM
import no.nav.bidrag.kalkulator.utils.testdata.testdataBP
import no.nav.bidrag.kalkulator.utils.testdata.testdataBarn1
import no.nav.bidrag.kalkulator.utils.testdata.testdataBarn2
import no.nav.bidrag.kalkulator.utils.testdata.testdataHusstandsmedlem1
import no.nav.bidrag.transport.felles.commonObjectmapper
import no.nav.bidrag.transport.person.PersonDto
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
import org.springframework.stereotype.Component
import java.time.LocalDate

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
    fun httpHeaderTestRestTemplate(): TestRestTemplate =
        TestRestTemplate(
            RestTemplateBuilder()
                .additionalMessageConverters(MappingJackson2HttpMessageConverter())
                .additionalInterceptors({ request, body, execution ->
                    request.headers.add(HttpHeaders.AUTHORIZATION, generateBearerToken())
                    execution.execute(request, body)
                }),
        )

    @Bean
    fun httpHeaderTestRestTemplateNoJackson(): TestRestTemplate =
        TestRestTemplate(
            RestTemplateBuilder()
                .additionalInterceptors({ request, body, execution ->
                    request.headers.add(HttpHeaders.AUTHORIZATION, generateBearerToken())
                    execution.execute(request, body)
                }),
        )

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

@Component
class ExampleTransformer : ResponseDefinitionTransformer() {
    override fun getName() = "example"

    override fun applyGlobally(): Boolean = false

    override fun transform(
        request: Request?,
        responseDefinition: ResponseDefinition?,
        files: FileSource?,
        parameters: Parameters?,
    ): ResponseDefinition {
        val personDto = commonObjectmapper.readValue<PersonDto>(request!!.bodyAsString)
        val personId = personDto.ident.verdi
        val personer =
            listOf(testdataBM, testdataBarn1, testdataBarn2, testdataBP, testdataHusstandsmedlem1)

        return ResponseDefinitionBuilder()
            .withStatus(200)
            .withBody(
                commonObjectmapper.writeValueAsString(
                    personer.find { it.ident == personId }?.tilPersonDto() ?: PersonDto(
                        Personident(personId),
                        fødselsdato = LocalDate.parse("2015-05-01"),
                    ),
                ),
            ).build()
    }
}
*/
