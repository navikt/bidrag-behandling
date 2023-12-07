package no.nav.bidrag.behandling.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.HusstandsBarn
import no.nav.bidrag.behandling.database.datamodell.OpplysningerType
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.database.datamodell.Sivilstand
import no.nav.bidrag.behandling.dto.notat.Barnetillegg
import no.nav.bidrag.behandling.dto.notat.Boforhold
import no.nav.bidrag.behandling.dto.notat.BoforholdBarn
import no.nav.bidrag.behandling.dto.notat.Inntekter
import no.nav.bidrag.behandling.dto.notat.InntekterPerRolle
import no.nav.bidrag.behandling.dto.notat.InntekterSomLeggesTilGrunn
import no.nav.bidrag.behandling.dto.notat.Notat
import no.nav.bidrag.behandling.dto.notat.NotatDto
import no.nav.bidrag.behandling.dto.notat.OpplysningerBruktTilBeregning
import no.nav.bidrag.behandling.dto.notat.OpplysningerFraFolkeregisteret
import no.nav.bidrag.behandling.dto.notat.ParterISøknad
import no.nav.bidrag.behandling.dto.notat.SivilstandNotat
import no.nav.bidrag.behandling.dto.notat.UtvidetBarnetrygd
import no.nav.bidrag.behandling.dto.notat.Virkningstidspunkt
import no.nav.bidrag.behandling.transformers.toLocalDate
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.service.organisasjon.SaksbehandlernavnProvider
import no.nav.bidrag.domene.enums.inntekt.Inntektsrapportering
import no.nav.bidrag.domene.enums.person.Bostatuskode
import no.nav.bidrag.domene.enums.person.Sivilstandskode
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.domene.ident.Personident
import no.nav.bidrag.domene.tid.ÅrMånedsperiode
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth

private val objectmapper = ObjectMapper().findAndRegisterModules()

@Service
class NotatOpplysningerService(
    private val behandlingService: BehandlingService,
    private val opplysningerService: OpplysningerService
) {


    fun hentNotatOpplysninger(behandlingId: Long): NotatDto {
        val behandling = behandlingService.hentBehandlingById(behandlingId)
        val opplysningerBoforholdJson =
            opplysningerService.hentSistAktiv(behandlingId, OpplysningerType.BOFORHOLD)
                ?.let { objectmapper.readTree(it.data) }
        val husstandGrunnlag = opplysningerBoforholdJson?.get("husstand")?.toList() ?: emptyList()
        val sivilstandGrunnlag =
            opplysningerBoforholdJson?.get("sivilstand")?.toList() ?: emptyList()
        val opplysningerInntekterJson =
            opplysningerService.hentSistAktiv(behandlingId, OpplysningerType.INNTEKTSOPPLYSNINGER)
                ?.let { objectmapper.readTree(it.data) }
        val inntektGrunnlag = opplysningerInntekterJson?.get("inntekt")?.toList() ?: emptyList()
        val utvidetbarnetrygdGrunnlag =
            opplysningerInntekterJson?.get("utvidetbarnetrygd")?.toList() ?: emptyList()
        val barnetilleggGrunnlag =
            opplysningerInntekterJson?.get("barnetillegg")?.toList() ?: emptyList()
        return NotatDto(
            saksnummer = behandling.saksnummer,
            saksbehandlerNavn = TokenUtils.hentSaksbehandlerIdent()
                ?.let { SaksbehandlernavnProvider.hentSaksbehandlernavn(it) },
            virkningstidspunkt = behandling.tilVirkningstidspunkt(),
            boforhold = Boforhold(
                notat = behandling.tilNotatBoforhold(),
                sivilstand = behandling.tilSivilstand(sivilstandGrunnlag),
                barn = behandling.husstandsBarn.map { it.tilBoforholdBarn(husstandGrunnlag) }
            ),
            parterISøknad = behandling.roller.map(Rolle::tilPartISøknad),
            inntekter = Inntekter(
                notat = behandling.tilNotatInntekt(),
                inntekterPerRolle = behandling.roller.map {
                    behandling.hentInntekterForIdent(it.ident!!, it.rolleType)
                }
            ),
            vedtak = emptyList()
        )
    }

}

private fun Behandling.tilNotatBoforhold() = Notat(
    medIVedtaket = boforholdBegrunnelseMedIVedtakNotat,
    intern = boforholdBegrunnelseKunINotat
)

private fun Behandling.tilNotatVirkningstidspunkt() = Notat(
    medIVedtaket = virkningsTidspunktBegrunnelseMedIVedtakNotat,
    intern = virkningsTidspunktBegrunnelseKunINotat
)

private fun Behandling.tilNotatInntekt() = Notat(
    medIVedtaket = inntektBegrunnelseMedIVedtakNotat,
    intern = inntektBegrunnelseKunINotat
)

private fun Behandling.tilSivilstand(sivilstandGrunnlag: List<JsonNode>) = SivilstandNotat(
    opplysningerBruktTilBeregning = sivilstand.map(Sivilstand::tilSivilstandsperiode),
    opplysningerFraFolkeregisteret = sivilstandGrunnlag.map { periode ->
        OpplysningerFraFolkeregisteret(
            periode = ÅrMånedsperiode(
                LocalDate.parse(periode.get("datoFom").asText()),
                periode.get("datoTom").asText().takeIf { date -> date != "null" }
                    ?.let { date -> LocalDate.parse(date) }
            ),
            status = periode.get("sivilstand")?.asText()
                ?.let { it1 -> Sivilstandskode.valueOf(it1) }
        )
    }
)

private fun Sivilstand.tilSivilstandsperiode() =
    OpplysningerBruktTilBeregning(
        periode = ÅrMånedsperiode(
            datoFom!!.toLocalDate(),
            datoTom?.toLocalDate()
        ),
        status = sivilstand,
        kilde = kilde.name
    )

private fun Behandling.tilVirkningstidspunkt() = Virkningstidspunkt(
    søknadstype = soknadType.name,
    søktAv = soknadFra,
    mottattDato = YearMonth.from(mottatDato.toLocalDate()),
    søktFraDato = YearMonth.from(datoFom.toLocalDate()),
    virkningstidspunkt = virkningsDato?.toLocalDate(),
    notat = tilNotatVirkningstidspunkt()
)

private fun HusstandsBarn.tilBoforholdBarn(husstandGrunnlag: List<JsonNode>) = BoforholdBarn(
    navn = navn!!,
    fødselsdato = foedselsDato?.toLocalDate()
        ?: hentPersonFødselsdato(ident),
    opplysningerFraFolkeregisteret = husstandGrunnlag.filter {
        it.get("ident").textValue() == this.ident
    }.flatMap {
        it.get("perioder")?.toList()?.map { periode ->
            OpplysningerFraFolkeregisteret(
                periode = ÅrMånedsperiode(
                    LocalDate.parse(periode.get("fraDato").asText().split("T")[0]),
                    periode.get("tilDato").asText().takeIf { date -> date != "null" }
                        ?.let { date -> LocalDate.parse(date.split("T")[0]) }
                ),
                status = periode.get("bostatus")?.asText()?.let { it1 -> Bostatuskode.valueOf(it1) }
            )
        } ?: emptyList()

    },
    opplysningerBruktTilBeregning = perioder.sortedBy { it.datoFom }.map { periode ->
        OpplysningerBruktTilBeregning(
            periode = ÅrMånedsperiode(
                periode.datoFom!!.toLocalDate(),
                periode.datoTom?.toLocalDate()
            ),
            status = periode.bostatus!!,
            kilde = periode.kilde.name
        )
    }
)

private fun Rolle.tilPartISøknad() = ParterISøknad(
    rolle = rolleType,
    navn = hentPersonVisningsnavn(ident),
    fødselsdato = fodtDato?.toLocalDate() ?: hentPersonFødselsdato(ident),
    personident = ident?.let { Personident(it) }
)

private fun Behandling.hentInntekterForIdent(ident: String, rolle: Rolletype) = InntekterPerRolle(
    rolle = rolle,
    inntekterSomLeggesTilGrunn = inntekter.sortedBy { it.datoFom }
        .filter { it.ident == ident && it.taMed }
        .map {
            InntekterSomLeggesTilGrunn(
                beløp = it.belop,
                periode = ÅrMånedsperiode(it.datoFom!!.toLocalDate(), it.datoTom?.toLocalDate()),
                beskrivelse = it.inntektType,
                inntektType = it.inntektType?.let { it1 -> Inntektsrapportering.valueOf(it1) }
            )
        },
    barnetillegg = barnetillegg.sortedBy { it.datoFom }.map {
        Barnetillegg(
            periode = ÅrMånedsperiode(it.datoFom!!.toLocalDate(), it.datoTom?.toLocalDate()),
            beløp = it.barnetillegg
        )
    },
    utvidetBarnetrygd = utvidetbarnetrygd.sortedBy { it.datoFom }.map {
        UtvidetBarnetrygd(
            periode = ÅrMånedsperiode(it.datoFom!!.toLocalDate(), it.datoTom?.toLocalDate()),
            beløp = it.belop
        )
    },
    arbeidsforhold = emptyList()
)