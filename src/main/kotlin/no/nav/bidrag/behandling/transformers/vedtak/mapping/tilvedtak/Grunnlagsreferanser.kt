package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import no.nav.bidrag.behandling.database.datamodell.Barnetilsyn
import no.nav.bidrag.behandling.database.datamodell.FaktiskTilsynsutgift
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtale
import no.nav.bidrag.behandling.database.datamodell.PrivatAvtalePeriode
import no.nav.bidrag.behandling.database.datamodell.Samværsperiode
import no.nav.bidrag.behandling.database.datamodell.Tilleggsstønad
import no.nav.bidrag.behandling.transformers.grunnlag.tilGrunnlagPerson
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.Grunnlagsreferanse
import no.nav.bidrag.transport.felles.toCompactString

fun Samværsperiode.tilGrunnlagsreferanseSamværsperiode() =
    "samvær_${Grunnlagstype.SAMVÆRSPERIODE}_${fom.toCompactString()}" +
        "${tom?.let { "_${it.toCompactString()}" } ?: ""}_${samvær.rolle.tilGrunnlagPerson().referanse}"

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

fun PrivatAvtale.tilGrunnlagsreferansPrivatAvtale(gjelderBarnReferanse: Grunnlagsreferanse) =
    "${Grunnlagstype.PRIVAT_AVTALE_GRUNNLAG}_${gjelderBarnReferanse}_${stønadstype ?: Stønadstype.BIDRAG}"

fun PrivatAvtalePeriode.tilGrunnlagsreferansPrivatAvtalePeriode(
    gjelderBarnReferanse: Grunnlagsreferanse,
    stønadstype: Stønadstype,
) = "${Grunnlagstype.PRIVAT_AVTALE_PERIODE_GRUNNLAG}_${gjelderBarnReferanse}_" +
    "_${fom.toCompactString()}${tom?.let { "_${it.toCompactString()}" } ?: ""}"
