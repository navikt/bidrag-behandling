package no.nav.bidrag.behandling.async.dto

data class SøknadSlettetBestilling(
    val søknadsid: Long,
    val behandlingsid: Long?,
)
