package no.nav.bidrag.behandling.config

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping

class BehandlingIdMdcInterceptorTest {
    private val behandlingRepository = mockk<no.nav.bidrag.behandling.database.repository.BehandlingRepository>()
    private val interceptor = BehandlingIdMdcInterceptor(behandlingRepository)

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    @Test
    fun `skal lagre behandlingId i mdc fra behandlingId pathvariable`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, mapOf("behandlingId" to "123"))
        every { behandlingRepository.hentSaksnummer(123L) } returns "9876543"

        interceptor.preHandle(request, response, Any())

        assertEquals("123", MDC.get(BehandlingIdMdcInterceptor.MDC_BEHANDLING_ID))
        assertEquals("9876543", MDC.get(BehandlingIdMdcInterceptor.MDC_SAKSNUMMER))
    }

    @Test
    fun `skal lagre behandlingId i mdc fra behandlingsid pathvariable`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, mapOf("behandlingsid" to "456"))
        every { behandlingRepository.hentSaksnummer(456L) } returns "7654321"

        interceptor.preHandle(request, response, Any())

        assertEquals("456", MDC.get(BehandlingIdMdcInterceptor.MDC_BEHANDLING_ID))
        assertEquals("7654321", MDC.get(BehandlingIdMdcInterceptor.MDC_SAKSNUMMER))
    }

    @Test
    fun `skal fjerne behandlingId fra mdc naar request ikke har behandlingId`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        MDC.put(BehandlingIdMdcInterceptor.MDC_BEHANDLING_ID, "skal-fjernes")
        MDC.put(BehandlingIdMdcInterceptor.MDC_SAKSNUMMER, "skal-ogsa-fjernes")

        interceptor.preHandle(request, response, Any())

        assertNull(MDC.get(BehandlingIdMdcInterceptor.MDC_BEHANDLING_ID))
        assertNull(MDC.get(BehandlingIdMdcInterceptor.MDC_SAKSNUMMER))
    }

    @Test
    fun `skal ikke sette saksnummer i mdc naar behandlingId ikke er numerisk`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, mapOf("behandlingId" to "abc"))

        interceptor.preHandle(request, response, Any())

        assertEquals("abc", MDC.get(BehandlingIdMdcInterceptor.MDC_BEHANDLING_ID))
        assertNull(MDC.get(BehandlingIdMdcInterceptor.MDC_SAKSNUMMER))
    }

    @Test
    fun `skal ikke feile hvis oppslag av saksnummer feiler`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, mapOf("behandlingId" to "123"))
        every { behandlingRepository.hentSaksnummer(123L) } throws RuntimeException("db-feil")

        interceptor.preHandle(request, response, Any())

        assertEquals("123", MDC.get(BehandlingIdMdcInterceptor.MDC_BEHANDLING_ID))
        assertNull(MDC.get(BehandlingIdMdcInterceptor.MDC_SAKSNUMMER))
    }

    @Test
    fun `skal rydde mdc ved afterCompletion`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        MDC.put(BehandlingIdMdcInterceptor.MDC_BEHANDLING_ID, "123")
        MDC.put(BehandlingIdMdcInterceptor.MDC_SAKSNUMMER, "456")

        interceptor.afterCompletion(request, response, Any(), null)

        assertNull(MDC.get(BehandlingIdMdcInterceptor.MDC_BEHANDLING_ID))
        assertNull(MDC.get(BehandlingIdMdcInterceptor.MDC_SAKSNUMMER))
    }

    @Test
    fun `skal rydde mdc ved afterConcurrentHandlingStarted`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        MDC.put(BehandlingIdMdcInterceptor.MDC_BEHANDLING_ID, "123")
        MDC.put(BehandlingIdMdcInterceptor.MDC_SAKSNUMMER, "456")

        interceptor.afterConcurrentHandlingStarted(request, response, Any())

        assertNull(MDC.get(BehandlingIdMdcInterceptor.MDC_BEHANDLING_ID))
        assertNull(MDC.get(BehandlingIdMdcInterceptor.MDC_SAKSNUMMER))
    }
}
