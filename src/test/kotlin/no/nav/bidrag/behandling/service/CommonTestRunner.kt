package no.nav.bidrag.behandling.service

import no.nav.bidrag.behandling.BehandlingAppTest
import no.nav.bidrag.commons.service.AppContext
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest(
    classes = [BehandlingAppTest::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@AutoConfigureWireMock(port = 0)
@EnableMockOAuth2Server
@Import(AppContext::class)
abstract class CommonTestRunner
