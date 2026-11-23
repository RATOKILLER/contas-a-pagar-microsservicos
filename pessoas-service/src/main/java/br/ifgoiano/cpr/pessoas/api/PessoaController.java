package br.ifgoiano.cpr.pessoas.api;

import br.ifgoiano.cpr.pessoas.domain.TipoPessoa;
import br.ifgoiano.cpr.pessoas.service.PessoaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Pessoas", description = "Clientes e fornecedores")
@RestController
@RequestMapping("/api/pessoas")
public class PessoaController {

    private final PessoaService service;

    public PessoaController(PessoaService service) {
        this.service = service;
    }

    @Operation(summary = "Lista pessoas ativas", description = "Filtros opcionais: tipo e parte do nome. Pessoas AMBOS aparecem nos dois tipos.")
    @GetMapping
    public List<PessoaResponse> listar(@RequestParam(required = false) TipoPessoa tipo,
                                       @RequestParam(required = false) String nome) {
        return service.listar(tipo, nome).stream().map(PessoaResponse::de).toList();
    }

    @Operation(summary = "Detalha uma pessoa ativa", description = "Usado pelos serviços de contas para validar o título (404 se não existir ou estiver inativa).")
    @GetMapping("/{id}")
    public PessoaResponse buscar(@PathVariable Long id) {
        return PessoaResponse.de(service.buscarAtiva(id));
    }

    @Operation(summary = "Cadastra uma pessoa")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PessoaResponse cadastrar(@Valid @RequestBody PessoaRequest request) {
        return PessoaResponse.de(service.cadastrar(request));
    }

    @Operation(summary = "Altera uma pessoa", description = "Publica pessoa.atualizada para os serviços de contas atualizarem a réplica do nome.")
    @PutMapping("/{id}")
    public PessoaResponse alterar(@PathVariable Long id, @Valid @RequestBody PessoaRequest request) {
        return PessoaResponse.de(service.alterar(id, request));
    }

    @Operation(summary = "Exclui (inativa) uma pessoa", description = "Exclusão lógica: os títulos já lançados continuam consultáveis.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.inativar(id);
    }
}
