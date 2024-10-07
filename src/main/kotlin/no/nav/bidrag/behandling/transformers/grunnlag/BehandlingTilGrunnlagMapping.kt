package no.nav.bidrag.behandling.transformers.grunnlag

import com.fasterxml.jackson.databind.node.POJONode
import no.nav.bidrag.behandling.database.datamodell.Rolle
import no.nav.bidrag.behandling.fantIkkeFødselsdatoTilSøknadsbarn
import no.nav.bidrag.behandling.service.hentNyesteIdent
import no.nav.bidrag.behandling.service.hentPersonFødselsdato
import no.nav.bidrag.behandling.service.hentPersonVisningsnavn
import no.nav.bidrag.domene.enums.rolle.Rolletype
import no.nav.bidrag.transport.behandling.felles.grunnlag.GrunnlagDto
import no.nav.bidrag.transport.behandling.felles.grunnlag.Person
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilGrunnlagstype
import no.nav.bidrag.transport.behandling.felles.grunnlag.tilPersonreferanse
import no.nav.bidrag.transport.felles.toCompactString
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.LocalDate

fun Rolle.tilGrunnlagsreferanse() = rolletype.tilGrunnlagstype().tilPersonreferanse(fødselsdato.toCompactString(), id!!.toInt())

fun Rolle.tilGrunnlagPerson(): GrunnlagDto {
    val grunnlagstype = rolletype.tilGrunnlagstype()
    return GrunnlagDto(
        referanse = tilGrunnlagsreferanse(),
        type = grunnlagstype,
        innhold =
            POJONode(
                Person(
                    ident = ident.takeIf { !it.isNullOrEmpty() }?.let { hentNyesteIdent(it) },
                    navn = if (ident.isNullOrEmpty()) navn ?: hentPersonVisningsnavn(ident) else null,
                    fødselsdato =
                        finnFødselsdato(
                            ident,
                            fødselsdato,
                        ) // Avbryter prosesering dersom fødselsdato til søknadsbarn er ukjent
                            ?: fantIkkeFødselsdatoTilSøknadsbarn(behandling.id ?: -1),
                ).valider(rolletype),
            ),
    )
}

fun finnFødselsdato(
    ident: String?,
    fødselsdato: LocalDate?,
): LocalDate? =
    if (fødselsdato == null && ident != null) {
        hentPersonFødselsdato(ident)
    } else {
        fødselsdato
    }

fun Person.valider(rolle: Rolletype? = null): Person {
    if ((ident == null || ident!!.verdi.isEmpty()) && navn.isNullOrEmpty()) {
        throw HttpClientErrorException(
            HttpStatus.BAD_REQUEST,
            "Person med fødselsdato $fødselsdato og rolle $rolle mangler ident men har ikke navn. Ident eller Navn må være satt",
        )
    }
    return this
}
