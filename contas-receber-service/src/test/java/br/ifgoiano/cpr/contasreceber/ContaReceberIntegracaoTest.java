package br.ifgoiano.cpr.contasreceber;

import br.ifgoiano.cpr.contasreceber.domain.SituacaoConta;
import br.ifgoiano.cpr.contasreceber.evento.OutboxRepository;
import br.ifgoiano.cpr.contasreceber.integracao.CategoriaClient;
import br.ifgoiano.cpr.contasreceber.integracao.CategoriaClient.CategoriaDTO;
import br.ifgoiano.cpr.contasreceber.integracao.PessoaClient;
import br.ifgoiano.cpr.contasreceber.integracao.PessoaClient.PessoaDTO;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integração com PostgreSQL real (Testcontainers): migrations Flyway, mapeamento JPA, constraints,
 * outbox e idempotência, do HTTP até o banco. Pessoas e categorias são simuladas (outros serviços).
 */
@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.port=1", // isolado do RabbitMQ real: publicação falha e é tratada
        "spring.cloud.discovery.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "management.tracing.enabled=false",
        "cpr.outbox.intervalo-ms=3600000"
})
@AutoConfigureMockMvc
class ContaReceberIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired OutboxRepository outbox;
    @Autowired ObjectMapper json;

    @MockBean PessoaClient pessoas;
    @MockBean CategoriaClient categorias;

    @Test
    @DisplayName("Lançar → quitar → repetir quitação: persiste, gera 2 eventos e não recebe duas vezes")
    void fluxoCompleto() throws Exception {
        when(pessoas.buscar(1L)).thenReturn(new PessoaDTO(1L, "AMBOS", "Distribuidora Alfa", "12345678000199"));
        when(categorias.buscar(10L)).thenReturn(new CategoriaDTO(10L, "2.2", "Fornecedores", false));
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        long eventosAntes = outbox.countByPublicadoEmIsNull();

        String corpo = """
                {"numeroTitulo":"INT-1","pessoa":{"id":1},"planoConta":{"id":10},"tipoTitulo":"PIX",
                 "valor":500.00,"emissao":"%s","vencimento":"%s","desconto":25.00,"validadeDesc":"%s"}
                """.formatted(hoje.minusDays(5), hoje.plusDays(10), hoje.plusDays(2));
        String resposta = mvc.perform(post("/api/contas-receber").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode criado = json.readTree(resposta);
        long id = criado.get("id").asLong();

        // Mesmo número de título de novo → recusado (único no banco).
        mvc.perform(post("/api/contas-receber").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnprocessableEntity());

        String quitacao = "{\"data\":\"" + hoje + "\"}";
        mvc.perform(post("/api/contas-receber/" + id + "/recebimento").header("Idempotency-Key", "int-chave-1")
                        .contentType(MediaType.APPLICATION_JSON).content(quitacao))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "false"))
                .andExpect(jsonPath("$.situacao").value(SituacaoConta.RECEBIDA.name()))
                .andExpect(jsonPath("$.valorRecebido").value(475.0));

        // Rede reenviou a mesma requisição: mesmo resultado, sem novo recebimento nem novo evento.
        mvc.perform(post("/api/contas-receber/" + id + "/recebimento").header("Idempotency-Key", "int-chave-1")
                        .contentType(MediaType.APPLICATION_JSON).content(quitacao))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.valorRecebido").value(475.0));

        // Nova tentativa com outra chave: título já quitado → 422.
        mvc.perform(post("/api/contas-receber/" + id + "/recebimento").header("Idempotency-Key", "int-chave-2")
                        .contentType(MediaType.APPLICATION_JSON).content(quitacao))
                .andExpect(status().isUnprocessableEntity());

        assertThat(outbox.countByPublicadoEmIsNull() - eventosAntes).isEqualTo(2); // REGISTRADO + QUITADO

        // Consulta por período e situação.
        mvc.perform(get("/api/contas-receber").param("situacao", SituacaoConta.RECEBIDA.name())
                        .param("inicio", hoje.toString()).param("fim", hoje.plusDays(30).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numeroTitulo=='INT-1')]").exists());
    }
}
