package no.nav.bidrag.kalkulator

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.SpringApplication
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration

const val PROFILE_NAIS = "nais"

@SpringBootApplication(exclude = [SecurityAutoConfiguration::class, ManagementWebSecurityAutoConfiguration::class])
@EnableJwtTokenValidation(ignore = ["org.springframework", "org.springdoc"])
class BidragKalkulator

fun main(args: Array<String>) {
    SpringApplication(BidragKalkulator::class.java)
        .apply {
            setAdditionalProfiles(if (args.isEmpty()) PROFILE_NAIS else args[0])
        }.run(*args)
}
