package no.nav.bidrag.behandling.transformers.vedtak.mapping.tilvedtak

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Behandling
import no.nav.bidrag.behandling.database.datamodell.særbidragKategori
import no.nav.bidrag.behandling.transformers.vedtak.ifTrue
import no.nav.bidrag.domene.enums.grunnlag.Grunnlagstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.SærbidragskategoriGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.UtgiftDirekteBetaltGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.UtgiftMaksGodkjentBeløpGrunnlag
import no.nav.bidrag.transport.behandling.felles.grunnlag.UtgiftspostGrunnlag

fun Behandling.byggGrunnlagUtgiftsposter() =
    setOf(
        GrunnlagDto(
            referanse = grunnlagsreferanse_utgiftsposter,
            type = Grunnlagstype.UTGIFTSPOSTER,
            innhold =
                POJONode(
                    utgift!!.utgiftsposter.map {
                        UtgiftspostGrunnlag(
                            dato = it.dato,
                            type = it.type,
                            kravbeløp = it.kravbeløp,
                            godkjentBeløp = it.godkjentBeløp,
                            kommentar = it.kommentar,
                            betaltAvBp = it.betaltAvBp,
                        )
                    },
                ),
        ),
    )

fun Behandling.byggGrunnlagUtgiftMaksGodkjentBeløp() =
    utgift!!.maksGodkjentBeløpTaMed.ifTrue {
        setOf(
            GrunnlagDto(
                referanse = grunnlagsreferanse_utgift_maks_godkjent_beløp,
                type = Grunnlagstype.UTGIFT_MAKS_GODKJENT_BELØP,
                innhold =
                    POJONode(
                        UtgiftMaksGodkjentBeløpGrunnlag(
                            beløp = utgift!!.maksGodkjentBeløp!!,
                            begrunnelse = utgift!!.maksGodkjentBeløpBegrunnelse!!,
                        ),
                    ),
            ),
        )
    } ?: emptySet()

fun Behandling.byggGrunnlagUtgiftDirekteBetalt() =
    setOf(
        GrunnlagDto(
            referanse = grunnlagsreferanse_utgift_direkte_betalt,
            type = Grunnlagstype.UTGIFT_DIREKTE_BETALT,
            innhold =
                POJONode(
                    UtgiftDirekteBetaltGrunnlag(
                        beløpDirekteBetalt = utgift!!.beløpDirekteBetaltAvBp,
                    ),
                ),
        ),
    )

fun Behandling.byggGrunnlagSærbidragKategori() =
    setOf(
        GrunnlagDto(
            referanse = "særbidrag_kategori",
            type = Grunnlagstype.SÆRBIDRAG_KATEGORI,
            innhold =
                POJONode(
                    SærbidragskategoriGrunnlag(
                        kategori = særbidragKategori,
                        beskrivelse = kategoriBeskrivelse,
                    ),
                ),
        ),
    )
