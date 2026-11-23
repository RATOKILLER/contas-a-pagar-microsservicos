package br.ifgoiano.cpr.pessoas;

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
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** API de pessoas de ponta a ponta contra PostgreSQL real (Testcontainers). */
@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.port=1", // isolado do RabbitMQ real: publicação falha e é tratada
        "spring.cloud.discovery.enabled=false",
        "management.tracing.enabled=false"
})
@AutoConfigureMockMvc
class PessoaIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;

    private String cadastrar(String json) throws Exception {
        return mvc.perform(post("/api/pessoas").contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Cadastro normaliza documento, UF e CEP; documento repetido é recusado")
    void cadastroEDuplicidade() throws Exception {
        mvc.perform(post("/api/pessoas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"FORNECEDOR\",\"nome\":\"Papelaria Gama\",\"documento\":\"11.222.333/0001-44\",\"uf\":\"go\",\"cep\":\"75900-000\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documento").value("11222333000144"))
                .andExpect(jsonPath("$.uf").value("GO"))
                .andExpect(jsonPath("$.cep").value("75900000"));

        mvc.perform(post("/api/pessoas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"CLIENTE\",\"nome\":\"Outra empresa\",\"documento\":\"11222333000144\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("REGRA_NEGOCIO"));
    }

    @Test
    @DisplayName("Entradas inválidas → 400 com os campos: CPF curto, e-mail e UF inválidos, tipo ausente")
    void entradasInvalidas() throws Exception {
        mvc.perform(post("/api/pessoas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Jo\",\"documento\":\"123\",\"email\":\"sem-arroba\",\"uf\":\"GOI\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].nome", hasItem("documento")))
                .andExpect(jsonPath("$.campos[*].nome", hasItem("email")))
                .andExpect(jsonPath("$.campos[*].nome", hasItem("uf")))
                .andExpect(jsonPath("$.campos[*].nome", hasItem("tipo")))
                .andExpect(jsonPath("$.campos[*].nome", hasItem("nome")));
    }

    @Test
    @DisplayName("Filtro por tipo inclui AMBOS; exclusão é lógica e a pessoa some da lista e do GET (404)")
    void filtroEExclusaoLogica() throws Exception {
        cadastrar("{\"tipo\":\"AMBOS\",\"nome\":\"Comercial Delta\",\"documento\":\"22333444000155\"}");
        String fornecedor = cadastrar("{\"tipo\":\"FORNECEDOR\",\"nome\":\"Fornecedor Épsilon\",\"documento\":\"33444555000166\"}");
        String id = fornecedor.replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(get("/api/pessoas").param("tipo", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", hasItem("Comercial Delta")))
                .andExpect(jsonPath("$[*].nome", not(hasItem("Fornecedor Épsilon"))));

        mvc.perform(delete("/api/pessoas/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/pessoas/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/pessoas"))
                .andExpect(jsonPath("$[*].nome", not(hasItem("Fornecedor Épsilon"))));
        mvc.perform(delete("/api/pessoas/" + id)).andExpect(status().isNotFound());
    }
}
