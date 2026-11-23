package br.ifgoiano.cpr.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackControllerTest {

    @Test
    @DisplayName("Fallback responde 503 no formato padrão de erro, com o nome do módulo")
    void respondeIndisponivel() {
        var resposta = new FallbackController().fallback("pessoas");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resposta.getBody()).containsEntry("erro", "SERVICO_INDISPONIVEL").containsEntry("status", 503);
        assertThat((String) resposta.getBody().get("mensagem")).contains("Pessoas");
    }
}
