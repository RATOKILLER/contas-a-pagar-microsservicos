package br.ifgoiano.cpr.contasreceber.integracao;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Chamada síncrona ao categorias-service, com circuit breaker e fallback. */
@FeignClient(name = "categorias-service", fallbackFactory = CadastrosFallbacks.CategoriaFallback.class)
public interface CategoriaClient {

    @GetMapping("/api/plano-contas/{id}")
    CategoriaDTO buscar(@PathVariable("id") Long id);

    record CategoriaDTO(Long id, String codigo, String descricao, boolean consolidada) {}
}
