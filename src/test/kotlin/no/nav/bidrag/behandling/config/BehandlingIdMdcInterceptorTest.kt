package no.nav.bidrag.behandling.config

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping

class BehandlingIdMdcInterceptorTest {
    private val interceptor = BehandlingIdMdcInterceptor()

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    @Test
    fun `skal lagre behandlingId i mdc fra behandlingId pathvariable`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, mapOf("behandlingId" to "123"))

        interceptor.preHandle(request, response, Any())

        assertEquals("123", MDC.get(BehandlingIdMdcInterceptor.MDC_BEHANDLING_ID))
    }

    @Test
    fun `skal lagre behandlingId i mdc fra behandlingsid pathvariable`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, mapOf("behandlingsid" to "456"))

        interceptor.preHandle(request, response, Any())

        assertEquals("456", MDC.get(BehandlingIdMdcInterceptor.MDC_BEHANDLING_ID))
    }

    @Test
    fun `skal fjerne behandlingId fra mdc naar request ikke har behandlingId`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        MDC.put(BehandlingIdMdcInterceptor.MDC_BEHANDLING_ID, "skal-fjernes")

        interceptor.preHandle(request, response, Any())

        assertNull(MDC.get(BehandlingIdMdcInterceptor.MDC_BEHANDLING_ID))
    }

    @Test
    fun `skal rydde mdc ved afterCompletion`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        MDC.put(BehandlingIdMdcInterceptor.MDC_BEHANDLING_ID, "123")

        interceptor.afterCompletion(request, response, Any(), null)

        assertNull(MDC.get(BehandlingIdMdcInterceptor.MDC_BEHANDLING_ID))
    }

    @Test
    fun `skal rydde mdc ved afterConcurrentHandlingStarted`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        MDC.put(BehandlingIdMdcInterceptor.MDC_BEHANDLING_ID, "123")

        interceptor.afterConcurrentHandlingStarted(request, response, Any())

        assertNull(MDC.get(BehandlingIdMdcInterceptor.MDC_BEHANDLING_ID))
    }
}
