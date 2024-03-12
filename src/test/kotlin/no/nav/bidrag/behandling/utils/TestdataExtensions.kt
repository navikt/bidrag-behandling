package no.nav.bidrag.behandling.utils

import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.transport.behandling.beregning.felles.InntektPerBarn

fun List<InntektPerBarn>.hentInntektForBarn(barnIdent: String) = find { it.inntektGjelderBarnIdent == Personident(barnIdent) }
