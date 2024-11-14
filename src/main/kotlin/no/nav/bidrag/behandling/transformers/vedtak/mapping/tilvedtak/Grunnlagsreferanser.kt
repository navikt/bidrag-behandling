package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.felles.toCompactString

fun Barnetilsyn.tilGrunnlagsreferanseBarnetilsyn(gjelderBarnReferanse: Grunnlagsreferanse) =
    "${Grunnlagstype.BARNETILSYN_MED_STØNAD_PERIODE}_${gjelderBarnReferanse}_${fom.toCompactString()}${tom?.let {
        "_${it.toCompactString()}"
    } ?: ""}"

fun Tilleggsstønad.tilGrunnlagsreferanseTilleggsstønad(gjelderBarnReferanse: Grunnlagsreferanse) =
    "${Grunnlagstype.TILLEGGSSTØNAD_PERIODE}_${gjelderBarnReferanse}_" +
        "_${fom.toCompactString()}${tom?.let { "_${it.toCompactString()}" } ?: ""}"

fun FaktiskTilsynsutgift.tilGrunnlagsreferanseFaktiskTilsynsutgift(gjelderBarnReferanse: Grunnlagsreferanse) =
    "${Grunnlagstype.FAKTISK_UTGIFT_PERIODE}_${gjelderBarnReferanse}_" +
        "_${fom.toCompactString()}${tom?.let { "_${it.toCompactString()}" } ?: ""}"
