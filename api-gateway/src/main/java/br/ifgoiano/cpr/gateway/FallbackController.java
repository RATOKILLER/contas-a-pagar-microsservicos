package br.ifgoiano.cpr.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resposta do gateway quando um serviço está fora do ar ou não respondeu a tempo
 * (circuit breaker aberto ou timeout). Em vez de um erro genérico de rede, o cliente
 * recebe 503 no mesmo formato de erro usado por todos os serviços.
 */
@RestController
public class FallbackController {

    private static final Map<String, String> NOMES = Map.of(
            "pessoas", "Pessoas",
            "categorias", "Plano de Contas",
            "contas-pagar", "Contas a Pagar",
            "contas-receber", "Contas a Receber",
            "relatorios", "Relatórios");

    @RequestMapping("/fallback/{servico}")
    public ResponseEntity<Map<String, Object>> fallback(@PathVariable String servico) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("timestamp", OffsetDateTime.now().toString());
        corpo.put("status", 503);
        corpo.put("erro", "SERVICO_INDISPONIVEL");
        corpo.put("mensagem", "O serviço de " + NOMES.getOrDefault(servico, servico)
                + " está temporariamente indisponível. Os demais módulos continuam funcionando; tente novamente em instantes.");
        corpo.put("campos", List.of());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(corpo);
    }
}
