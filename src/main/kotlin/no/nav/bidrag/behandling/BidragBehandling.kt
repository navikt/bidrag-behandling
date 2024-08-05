package no.nav.bidrag.behandling

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration

const val PROFILE_NAIS = "nais"
val SECURE_LOGGER: Logger = LoggerFactory.getLogger("secureLogger")
val objectmapper =
    ObjectMapper()
        .findAndRegisterModules()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
        .configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, false)

@SpringBootApplication(exclude = [SecurityAutoConfiguration::class, ManagementWebSecurityAutoConfiguration::class])
@EnableJwtTokenValidation(ignore = ["org.springframework", "org.springdoc"])
class BidragBehandling

fun main(args: Array<String>) {
    SpringApplication(BidragBehandling::class.java)
        .apply {
            setAdditionalProfiles(if (args.isEmpty()) PROFILE_NAIS else args[0])
        }.run(*args)
}
