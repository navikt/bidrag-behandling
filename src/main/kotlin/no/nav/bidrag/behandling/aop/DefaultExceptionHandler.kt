package no.nav.bidrag.behandling.aop

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import no.nav.bidrag.behandling.BeregningAvResultatForBehandlingFeilet
import no.nav.bidrag.commons.util.secureLogger
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.convert.ConversionFailedException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
@Suppress("unused")
class DefaultExceptionHandler {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultExceptionHandler::class.java)
    }

    @ResponseBody
    @ExceptionHandler(
        value = [
            MethodArgumentNotValidException::class, InvalidFormatException::class, IllegalArgumentException::class,
            MethodArgumentTypeMismatchException::class, ConversionFailedException::class, HttpMessageNotReadableException::class,
        ],
    )
    fun handleInvalidValueExceptions(exception: Exception): ResponseEntity<*> {
        val cause = exception.cause ?: exception
        val validationError =
            when (cause) {
                is JsonMappingException -> createMissingKotlinParameterViolation(cause)
                is MethodArgumentNotValidException -> parseMethodArgumentNotValidException(cause)
                else -> null
            }
        val feilmelding =
            validationError?.fieldErrors?.joinToString(", ") { "${it.field}: ${it.message}" }
                ?: exception.message

        LOGGER.error(feilmelding, exception)

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .header(
                HttpHeaders.WARNING,
                feilmelding,
            )
            .build<Any>()
    }

    @ResponseBody
    @ExceptionHandler(HttpStatusCodeException::class)
    fun handleHttpClientErrorException(exception: HttpStatusCodeException): ResponseEntity<*> {
        val feilmelding = getErrorMessage(exception)
        LOGGER.warn(feilmelding, exception)
        secureLogger.warn(exception) { feilmelding }
        return ResponseEntity.status(exception.statusCode)
            .header(HttpHeaders.WARNING, feilmelding)
            .build<Any>()
    }

    @ResponseBody
    @ExceptionHandler(BeregningAvResultatForBehandlingFeilet::class)
    fun handleBeregningAvResultatForBehandlingFeilet(exception: BeregningAvResultatForBehandlingFeilet): ResponseEntity<*> {
        LOGGER.warn(exception.message, exception)
        val response = ResponseEntity.status(exception.statusCode)
        exception.feilmeldinger.forEach { response.header(HttpHeaders.WARNING, it) }
        return response.build<Any>()
    }

    private fun getErrorMessage(exception: HttpStatusCodeException): String {
        val errorMessage = StringBuilder()
        errorMessage.append("Validering feilet")
        exception.responseHeaders?.get(HttpHeaders.WARNING)?.firstOrNull()
            ?.let { errorMessage.append(it) }
        if (exception.statusText.isNotEmpty()) {
            errorMessage.append(" - ")
            errorMessage.append(exception.statusText)
        }
        return errorMessage.toString()
    }

    private fun createMissingKotlinParameterViolation(ex: JsonMappingException): Error {
        val error = Error(HttpStatus.BAD_REQUEST.value(), "validation error")
        ex.path.filter { it.fieldName != null }.forEach {
            error.addFieldError(it.from.toString(), it.fieldName, ex.message.toString())
        }
        return error
    }

    private fun parseMethodArgumentNotValidException(ex: MethodArgumentNotValidException): Error {
        val error = Error(HttpStatus.BAD_REQUEST.value(), "validation error")
        ex.fieldErrors.forEach {
            val message: String =
                if (it.defaultMessage == null) it.toString() else it.defaultMessage!!
            error.addFieldError(it.objectName, it.field, message)
        }
        return error
    }

    data class Error(
        val status: Int,
        val message: String,
        val fieldErrors: MutableList<CustomFieldError> = mutableListOf(),
    ) {
        fun addFieldError(
            objectName: String,
            field: String,
            message: String,
        ) {
            val error = CustomFieldError(objectName, field, message)
            fieldErrors.add(error)
        }
    }

    data class CustomFieldError(val objectName: String, val field: String, val message: String)
}
