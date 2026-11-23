package br.ifgoiano.cpr.categorias.api;

import br.ifgoiano.cpr.categorias.api.PlanoContaDtos.PlanoContaRequest;
import br.ifgoiano.cpr.categorias.api.PlanoContaDtos.PlanoContaResponse;
import br.ifgoiano.cpr.categorias.service.PlanoContaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Plano de Contas", description = "Categorias financeiras hierárquicas")
@RestController
@RequestMapping("/api/plano-contas")
public class PlanoContaController {

    private final PlanoContaService service;

    public PlanoContaController(PlanoContaService service) {
        this.service = service;
    }

    @Operation(summary = "Lista as contas ativas, ordenadas pelo código (árvore)")
    @GetMapping
    public List<PlanoContaResponse> listar() {
        return service.listar().stream().map(PlanoContaResponse::de).toList();
    }

    @Operation(summary = "Detalha uma conta ativa", description = "Usado pelos serviços de contas para validar a categoria do título.")
    @GetMapping("/{id}")
    public PlanoContaResponse buscar(@PathVariable Long id) {
        return PlanoContaResponse.de(service.buscarAtiva(id));
    }

    @Operation(summary = "Cadastra uma conta", description = "A conta superior, se informada, precisa ser consolidada (RN01).")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanoContaResponse cadastrar(@Valid @RequestBody PlanoContaRequest request) {
        return PlanoContaResponse.de(service.cadastrar(request));
    }

    @Operation(summary = "Altera uma conta")
    @PutMapping("/{id}")
    public PlanoContaResponse alterar(@PathVariable Long id, @Valid @RequestBody PlanoContaRequest request) {
        return PlanoContaResponse.de(service.alterar(id, request));
    }

    @Operation(summary = "Exclui (inativa) uma conta", description = "Recusada se a conta tiver subcontas ativas.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.inativar(id);
    }
}
