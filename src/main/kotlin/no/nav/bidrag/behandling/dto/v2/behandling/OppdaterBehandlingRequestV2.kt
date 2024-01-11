package no.nav.bidrag.behandling.dto.v2.behandling

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterBoforholdRequest
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterNotat
import no.nav.bidrag.behandling.dto.v1.behandling.OppdaterVirkningstidspunkt
<<<<<<< HEAD
import no.nav.bidrag.behandling.dto.v1.inntekt.InntektDto
=======
import no.nav.bidrag.behandling.dto.v2.inntekt.InntektDtoV2
>>>>>>> main

data class OppdaterBehandlingRequestV2(
    val grunnlagspakkeId: Long? = null,
    val vedtaksid: Long? = null,
    val virkningstidspunkt: OppdaterVirkningstidspunkt? = null,
    val boforhold: OppdaterBoforholdRequest? = null,
    val inntekter: OppdatereInntekterRequestV2? = null,
)

@Schema(
    description = """
For `inntekter`,
* Hvis feltet er null eller ikke satt vil det ikke bli gjort noe endringer. 
* Hvis feltet er tom liste vil alt bli slettet
* Innholdet i listen vil erstatte alt som er lagret. Det er derfor ikke mulig å endre på deler av informasjon i listene.
""",
)
data class OppdatereInntekterRequestV2(
<<<<<<< HEAD
    val inntekter: Set<InntektDto>? = null,
=======
    val inntekter: Set<InntektDtoV2>? = null,
>>>>>>> main
    val notat: OppdaterNotat? = null,
)
