package br.ifgoiano.cpr.contaspagar.api;

import br.ifgoiano.cpr.contaspagar.api.ContaPagarDtos.ContaRequest;
import br.ifgoiano.cpr.contaspagar.api.ContaPagarDtos.ContaResponse;
import br.ifgoiano.cpr.contaspagar.api.ContaPagarDtos.QuitacaoRequest;
import br.ifgoiano.cpr.contaspagar.domain.SituacaoConta;
import br.ifgoiano.cpr.contaspagar.service.ContaPagarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Contas a Pagar", description = "Títulos a pagar e registro de pagamento")
@RestController
@RequestMapping("/api/contas-pagar")
public class ContaPagarController {

    private final ContaPagarService service;

    public ContaPagarController(ContaPagarService service) {
        this.service = service;
    }

    @Operation(summary = "Lista títulos", description = "Filtros opcionais: período de vencimento (inicio/fim), situação e pessoa.")
    @GetMapping
    public List<ContaResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) SituacaoConta situacao,
            @RequestParam(required = false) Long pessoaId) {
        return service.listar(inicio, fim, situacao, pessoaId).stream().map(ContaResponse::de).toList();
    }

    @Operation(summary = "Detalha um título")
    @GetMapping("/{id}")
    public ContaResponse buscar(@PathVariable Long id) {
        return ContaResponse.de(service.buscar(id));
    }

    @Operation(summary = "Lança um título", description = "Confirma pessoa (fornecedor) e categoria (não consolidada) nos serviços donos desses dados.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContaResponse lancar(@Valid @RequestBody ContaRequest request) {
        return ContaResponse.de(service.lancar(request));
    }

    @Operation(summary = "Altera um título em aberto ou vencido")
    @PutMapping("/{id}")
    public ContaResponse alterar(@PathVariable Long id, @Valid @RequestBody ContaRequest request) {
        return ContaResponse.de(service.alterar(id, request));
    }

    @Operation(summary = "Cancela um título", description = "O título não é apagado: passa para CANCELADA e continua nas consultas (RN07).")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable Long id) {
        service.cancelar(id);
    }

    @Operation(summary = "Registra o pagamento (quitação)",
            description = "Desconto só vale até a validade (RN05); sem desconto/acréscimo informados, são calculados pela data. "
                    + "Envie o header Idempotency-Key: repetir a requisição com a mesma chave não paga duas vezes.")
    @PostMapping("/{id}/pagamento")
    public ResponseEntity<ContaResponse> quitar(
            @PathVariable Long id,
            @Valid @RequestBody QuitacaoRequest request,
            @Parameter(description = "Identificador único da tentativa de pagamento (ex.: um UUID gerado ao abrir a tela)")
            @RequestHeader(value = "Idempotency-Key", required = false) String chave) {
        ContaPagarService.Resultado r = service.quitar(id, request, chave);
        return ResponseEntity.ok()
                .header("Idempotent-Replay", String.valueOf(r.repetida()))
                .body(ContaResponse.de(r.conta()));
    }
}
