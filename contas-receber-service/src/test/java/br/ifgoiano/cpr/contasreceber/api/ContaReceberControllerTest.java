package br.ifgoiano.cpr.contasreceber.api;

import br.ifgoiano.cpr.contasreceber.domain.ContaReceber;
import br.ifgoiano.cpr.contasreceber.domain.SituacaoConta;
import br.ifgoiano.cpr.contasreceber.domain.TipoTitulo;
import br.ifgoiano.cpr.contasreceber.service.ContaReceberService;
import br.ifgoiano.cpr.contasreceber.shared.NegocioException;
import br.ifgoiano.cpr.contasreceber.shared.RecursoNaoEncontradoException;
import br.ifgoiano.cpr.contasreceber.shared.ServicoIndisponivelException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Contrato HTTP do controller: status e formato de erro (o serviço é simulado). */
@WebMvcTest(ContaReceberController.class)
class ContaReceberControllerTest {

    @Autowired MockMvc mvc;
    @MockBean ContaReceberService service;

    static final String CORPO_VALIDO = """
            {"numeroTitulo":"T-1","pessoa":{"id":1,"nome":"ignorado pelo servidor"},"planoConta":{"id":10},
             "tipoTitulo":"DUPLICATA","valor":150.00,"emissao":"2026-11-01","vencimento":"2026-12-01"}
            """;

    private static ContaReceber conta() {
        ContaReceber c = new ContaReceber();
        c.setId(1L);
        c.setNumeroTitulo("T-1");
        c.setPessoaId(1L);
        c.setPessoaNome("Alfa");
        c.setPessoaDocumento("12345678000199");
        c.setPlanoContaId(10L);
        c.setPlanoContaCodigo("2.2");
        c.setPlanoContaDescricao("Fornecedores");
        c.setTipoTitulo(TipoTitulo.DUPLICATA);
        c.setValor(new BigDecimal("150.00"));
        c.setEmissao(LocalDate.of(2026, 11, 1));
        c.setVencimento(LocalDate.of(2026, 12, 1));
        return c;
    }

    @Test
    @DisplayName("POST válido → 201 com pessoa e categoria resumidas")
    void lancaComSucesso() throws Exception {
        when(service.lancar(any())).thenReturn(conta());

        mvc.perform(post("/api/contas-receber").contentType(MediaType.APPLICATION_JSON).content(CORPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pessoa.nome").value("Alfa"))
                .andExpect(jsonPath("$.planoConta.codigo").value("2.2"))
                .andExpect(jsonPath("$.situacao").value(SituacaoConta.ABERTA.name()));
    }

    @Test
    @DisplayName("POST com valor negativo e sem pessoa → 400 listando os campos")
    void validacao() throws Exception {
        mvc.perform(post("/api/contas-receber").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroTitulo\":\"\",\"valor\":-5,\"tipoTitulo\":\"DUPLICATA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("FALHA_VALIDACAO"))
                .andExpect(jsonPath("$.campos[?(@.nome=='valor')]").exists())
                .andExpect(jsonPath("$.campos[?(@.nome=='pessoa')]").exists());
    }

    @Test
    @DisplayName("JSON mal formado ou tipo de título inexistente → 400")
    void jsonInvalido() throws Exception {
        mvc.perform(post("/api/contas-receber").contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO.replace("DUPLICATA", "CHEQUE_VOADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("REQUISICAO_INVALIDA"));
    }

    @Test
    @DisplayName("GET de título inexistente → 404")
    void naoEncontrado() throws Exception {
        when(service.buscar(99L)).thenThrow(new RecursoNaoEncontradoException("Conta 99 não encontrada."));

        mvc.perform(get("/api/contas-receber/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("NAO_ENCONTRADO"));
    }

    @Test
    @DisplayName("Regra de negócio violada → 422")
    void regraDeNegocio() throws Exception {
        when(service.lancar(any())).thenThrow(new NegocioException("A conta 2 é consolidada"));

        mvc.perform(post("/api/contas-receber").contentType(MediaType.APPLICATION_JSON).content(CORPO_VALIDO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("REGRA_NEGOCIO"));
    }

    @Test
    @DisplayName("Serviço de pessoas indisponível → 503 com mensagem clara")
    void indisponivel() throws Exception {
        when(service.lancar(any())).thenThrow(new ServicoIndisponivelException("O serviço de Pessoas está indisponível"));

        mvc.perform(post("/api/contas-receber").contentType(MediaType.APPLICATION_JSON).content(CORPO_VALIDO))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.erro").value("SERVICO_INDISPONIVEL"));
    }

    @Test
    @DisplayName("Quitação repetida com a mesma Idempotency-Key → 200 e header Idempotent-Replay: true")
    void quitacaoRepetida() throws Exception {
        ContaReceber c = conta();
        c.setSituacao(SituacaoConta.RECEBIDA);
        when(service.quitar(eq(1L), any(), eq("abc"))).thenReturn(new ContaReceberService.Resultado(c, true));

        mvc.perform(post("/api/contas-receber/1/recebimento").header("Idempotency-Key", "abc")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"data\":\"2026-11-20\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"));
    }

    @Test
    @DisplayName("DELETE cancela o título → 204")
    void cancela() throws Exception {
        when(service.cancelar(1L)).thenReturn(conta());

        mvc.perform(delete("/api/contas-receber/1")).andExpect(status().isNoContent());
    }
}
