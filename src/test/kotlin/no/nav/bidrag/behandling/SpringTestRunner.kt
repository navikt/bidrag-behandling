package no.nav.bidrag.behandling

import StubUtils
import com.github.tomakehurst.wiremock.WireMockServer
import io.getunleash.FakeUnleash
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [BidragBehandlingLocal::class])
@SpringBootTest(classes = [BidragBehandlingLocal::class, StubUtils::class, TestRestTemplateConfiguration::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@ActiveProfiles("test")
@EnableMockOAuth2Server
class SpringTestRunner {
    @LocalServerPort
    protected var port: Int = 0

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    lateinit var stubUtils: StubUtils

    @Autowired
    lateinit var httpHeaderTestRestTemplate: TestRestTemplate

    @Autowired
    lateinit var unleash: FakeUnleash

    @AfterEach
    fun reset() {
        resetWiremockServers()
    }

    private fun resetWiremockServers() {
        applicationContext
            .getBeansOfType(WireMockServer::class.java)
            .values
            .forEach {
                it.resetAll()
                it
            }
    }

    protected fun getPort(): String = port.toString()
}
