package br.ifgoiano.cpr.categorias;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Plano de contas hierárquico (RN01) contra PostgreSQL real, com a carga inicial do Flyway. */
@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.port=1", // isolado do RabbitMQ real: publicação falha e é tratada
        "spring.cloud.discovery.enabled=false",
        "management.tracing.enabled=false"
})
@AutoConfigureMockMvc
class PlanoContaIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("Carga inicial do Flyway disponível, com a conta superior resumida")
    void cargaInicial() throws Exception {
        mvc.perform(get("/api/plano-contas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", hasItem("2.1.01")))
                .andExpect(jsonPath("$[?(@.codigo=='2.1.01')].superior.codigo", hasItem("2.1")));
    }

    @Test
    @DisplayName("RN01: subconta só pode ficar sob conta consolidada")
    void superiorPrecisaSerConsolidada() throws Exception {
        // 1.1 (Vendas) não é consolidada → não pode ser superior.
        String superiorNaoConsolidada = "{\"codigo\":\"1.1.01\",\"descricao\":\"Vendas à vista\",\"superior\":{\"id\":3},\"consolidada\":false}";
        mvc.perform(post("/api/plano-contas").contentType(MediaType.APPLICATION_JSON).content(superiorNaoConsolidada))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("consolidada")));

        // 2.1 (Despesas fixas) é consolidada → aceita.
        mvc.perform(post("/api/plano-contas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"2.1.03\",\"descricao\":\"Internet\",\"superior\":{\"id\":5},\"consolidada\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.superior.codigo").value("2.1"));
    }

    @Test
    @DisplayName("Conta com subcontas não pode ser excluída nem deixar de ser consolidada")
    void contaComFilhos() throws Exception {
        mvc.perform(delete("/api/plano-contas/2")).andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/plano-contas/2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"2\",\"descricao\":\"Despesas\",\"consolidada\":false}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Código com formato inválido → 400; código repetido → 422")
    void validacoes() throws Exception {
        mvc.perform(post("/api/plano-contas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"abc\",\"descricao\":\"X\",\"consolidada\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].nome", hasItem("codigo")));
        mvc.perform(post("/api/plano-contas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"1\",\"descricao\":\"Duplicada\",\"consolidada\":true}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
