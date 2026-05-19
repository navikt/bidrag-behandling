package no.nav.bidrag.behandling.service

import com.github.tomakehurst.wiremock.WireMockServer
import no.nav.bidrag.behandling.BehandlingAppTest
import no.nav.bidrag.commons.service.AppContext
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.wiremock.spring.ConfigureWireMock
import org.wiremock.spring.EnableWireMock

@ActiveProfiles("test")
@SpringBootTest(
    classes = [BehandlingAppTest::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@EnableWireMock(ConfigureWireMock(port = 0))
@EnableMockOAuth2Server
@Import(AppContext::class)
abstract class CommonTestRunner {
    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @AfterEach
    fun reset() {
        resetWiremockServers()
    }

    private fun resetWiremockServers() {
        applicationContext
            .getBeansOfType(WireMockServer::class.java)
            .values
            .forEach(WireMockServer::resetAll)
    }
}
