package no.nav.bidrag.behandling.aop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.net.SocketException

class DefaultExceptionHandlerTest {
    private val defaultExceptionHandler = DefaultExceptionHandler()

    @Test
    fun `skal returnere no content ved client disconnect`() {
        val response = defaultExceptionHandler.handleClientDisconnect(SocketException("Broken pipe"))

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    }

    @Test
    fun `skal returnere internal server error for ukjent feil`() {
        val response = defaultExceptionHandler.handleOtherExceptions(IllegalStateException("ukjent feil"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    }
}
