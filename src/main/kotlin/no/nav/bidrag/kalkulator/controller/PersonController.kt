package no.nav.bidrag.kalkulator.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import no.nav.bidrag.kalkulator.config.Tokeninformation
import no.nav.bidrag.kalkulator.service.PersonService
import no.nav.bidrag.transport.person.MotpartBarnRelasjonDto
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.GetMapping

@ProtectedWithClaims(issuer = "tokenx")
@no.nav.bidrag.kalkulator.controller.PersonRestController
class PersonController(
    private val personService: PersonService,
) {
    @GetMapping("/familierelasjoner")
    @Operation(
        description = "Hente familierelasjoner for pålogget person samt evnt aktive fordelingsforespørsler",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Ingen feil ved henting av brukerinformasjon",
            ), ApiResponse(responseCode = "400", description = "Ugyldig fødselsnummer"), ApiResponse(
                responseCode = "401",
                description = "Sikkerhetstoken mangler, er utløpt, eller av andre årsaker ugyldig",
            ), ApiResponse(responseCode = "404", description = "Fant ikke fødselsnummer"), ApiResponse(
                responseCode = "500",
                description = "Serverfeil",
            ), ApiResponse(responseCode = "503", description = "Tjeneste utilgjengelig"),
        ],
    )
    fun henteFamilierelasjoner(): MotpartBarnRelasjonDto? {
        val personident = Tokeninformation.hentPaaloggetPerson()

        return personService.hentFamilie(personident)
    }
}
