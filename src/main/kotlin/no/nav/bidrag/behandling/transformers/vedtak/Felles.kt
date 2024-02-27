package no.nav.bidrag.behandling.transformers.vedtak

import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.service.hentNyesteIdent
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.erPerson
import no.nav.bidrag.transport.behandling.felles.grunnlag.personIdent
import no.nav.bidrag.transport.sak.RolleDto

val skyldnerNav = Personident("NAV")

fun Collection<GrunnlagDto>.hentPersonNyesteIdent(ident: String?) =
    filter { it.erPerson() }.find { it.personIdent == hentNyesteIdent(ident)?.verdi || it.personIdent == ident }

// TODO: Reel mottaker fra bidrag-sak?
fun Set<Rolle>.reelMottakerEllerBidragsmottaker(rolle: RolleDto) =
    rolle.reellMottager?.verdi?.let { Personident(it) }
        ?: find { it.rolletype == Rolletype.BIDRAGSMOTTAKER }!!.let { hentNyesteIdent(it.ident)!! }

fun <T, R> T?.takeIfNotNullOrEmpty(block: (T) -> R): R? {
    return if (this == null || this is String && this.trim().isEmpty()) null else block(this)
}