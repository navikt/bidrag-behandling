package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.dto.v1.beregning.ResultatForskuddsberegningBarn
import no.nav.bidrag.behandling.rolleManglerIdent
import no.nav.bidrag.behandling.transformers.grunnlag.StønadsendringPeriode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.enums.vedtak.Stønadstype
import no.nav.bidrag.transport.behandling.vedtak.request.OpprettPeriodeRequestDto
import java.math.BigDecimal

fun ResultatForskuddsberegningBarn.byggStønadsendringerForVedtak(behandling: Behandling): StønadsendringPeriode {
    val søknadsbarn =
        behandling.søknadsbarn.find { it.ident == barn.ident?.verdi }
            ?: rolleManglerIdent(Rolletype.BARN, behandling.id!!)

    val grunnlagListe = resultat.grunnlagListe.toSet()
    val periodeliste =
        resultat.beregnetForskuddPeriodeListe.map {
            OpprettPeriodeRequestDto(
                periode = it.periode,
                beløp =
                    if (behandling.stonadstype == Stønadstype.FORSKUDD &&
                        it.resultat.belop <= BigDecimal.ZERO
                    ) {
                        null
                    } else {
                        it.resultat.belop
                    },
                valutakode = "NOK",
                resultatkode = it.resultat.kode.name,
                grunnlagReferanseListe = it.grunnlagsreferanseListe,
            )
        }

    return StønadsendringPeriode(
        søknadsbarn,
        periodeliste,
        grunnlagListe,
    )
}
