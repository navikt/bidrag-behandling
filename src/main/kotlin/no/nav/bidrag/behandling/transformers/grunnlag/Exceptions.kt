package no.nav.bidrag.behandling.transformers.grunnlag

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

fun innhentetGrunnlagHarFlereRelatertePersonMedSammeId(): Nothing =
    throw HttpClientErrorException(
        HttpStatus.BAD_REQUEST,
        "Innhentet grunnlag for husstandsmedlemmer har flere relaterte personer med samme personId",
    )
