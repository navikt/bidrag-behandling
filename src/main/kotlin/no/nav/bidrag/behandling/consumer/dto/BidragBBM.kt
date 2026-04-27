package no.nav.bidrag.behandling.consumer.dto

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import no.nav.bidrag.domene.enums.behandling.SøknadsknytningStatus
import no.nav.bidrag.transport.behandling.beregning.felles.HentSøknad
import java.time.LocalDateTime

data class SammenknyttSøknaderRequest(
    val hovedsøknadsid: Long,
    val referertSøknadsid: Long,
)

data class SlettHovedsøknadRequest(
    val eksisterendeHovedsøknadsid: Long,
    val nyHovedsøknadsid: Long? = null,
)

data class SlettSammenknytningForSøknadRequest(
    val søknadsid: Long,
)

data class DeaktiverHovedsøknadRequest(
    val søknadsid: Long,
)

data class SøknadsknytningResponse(
    val id: Long? = null,
    val hovedsøknadsid: Long? = null,
    val referertSøknadsid: Long? = null,
    val status: String? = null,
    val søknadKnytningstype: String? = null,
    val opprettetTidspunkt: LocalDateTime? = null,
)

data class FinnSammenknytningerHovedsøknadResponse(
    val hovedsøknadsid: Long? = null,
    val søknader: List<HentSøknad>,
)

data class FinnSammenknytningerHovedsøknadRequest(
    val søknadsid: Long,
    val status: SøknadsknytningStatus = SøknadsknytningStatus.Aktiv,
)
