package no.nav.bidrag.behandling.controller

import StubUtils
import com.ninjasquad.springmockk.MockkBean
import io.getunleash.Unleash
import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import no.nav.bidrag.behandling.service.CommonTestRunner
import no.nav.bidrag.behandling.utils.testdata.TestdataManager
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.commons.web.mock.stubKodeverkProvider
import no.nav.bidrag.commons.web.mock.stubSjablonProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import stubPersonConsumer

abstract class KontrollerTestRunner : CommonTestRunner() {
    companion object {
        @Container
        protected val postgreSqlDb =
            PostgreSQLContainer("postgres:15.4").apply {
                withDatabaseName("bidrag-behandling")
                withUsername("cloudsqliamuser")
                withPassword("admin")
                withInitScript("db/init.sql")
                portBindings = listOf("7778:5432")
                start()
            }

        @Suppress("unused")
        @JvmStatic
        @DynamicPropertySource
        fun postgresqlProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.jpa.database") { "POSTGRESQL" }
            registry.add("spring.datasource.type") { "com.zaxxer.hikari.HikariDataSource" }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.locations") { "classpath:/db/migration" }
            registry.add("spring.datasource.url", postgreSqlDb::getJdbcUrl)
            registry.add("spring.datasource.password", postgreSqlDb::getPassword)
            registry.add("spring.datasource.username", postgreSqlDb::getUsername)
            registry.add("spring.datasource.hikari.connection-timeout") { 250 }
        }
    }

    @LocalServerPort
    private val port = 0

    @Autowired
    lateinit var httpHeaderTestRestTemplate: TestRestTemplate

    @Autowired
    lateinit var httpHeaderTestRestTemplateNoJackson: TestRestTemplate

    @Autowired
    lateinit var testdataManager: TestdataManager

    @MockkBean
    lateinit var unleashInstance: Unleash

    val stubUtils: StubUtils = StubUtils()

    protected fun rootUriV1(): String = "http://localhost:$port/api/v1"

    protected fun rootUriV2(): String = "http://localhost:$port/api/v2"

    @BeforeEach
    fun initMocks() {
        clearMocks(unleashInstance)
        every { unleashInstance.isEnabled(any(), any<Boolean>()) } returns true
        mockkObject(SaksbehandlernavnProvider)
        every { SaksbehandlernavnProvider.hentSaksbehandlernavn(any()) } returns "Fornavn Etternavn"
        stubSjablonProvider()
        stubPersonConsumer()
        stubKodeverkProvider()
        stubUtils.stubUnleash()
        stubUtils.stubHentSaksbehandler()
        stubUtils.stubOpprettForsendelse()
        stubUtils.stubSlettForsendelse()
        stubUtils.stubHentForsendelserForSak()
        stubUtils.stubTilgangskontrollTema()
        stubUtils.stubHentePersoninfo(personident = "12345")
        stubUtils.stubKodeverkSkattegrunnlag()
        stubUtils.stubKodeverkLønnsbeskrivelse()
        stubUtils.stubKodeverkNaeringsinntektsbeskrivelser()
        stubUtils.stubKodeverkYtelsesbeskrivelser()
        stubUtils.stubKodeverkPensjonsbeskrivelser()
        stubUtils.stubKodeverkSpesifisertSummertSkattegrunnlag()
        stubUtils.stubTilgangskontrollSak()
        stubUtils.stubTilgangskontrollPerson()
        stubUtils.stubTilgangskontrollPersonISak()
    }
}
