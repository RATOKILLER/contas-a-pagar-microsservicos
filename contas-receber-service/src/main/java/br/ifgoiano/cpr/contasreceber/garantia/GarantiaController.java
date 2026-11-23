package br.ifgoiano.cpr.contasreceber.garantia;

import br.ifgoiano.cpr.contasreceber.domain.ContaReceber;
import br.ifgoiano.cpr.contasreceber.domain.ContaReceberRepository;
import br.ifgoiano.cpr.contasreceber.garantia.GarantiaDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** RN08 (cheque pré-datado) e RN09 (cartório e remessa a protesto), no menu Contas a Receber do livro. */
@RestController
@RequestMapping("/api/contas-receber")
public class GarantiaController {

    private final ChequeService cheques;
    private final CartorioService cartorios;
    private final ContaReceberRepository contas;

    public GarantiaController(ChequeService cheques, CartorioService cartorios, ContaReceberRepository contas) {
        this.cheques = cheques;
        this.cartorios = cartorios;
        this.contas = contas;
    }

    // ---------------- Cheques pré-datados ----------------

    @Tag(name = "Cheques pré-datados")
    @Operation(summary = "Lista cheques", description = "Ordenados pela data de depósito (bom para). Filtro opcional por situação.")
    @GetMapping("/cheques")
    public List<ChequeResponse> listarCheques(@RequestParam(required = false) SituacaoCheque situacao) {
        List<ChequePreDatado> lista = cheques.listar(situacao);
        Map<Long, ContaReceber> titulos = titulos(lista.stream().map(ChequePreDatado::getContaReceberId).toList());
        return lista.stream().map(c -> ChequeResponse.de(c, titulos.get(c.getContaReceberId()))).toList();
    }

    @Tag(name = "Cheques pré-datados")
    @Operation(summary = "Registra cheque recebido como garantia de um título em aberto",
            description = "A soma dos cheques vigentes não pode passar do valor do título; o mesmo cheque não entra duas vezes.")
    @PostMapping("/cheques")
    @ResponseStatus(HttpStatus.CREATED)
    public ChequeResponse registrarCheque(@Valid @RequestBody ChequeRequest req) {
        return comTitulo(cheques.registrar(req));
    }

    @Tag(name = "Cheques pré-datados")
    @Operation(summary = "Deposita o cheque", description = "Recusado antes da data 'bom para'.")
    @PostMapping("/cheques/{id}/deposito")
    public ChequeResponse depositar(@PathVariable Long id, @Valid @RequestBody DataRequest req) {
        return comTitulo(cheques.depositar(id, req.data()));
    }

    @Tag(name = "Cheques pré-datados")
    @Operation(summary = "Registra a devolução do cheque pelo banco (sem fundos)")
    @PostMapping("/cheques/{id}/devolucao")
    public ChequeResponse devolver(@PathVariable Long id) {
        return comTitulo(cheques.devolver(id));
    }

    @Tag(name = "Cheques pré-datados")
    @Operation(summary = "Cancela um cheque ainda não depositado (devolvido ao cliente)")
    @DeleteMapping("/cheques/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelarCheque(@PathVariable Long id) {
        cheques.cancelar(id);
    }

    // ---------------- Cartórios ----------------

    @Tag(name = "Cartórios")
    @Operation(summary = "Lista cartórios ativos")
    @GetMapping("/cartorios")
    public List<CartorioResponse> listarCartorios() {
        return cartorios.listarCartorios().stream().map(CartorioResponse::de).toList();
    }

    @Tag(name = "Cartórios")
    @Operation(summary = "Cadastra cartório")
    @PostMapping("/cartorios")
    @ResponseStatus(HttpStatus.CREATED)
    public CartorioResponse cadastrarCartorio(@Valid @RequestBody CartorioRequest req) {
        return CartorioResponse.de(cartorios.cadastrarCartorio(req));
    }

    @Tag(name = "Cartórios")
    @Operation(summary = "Altera cartório")
    @PutMapping("/cartorios/{id}")
    public CartorioResponse alterarCartorio(@PathVariable Long id, @Valid @RequestBody CartorioRequest req) {
        return CartorioResponse.de(cartorios.alterarCartorio(id, req));
    }

    @Tag(name = "Cartórios")
    @Operation(summary = "Exclui (inativa) cartório", description = "Recusado se houver remessa em andamento.")
    @DeleteMapping("/cartorios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirCartorio(@PathVariable Long id) {
        cartorios.inativarCartorio(id);
    }

    // ---------------- Remessas para cartório ----------------

    @Tag(name = "Remessas para cartório")
    @Operation(summary = "Lista remessas", description = "Filtro opcional por situação (ENVIADA, PROTESTADA, BAIXADA).")
    @GetMapping("/remessas")
    public List<RemessaResponse> listarRemessas(@RequestParam(required = false) SituacaoRemessa situacao) {
        List<RemessaCartorio> lista = cartorios.listarRemessas(situacao);
        Map<Long, ContaReceber> titulos = titulos(lista.stream().map(RemessaCartorio::getContaReceberId).toList());
        return lista.stream().map(r -> RemessaResponse.de(r, titulos.get(r.getContaReceberId()))).toList();
    }

    @Tag(name = "Remessas para cartório")
    @Operation(summary = "Envia título vencido a protesto",
            description = "Só título vencido e não recebido, para cartório cadastrado, e no máximo uma remessa em andamento por título.")
    @PostMapping("/remessas")
    @ResponseStatus(HttpStatus.CREATED)
    public RemessaResponse enviar(@Valid @RequestBody RemessaRequest req) {
        return comTitulo(cartorios.enviar(req));
    }

    @Tag(name = "Remessas para cartório")
    @Operation(summary = "Registra a data do protesto")
    @PostMapping("/remessas/{id}/protesto")
    public RemessaResponse protestar(@PathVariable Long id, @Valid @RequestBody DataRequest req) {
        return comTitulo(cartorios.protestar(id, req.data()));
    }

    @Tag(name = "Remessas para cartório")
    @Operation(summary = "Dá baixa na remessa", description = "Motivo PAGO ou DESISTENCIA. O recebimento do título já dá baixa automática.")
    @PostMapping("/remessas/{id}/baixa")
    public RemessaResponse baixar(@PathVariable Long id, @Valid @RequestBody BaixaRequest req) {
        return comTitulo(cartorios.baixar(id, req.data(), req.motivo()));
    }

    // ---------------- auxiliares ----------------

    private Map<Long, ContaReceber> titulos(List<Long> ids) {
        return contas.findAllById(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(ContaReceber::getId, Function.identity()));
    }

    private ChequeResponse comTitulo(ChequePreDatado c) {
        return ChequeResponse.de(c, contas.findById(c.getContaReceberId()).orElse(null));
    }

    private RemessaResponse comTitulo(RemessaCartorio r) {
        return RemessaResponse.de(r, contas.findById(r.getContaReceberId()).orElse(null));
    }
}
