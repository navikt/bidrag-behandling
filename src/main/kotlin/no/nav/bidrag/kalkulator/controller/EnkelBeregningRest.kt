package no.nav.bidrag.kalkulator.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/kalkulator/beregning/enkel")
class EnkelBeregningRest {

    @GetMapping
    @Operation(
        description = "Gjøre en forenklet beregning av barnebidrag til bidragskalkulatoren",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Resultatet av en beregning",
            ),
            ApiResponse(responseCode = "400", description = "Ugyldig input"),
            ApiResponse(
                responseCode = "500",
                description = "Serverfeil",
            ),
            ApiResponse(responseCode = "503", description = "Tjeneste utilgjengelig"),
        ],
    )
    fun gjørEnkelBeregning(dto: EnkelBeregningRequestDto): EnkelBeregningResponseDto {
        return EnkelBeregningResponseDto(1330)
    }
}

data class EnkelBeregningResponseDto(
    val resultat: Number
)

data class EnkelBeregningRequestDto(
    val inntektForelder1: Number,
    val inntektForelder2: Number,
    val barn: List<Barn>
) {
    data class Barn(
        val alder: Number,
        val samværsgrad: Number,
    )
}