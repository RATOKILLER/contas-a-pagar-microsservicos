package br.ifgoiano.cpr.contasreceber.garantia;

import br.ifgoiano.cpr.contasreceber.integracao.CategoriaClient;
import br.ifgoiano.cpr.contasreceber.integracao.CategoriaClient.CategoriaDTO;
import br.ifgoiano.cpr.contasreceber.integracao.PessoaClient;
import br.ifgoiano.cpr.contasreceber.integracao.PessoaClient.PessoaDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** RN08 e RN09 de ponta a ponta (HTTP → PostgreSQL real com a migration V2). */
@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.port=1",
        "spring.cloud.discovery.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "management.tracing.enabled=false",
        "cpr.outbox.intervalo-ms=3600000"
})
@AutoConfigureMockMvc
class GarantiaIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean PessoaClient pessoas;
    @MockBean CategoriaClient categorias;

    final LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    private ResultActions post(String url, String corpo) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private long id(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long lancarTitulo(String numero, LocalDate emissao, LocalDate vencimento, String valor) throws Exception {
        when(pessoas.buscar(2L)).thenReturn(new PessoaDTO(2L, "CLIENTE", "Mercado Beta", "98765432100"));
        when(categorias.buscar(3L)).thenReturn(new CategoriaDTO(3L, "1.1", "Vendas", false));
        return id(post("/api/contas-receber", """
                {"numeroTitulo":"%s","pessoa":{"id":2},"planoConta":{"id":3},"tipoTitulo":"DUPLICATA",
                 "valor":%s,"emissao":"%s","vencimento":"%s"}""".formatted(numero, valor, emissao, vencimento))
                .andExpect(status().isCreated()));
    }

    @Test
    @DisplayName("RN09: remessa de título vencido; título em cartório não cancela; recebimento dá baixa automática")
    void fluxoCartorio() throws Exception {
        long titulo = lancarTitulo("INT-CART-1", hoje.minusDays(40), hoje.minusDays(10), "800.00");
        long emDia = lancarTitulo("INT-CART-2", hoje, hoje.plusDays(10), "100.00");

        // Cartório precisa existir antes; o nome é único.
        long cartorio = id(post("/api/contas-receber/cartorios", "{\"nome\":\"1º Tabelionato de Protesto\",\"cidade\":\"Morrinhos\"}")
                .andExpect(status().isCreated()));
        post("/api/contas-receber/cartorios", "{\"nome\":\"1º TABELIONATO DE PROTESTO\"}").andExpect(status().isUnprocessableEntity());

        // Título em dia não vai a protesto; cartório inexistente é recusado.
        post("/api/contas-receber/remessas", "{\"contaReceberId\":%d,\"cartorioId\":%d,\"dataRemessa\":\"%s\"}".formatted(emDia, cartorio, hoje))
                .andExpect(status().isUnprocessableEntity());
        post("/api/contas-receber/remessas", "{\"contaReceberId\":%d,\"cartorioId\":999,\"dataRemessa\":\"%s\"}".formatted(titulo, hoje))
                .andExpect(status().isUnprocessableEntity());

        long remessa = id(post("/api/contas-receber/remessas", "{\"contaReceberId\":%d,\"cartorioId\":%d,\"dataRemessa\":\"%s\"}"
                .formatted(titulo, cartorio, hoje.minusDays(3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.situacao").value("ENVIADA"))
                .andExpect(jsonPath("$.titulo.numeroTitulo").value("INT-CART-1")));

        // Segunda remessa do mesmo título → 422 (índice único parcial também protege no banco).
        post("/api/contas-receber/remessas", "{\"contaReceberId\":%d,\"cartorioId\":%d,\"dataRemessa\":\"%s\"}".formatted(titulo, cartorio, hoje))
                .andExpect(status().isUnprocessableEntity());

        post("/api/contas-receber/remessas/" + remessa + "/protesto", "{\"data\":\"" + hoje.minusDays(1) + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("PROTESTADA"));

        // Título em cartório não pode ser cancelado; cartório com remessa ativa não pode ser excluído.
        mvc.perform(delete("/api/contas-receber/" + titulo)).andExpect(status().isUnprocessableEntity());
        mvc.perform(delete("/api/contas-receber/cartorios/" + cartorio)).andExpect(status().isUnprocessableEntity());

        // Cliente paga: o recebimento dá baixa automática na remessa.
        post("/api/contas-receber/" + titulo + "/recebimento", "{\"data\":\"" + hoje + "\"}").andExpect(status().isOk());
        mvc.perform(get("/api/contas-receber/remessas").param("situacao", "BAIXADA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(remessa))
                .andExpect(jsonPath("$[0].motivoBaixa").value("PAGO"))
                .andExpect(jsonPath("$[0].titulo.situacao").value("RECEBIDA"));
    }

    @Test
    @DisplayName("RN08: cheque pré-datado — limite pelo valor do título, depósito só no 'bom para', devolução")
    void fluxoCheque() throws Exception {
        long titulo = lancarTitulo("INT-CHQ-1", hoje.minusDays(5), hoje.plusDays(25), "1000.00");
        String cheque = """
                {"contaReceberId":%d,"numeroCheque":"%s","banco":"001","agencia":"1234","contaCorrente":"5678-9",
                 "valor":%s,"emissao":"%s","dataDeposito":"%s"}""";

        long hojeCheque = id(post("/api/contas-receber/cheques", cheque.formatted(titulo, "100", "600.00", hoje.minusDays(5), hoje))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emitente").value("Mercado Beta"))
                .andExpect(jsonPath("$.situacao").value("A_DEPOSITAR")));
        long futuro = id(post("/api/contas-receber/cheques", cheque.formatted(titulo, "101", "400.00", hoje.minusDays(5), hoje.plusDays(30)))
                .andExpect(status().isCreated()));

        // Passaria de 1000 → 422; mesmo cheque de novo → 422; banco inválido → 400.
        post("/api/contas-receber/cheques", cheque.formatted(titulo, "102", "0.01", hoje, hoje)).andExpect(status().isUnprocessableEntity());
        post("/api/contas-receber/cheques", cheque.formatted(titulo, "100", "1.00", hoje, hoje)).andExpect(status().isUnprocessableEntity());
        post("/api/contas-receber/cheques", cheque.formatted(titulo, "103", "1.00", hoje, hoje).replace("\"001\"", "\"1\""))
                .andExpect(status().isBadRequest());

        // Pré-datado para daqui a 30 dias: não deposita hoje.
        post("/api/contas-receber/cheques/" + futuro + "/deposito", "{\"data\":\"" + hoje + "\"}").andExpect(status().isUnprocessableEntity());

        post("/api/contas-receber/cheques/" + hojeCheque + "/deposito", "{\"data\":\"" + hoje + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("DEPOSITADO"));
        post("/api/contas-receber/cheques/" + hojeCheque + "/devolucao", "{}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("DEVOLVIDO"));

        // O devolvido deixou de garantir: agora cabe outro cheque de 600.
        post("/api/contas-receber/cheques", cheque.formatted(titulo, "104", "600.00", hoje, hoje)).andExpect(status().isCreated());

        mvc.perform(delete("/api/contas-receber/cheques/" + futuro)).andExpect(status().isNoContent());
        mvc.perform(get("/api/contas-receber/cheques").param("situacao", "CANCELADO"))
                .andExpect(jsonPath("$[0].id").value(futuro));
    }
}
