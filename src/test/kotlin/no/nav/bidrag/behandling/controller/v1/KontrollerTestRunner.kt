package no.nav.bidrag.behandling.controller.v1

import StubUtils
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.bidrag.behandling.service.CommonTestRunner
import no.nav.bidrag.behandling.utils.TestdataManager
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container

abstract class KontrollerTestRunner : CommonTestRunner() {
    companion object {
        @Container
        protected val postgreSqlDb =
            PostgreSQLContainer("postgres:15.4").apply {
                withDatabaseName("bidrag-behandling")
                withUsername("cloudsqliamuser")
                withPassword("admin")
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
    lateinit var testdataManager: TestdataManager

    val stubUtils: StubUtils = StubUtils()

    protected fun rootUri(): String {
        return "http://localhost:$port/api/v1"
    }

    @BeforeEach
    fun initMocks() {
        WireMock.resetAllRequests()
        stubUtils.stubHentSaksbehandler()
        stubUtils.stubOpprettForsendelse()
        stubUtils.stubSlettForsendelse()
        stubUtils.stubHentForsendelserForSak()
        stubUtils.stubTilgangskontrollTema()
        stubUtils.stubHentePersoninfo(personident = "12345")
        stubUtils.stubBeregneForskudd()
    }
}
