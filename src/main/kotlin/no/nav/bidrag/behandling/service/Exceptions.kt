package no.nav.bidrag.behandling.service

import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

fun `404`(behandlingId: Long): Nothing =
    throw HttpClientErrorException(HttpStatus.NOT_FOUND, "Fant ikke behandling med id $behandlingId")
