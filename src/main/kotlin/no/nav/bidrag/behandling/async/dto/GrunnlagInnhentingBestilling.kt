package no.nav.bidrag.behandling.async.dto

data class GrunnlagInnhentingBestilling(
    val behandlingId: Long,
    val waitForCommit: Boolean = true,
)
