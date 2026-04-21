package no.nav.bidrag.behandling.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.servlet.AsyncHandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

@Component
class BehandlingIdMdcInterceptor : AsyncHandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val behandlingId = request.hentBehandlingIdFraPathVariable()
        if (behandlingId == null) {
            MDC.remove(MDC_BEHANDLING_ID)
        } else {
            MDC.put(MDC_BEHANDLING_ID, behandlingId)
        }
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        MDC.remove(MDC_BEHANDLING_ID)
    }

    override fun afterConcurrentHandlingStarted(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ) {
        MDC.remove(MDC_BEHANDLING_ID)
    }

    private fun HttpServletRequest.hentBehandlingIdFraPathVariable(): String? {
        val pathVariabler =
            getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)
                as? Map<*, *>
                ?: return null

        return pathVariabler.entries
            .firstOrNull { it.key.toString().lowercase() in BEHANDLING_ID_PATH_VARIABELNAVN }
            ?.value
            ?.toString()
            ?.takeIf { it.isNotBlank() }
    }

    companion object {
        const val MDC_BEHANDLING_ID = "behandlingId"
        private val BEHANDLING_ID_PATH_VARIABELNAVN = setOf("behandlingid", "behandlingsid")
    }
}
