package no.nav.bidrag.behandling.controller

import StubUtils
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.bidrag.behandling.service.CommonTestRunner
import no.nav.bidrag.behandling.utils.TestdataManager
import no.nav.bidrag.commons.web.test.HttpHeaderTestRestTemplate
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort

abstract class KontrollerTestRunner : CommonTestRunner() {
    @LocalServerPort
    private val port = 0

    @Autowired
    lateinit var httpHeaderTestRestTemplate: HttpHeaderTestRestTemplate

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
