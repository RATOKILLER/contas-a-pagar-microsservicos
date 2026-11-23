package br.ifgoiano.cpr.contaspagar.integracao;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Chamada síncrona ao pessoas-service. O endereço vem do Eureka pelo nome "pessoas-service";
 * a chamada passa por um circuit breaker (Resilience4j) e, em caso de falha, pelo fallback.
 */
@FeignClient(name = "pessoas-service", fallbackFactory = CadastrosFallbacks.PessoaFallback.class)
public interface PessoaClient {

    @GetMapping("/api/pessoas/{id}")
    PessoaDTO buscar(@PathVariable("id") Long id);

    record PessoaDTO(Long id, String tipo, String nome, String documento) {
        public boolean podeSerFornecedor() {
            return "FORNECEDOR".equals(tipo) || "AMBOS".equals(tipo);
        }

        public boolean podeSerCliente() {
            return "CLIENTE".equals(tipo) || "AMBOS".equals(tipo);
        }
    }
}
