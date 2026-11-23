package br.ifgoiano.cpr.relatorios;

import br.ifgoiano.cpr.relatorios.api.RelatorioDtos.FluxoCaixa;
import br.ifgoiano.cpr.relatorios.api.RelatorioDtos.MesFluxo;
import br.ifgoiano.cpr.relatorios.api.RelatorioService;
import br.ifgoiano.cpr.relatorios.evento.ProjecaoTitulos;
import br.ifgoiano.cpr.relatorios.evento.TituloEvento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Projeção de eventos e relatórios contra PostgreSQL real. */
@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.port=1", // isolado do RabbitMQ real: publicação falha e é tratada
        "spring.cloud.discovery.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "management.tracing.enabled=false"
})
class RelatoriosIntegracaoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired ProjecaoTitulos projecao;
    @Autowired RelatorioService relatorios;

    private static TituloEvento evento(String tipo, String natureza, long id, String valor, LocalDate venc,
                                       String situacao, LocalDate quitacao, String valorQuitado, long versao) {
        return new TituloEvento(UUID.randomUUID(), tipo, natureza, id, natureza.charAt(0) + "-" + id, 1L, "Pessoa",
                9L, "2.2", "Fornecedores", new BigDecimal(valor), venc.minusDays(10), venc, situacao, quitacao,
                valorQuitado == null ? null : new BigDecimal(valorQuitado), versao, OffsetDateTime.now());
    }

    private static MesFluxo mes(FluxoCaixa f, String mes) {
        return f.meses().stream().filter(m -> m.mes().equals(mes)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Evento repetido (mesmo id) é aplicado uma vez só")
    void eventoDuplicado() {
        TituloEvento e = evento("REGISTRADO", "PAGAR", 100, "300.00", LocalDate.of(2030, 3, 10), "ABERTA", null, null, 0);
        assertThat(projecao.aplicar(e.eventoId(), e)).isTrue();
        assertThat(projecao.aplicar(e.eventoId(), e)).isFalse();

        FluxoCaixa f = relatorios.fluxoCaixa(LocalDate.of(2030, 3, 1), LocalDate.of(2030, 3, 31));
        assertThat(mes(f, "2030-03").previstoPagar()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("Evento antigo que chega depois do novo é descartado pela versão")
    void eventoForaDeOrdem() {
        LocalDate venc = LocalDate.of(2030, 4, 10);
        TituloEvento quitado = evento("QUITADO", "RECEBER", 200, "1000.00", venc, "RECEBIDA", venc, "950.00", 1);
        TituloEvento registradoAtrasado = evento("REGISTRADO", "RECEBER", 200, "1000.00", venc, "ABERTA", null, null, 0);

        assertThat(projecao.aplicar(quitado.eventoId(), quitado)).isTrue();
        assertThat(projecao.aplicar(registradoAtrasado.eventoId(), registradoAtrasado)).isFalse();

        FluxoCaixa f = relatorios.fluxoCaixa(LocalDate.of(2030, 4, 1), LocalDate.of(2030, 4, 30));
        assertThat(mes(f, "2030-04").recebido()).isEqualByComparingTo("950.00");
    }

    @Test
    @DisplayName("Fluxo de caixa: previsto por vencimento, realizado por quitação; cancelado fica de fora")
    void fluxoDeCaixa() {
        projecao.aplicar(null, evento("REGISTRADO", "RECEBER", 301, "500.00", LocalDate.of(2031, 1, 15), "ABERTA", null, null, 0));
        projecao.aplicar(null, evento("QUITADO", "PAGAR", 302, "200.00", LocalDate.of(2031, 1, 20), "PAGA", LocalDate.of(2031, 2, 2), "210.00", 1));
        projecao.aplicar(null, evento("CANCELADO", "PAGAR", 303, "999.00", LocalDate.of(2031, 1, 25), "CANCELADA", null, null, 1));

        FluxoCaixa f = relatorios.fluxoCaixa(LocalDate.of(2031, 1, 1), LocalDate.of(2031, 2, 28));
        MesFluxo jan = mes(f, "2031-01");
        MesFluxo fev = mes(f, "2031-02");
        assertThat(jan.previstoReceber()).isEqualByComparingTo("500.00");
        assertThat(jan.previstoPagar()).isEqualByComparingTo("200.00");   // o cancelado (999) não entra
        assertThat(jan.saldoPrevisto()).isEqualByComparingTo("300.00");
        assertThat(fev.pago()).isEqualByComparingTo("210.00");            // realizado no mês da quitação
        assertThat(f.totais().saldoRealizado()).isEqualByComparingTo("-210.00");
    }

    @Test
    @DisplayName("Período invertido é recusado")
    void periodoInvalido() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> relatorios.fluxoCaixa(LocalDate.of(2031, 2, 1), LocalDate.of(2031, 1, 1)))
                .hasMessageContaining("anterior");
    }
}
