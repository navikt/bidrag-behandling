package no.nav.bidrag.behandling.config

import no.nav.bidrag.commons.security.SikkerhetsKontekst
import no.nav.bidrag.commons.security.utils.TokenUtils
import no.nav.bidrag.commons.web.MdcConstants.MDC_USER_ID
import org.slf4j.MDC
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskDecorator
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class AsyncConfig : AsyncConfigurer {
    override fun getAsyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 5
        executor.maxPoolSize = 10
        executor.queueCapacity = 25
        executor.setThreadNamePrefix("Async-")
        executor.setTaskDecorator(ThreadLocalTaskDecorator())
        executor.initialize()
        return executor
    }

    class ThreadLocalTaskDecorator : TaskDecorator {
        override fun decorate(runnable: Runnable): Runnable {
            val erIApplikasjonskontekst = SikkerhetsKontekst.erIApplikasjonKontekst()
            val contextMap = MDC.getCopyOfContextMap() ?: emptyMap()
            val saksbehandlerIdent = TokenUtils.hentSaksbehandlerIdent()
            return Runnable {
                try {
                    MDC.setContextMap(contextMap)
                    MDC.put(MDC_USER_ID, saksbehandlerIdent)
                    if (erIApplikasjonskontekst) {
                        SikkerhetsKontekst.medApplikasjonKontekst {
                            runnable.run()
                        }
                    } else {
                        runnable.run()
                    }
                } finally {
                    // Reset state
                    MDC.clear()
                }
            }
        }
    }
}
