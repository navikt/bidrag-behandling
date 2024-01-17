package no.nav.bidrag.behandling.controller.v2

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import no.nav.bidrag.behandling.dto.v1.behandling.BehandlingNotatDto
import no.nav.bidrag.behandling.dto.v1.behandling.BoforholdDto
import no.nav.bidrag.behandling.dto.v1.behandling.VirkningstidspunktDto
import no.nav.bidrag.behandling.dto.v2.behandling.BehandlingDtoV2
import no.nav.bidrag.behandling.dto.v2.behandling.OppdaterBehandlingRequestV2
import no.nav.bidrag.behandling.dto.v2.inntekt.InntekterDtoV2
import no.nav.bidrag.domene.enums.rolle.SøktAvType
import no.nav.bidrag.domene.enums.vedtak.Vedtakstype
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import java.time.LocalDate

@BehandlingRestControllerV2
class BehandlingControllerV2 {
    @Suppress("unused")
    @PutMapping("/behandling/{behandlingId}")
    @Operation(
        description = "Oppdatere behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    fun oppdatereBehandlingV2(
        @PathVariable behandlingId: Long,
        @Valid @RequestBody(required = true) request: OppdaterBehandlingRequestV2,
    ): BehandlingDtoV2 {
        // TODO: implementere
        return responsstubbe()
    }

    @Suppress("unused")
    @GetMapping("/behandling/{behandlingId}")
    @Operation(
        description = "Hente en behandling",
        security = [SecurityRequirement(name = "bearer-key")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Hentet behandling"),
            ApiResponse(responseCode = "404", description = "Fant ikke behandling"),
        ],
    )
    fun hentBehandlingV2(
        @PathVariable behandlingId: Long,
    ): BehandlingDtoV2 {
        // TODO: implementere
        return responsstubbe()
    }

    fun responsstubbe(): BehandlingDtoV2 {
        val nå = LocalDate.now()
        return BehandlingDtoV2(
            0L,
            vedtakstype = Vedtakstype.FASTSETTELSE,
            erVedtakFattet = false,
            søktFomDato = nå,
            mottattdato = nå,
            søktAv = SøktAvType.VERGE,
            saksnummer = "1234567",
            søknadsid = 1,
            behandlerenhet = "4806",
            roller = emptySet(),
            virkningstidspunkt = VirkningstidspunktDto(notat = BehandlingNotatDto()),
            inntekter = InntekterDtoV2(emptySet(), BehandlingNotatDto()),
            boforhold = BoforholdDto(emptySet(), sivilstand = emptySet(), notat = BehandlingNotatDto()),
            opplysninger = emptyList(),
        )
    }
}
