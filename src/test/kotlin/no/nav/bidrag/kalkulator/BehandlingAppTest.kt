/*
package no.nav.bidrag.kalkulator

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.SpringApplication
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.context.annotation.EnableAspectJAutoProxy

@SpringBootApplication(exclude = [SecurityAutoConfiguration::class, ManagementWebSecurityAutoConfiguration::class])
@EnableAspectJAutoProxy
@EnableJwtTokenValidation(ignore = ["org.springframework", "org.springdoc"])
class BehandlingAppTest

fun main(args: Array<String>) {
    val wireMockServer =
        WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort().dynamicHttpsPort())
    wireMockServer.start()
    val app = SpringApplication(BehandlingAppTest::class.java)
    app.setAdditionalProfiles("test", "database-legacy")
    app.run(*args)

    wireMockServer.resetAll()
    wireMockServer.stop()
}
*/
