package br.ifgoiano.cpr.contaspagar.service;

import br.ifgoiano.cpr.contaspagar.api.ContaPagarDtos.ContaRequest;
import br.ifgoiano.cpr.contaspagar.api.ContaPagarDtos.QuitacaoRequest;
import br.ifgoiano.cpr.contaspagar.api.ContaPagarDtos.Referencia;
import br.ifgoiano.cpr.contaspagar.domain.*;
import br.ifgoiano.cpr.contaspagar.evento.Outbox;
import br.ifgoiano.cpr.contaspagar.integracao.CategoriaClient;
import br.ifgoiano.cpr.contaspagar.integracao.CategoriaClient.CategoriaDTO;
import br.ifgoiano.cpr.contaspagar.integracao.PessoaClient;
import br.ifgoiano.cpr.contaspagar.integracao.PessoaClient.PessoaDTO;
import br.ifgoiano.cpr.contaspagar.shared.NegocioException;
import br.ifgoiano.cpr.contaspagar.shared.ServicoIndisponivelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Regras de negócio do título, isoladas dos outros serviços (clientes Feign simulados). */
@ExtendWith(MockitoExtension.class)
class ContaPagarServiceTest {

    // Tipos de pessoa aceito e recusado por este serviço.
    static final String TIPO_VALIDO = "FORNECEDOR";
    static final String TIPO_INVALIDO = "CLIENTE";

    static final LocalDate HOJE = LocalDate.of(2026, 11, 20);

    @Mock ContaPagarRepository repository;
    @Mock QuitacaoIdempotenciaRepository idempotencia;
    @Mock PessoaClient pessoas;
    @Mock CategoriaClient categorias;
    @Mock Outbox outbox;

    ContaPagarService service;

    @BeforeEach
    void setUp() {
        Clock relogio = Clock.fixed(HOJE.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant(), ZoneId.of("America/Sao_Paulo"));
        service = new ContaPagarService(repository, idempotencia, pessoas, categorias, outbox, relogio);
    }

    private ContaRequest request(BigDecimal valor, LocalDate emissao, LocalDate vencimento, BigDecimal desconto, LocalDate validade) {
        return new ContaRequest("T-1", new Referencia(1L), new Referencia(10L), TipoTitulo.DUPLICATA, valor,
                emissao, vencimento, desconto, validade, BigDecimal.ZERO, null);
    }

    private ContaPagar contaAberta() {
        ContaPagar c = new ContaPagar();
        c.setId(5L);
        c.setNumeroTitulo("T-5");
        c.setValor(new BigDecimal("1000.00"));
        c.setEmissao(LocalDate.of(2026, 11, 1));
        c.setVencimento(LocalDate.of(2026, 11, 15));
        c.setDesconto(new BigDecimal("50.00"));
        c.setValidadeDesc(LocalDate.of(2026, 11, 10));
        c.setAcrescimo(new BigDecimal("20.00"));
        c.setSituacao(SituacaoConta.VENCIDA);
        c.setVersao(0L);
        return c;
    }

    @Test
    @DisplayName("Lança título com pessoa e categoria válidas, guarda a réplica e registra evento")
    void lancaTitulo() {
        when(pessoas.buscar(1L)).thenReturn(new PessoaDTO(1L, TIPO_VALIDO, "Distribuidora Alfa", "12345678000199"));
        when(categorias.buscar(10L)).thenReturn(new CategoriaDTO(10L, "2.2", "Fornecedores", false));

        ContaPagar c = service.lancar(request(new BigDecimal("300"), HOJE, HOJE.plusDays(30), BigDecimal.ZERO, null));

        assertThat(c.getSituacao()).isEqualTo(SituacaoConta.ABERTA);
        assertThat(c.getPessoaNome()).isEqualTo("Distribuidora Alfa");
        assertThat(c.getPlanoContaCodigo()).isEqualTo("2.2");
        verify(outbox).registrar("REGISTRADO", c);
    }

    @Test
    @DisplayName("Título lançado com vencimento passado já nasce VENCIDA")
    void lancaVencido() {
        when(pessoas.buscar(1L)).thenReturn(new PessoaDTO(1L, TIPO_VALIDO, "Alfa", "12345678000199"));
        when(categorias.buscar(10L)).thenReturn(new CategoriaDTO(10L, "2.2", "Fornecedores", false));

        ContaPagar c = service.lancar(request(new BigDecimal("300"), HOJE.minusDays(40), HOJE.minusDays(10), BigDecimal.ZERO, null));

        assertThat(c.getSituacao()).isEqualTo(SituacaoConta.VENCIDA);
    }

    @Test
    @DisplayName("RN04: recusa pessoa do tipo errado para esta natureza de título")
    void recusaTipoDePessoa() {
        when(pessoas.buscar(1L)).thenReturn(new PessoaDTO(1L, TIPO_INVALIDO, "Pessoa X", "12345678901"));

        assertThatThrownBy(() -> service.lancar(request(new BigDecimal("300"), HOJE, HOJE.plusDays(5), BigDecimal.ZERO, null)))
                .isInstanceOf(NegocioException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("RN01: recusa categoria consolidada")
    void recusaCategoriaConsolidada() {
        when(pessoas.buscar(1L)).thenReturn(new PessoaDTO(1L, TIPO_VALIDO, "Alfa", "12345678000199"));
        when(categorias.buscar(10L)).thenReturn(new CategoriaDTO(10L, "2", "Despesas", true));

        assertThatThrownBy(() -> service.lancar(request(new BigDecimal("300"), HOJE, HOJE.plusDays(5), BigDecimal.ZERO, null)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("consolidada");
    }

    @Test
    @DisplayName("Serviço de pessoas fora do ar: o título NÃO é gravado e o erro é 'indisponível'")
    void pessoasIndisponivel() {
        when(pessoas.buscar(1L)).thenThrow(new ServicoIndisponivelException("O serviço de Pessoas está indisponível"));

        assertThatThrownBy(() -> service.lancar(request(new BigDecimal("300"), HOJE, HOJE.plusDays(5), BigDecimal.ZERO, null)))
                .isInstanceOf(ServicoIndisponivelException.class);
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(outbox);
    }

    @Test
    @DisplayName("Entrada inválida: vencimento antes da emissão")
    void vencimentoAntesDaEmissao() {
        assertThatThrownBy(() -> service.lancar(request(new BigDecimal("300"), HOJE, HOJE.minusDays(1), BigDecimal.ZERO, null)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("vencimento");
    }

    @Test
    @DisplayName("Entrada inválida: desconto maior ou igual ao valor")
    void descontoMaiorQueValor() {
        assertThatThrownBy(() -> service.lancar(request(new BigDecimal("100"), HOJE, HOJE.plusDays(1), new BigDecimal("100"), null)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("desconto");
    }

    @Test
    @DisplayName("RN05: quitação dentro da validade aplica o desconto do título (1000 - 50 = 950)")
    void quitaDentroDaValidade() {
        ContaPagar c = contaAberta();
        when(repository.findById(5L)).thenReturn(Optional.of(c));

        var r = service.quitar(5L, new QuitacaoRequest(LocalDate.of(2026, 11, 9), null, null), null);

        assertThat(r.conta().getSituacao()).isEqualTo(SituacaoConta.PAGA);
        assertThat(r.conta().getValorPago()).isEqualByComparingTo("950.00");
        assertThat(r.conta().getAcrescimo()).isEqualByComparingTo("0");
        verify(outbox).registrar("QUITADO", c);
    }

    @Test
    @DisplayName("RN05: quitação após o vencimento perde o desconto e paga o acréscimo (1000 + 20 = 1020)")
    void quitaAtrasado() {
        ContaPagar c = contaAberta();
        when(repository.findById(5L)).thenReturn(Optional.of(c));

        var r = service.quitar(5L, new QuitacaoRequest(LocalDate.of(2026, 11, 18), null, null), null);

        assertThat(r.conta().getValorPago()).isEqualByComparingTo("1020.00");
        assertThat(r.conta().getDesconto()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("RN05: desconto informado depois da validade é recusado")
    void descontoForaDaValidade() {
        when(repository.findById(5L)).thenReturn(Optional.of(contaAberta()));

        assertThatThrownBy(() -> service.quitar(5L, new QuitacaoRequest(LocalDate.of(2026, 11, 12), new BigDecimal("50"), null), null))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("desconto só vale até");
    }

    @Test
    @DisplayName("Quitação duplicada sem chave: o segundo pedido é recusado (título já quitado)")
    void quitacaoDuplicada() {
        ContaPagar c = contaAberta();
        when(repository.findById(5L)).thenReturn(Optional.of(c));
        service.quitar(5L, new QuitacaoRequest(LocalDate.of(2026, 11, 9), null, null), null);

        assertThatThrownBy(() -> service.quitar(5L, new QuitacaoRequest(LocalDate.of(2026, 11, 9), null, null), null))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("já quitado");
        verify(outbox, times(1)).registrar(anyString(), any());
    }

    @Test
    @DisplayName("Mesma Idempotency-Key: devolve o resultado anterior sem pagar de novo")
    void quitacaoIdempotente() {
        ContaPagar c = contaAberta();
        when(repository.findById(5L)).thenReturn(Optional.of(c));
        when(idempotencia.findById("chave-1")).thenReturn(Optional.empty())
                .thenReturn(Optional.of(new QuitacaoIdempotencia("chave-1", 5L)));

        var primeira = service.quitar(5L, new QuitacaoRequest(LocalDate.of(2026, 11, 9), null, null), "chave-1");
        var segunda = service.quitar(5L, new QuitacaoRequest(LocalDate.of(2026, 11, 9), null, null), "chave-1");

        assertThat(primeira.repetida()).isFalse();
        assertThat(segunda.repetida()).isTrue();
        assertThat(segunda.conta().getValorPago()).isEqualByComparingTo("950.00");
        verify(outbox, times(1)).registrar("QUITADO", c);
        verify(idempotencia, times(1)).save(any());
    }

    @Test
    @DisplayName("Idempotency-Key reaproveitada em outro título é recusada")
    void chaveDeOutroTitulo() {
        when(idempotencia.findById("chave-1")).thenReturn(Optional.of(new QuitacaoIdempotencia("chave-1", 99L)));

        assertThatThrownBy(() -> service.quitar(5L, new QuitacaoRequest(HOJE, null, null), "chave-1"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("outro título");
    }

    @Test
    @DisplayName("Quitação com data futura é recusada")
    void dataFutura() {
        when(repository.findById(5L)).thenReturn(Optional.of(contaAberta()));

        assertThatThrownBy(() -> service.quitar(5L, new QuitacaoRequest(HOJE.plusDays(1), null, null), null))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("futura");
    }

    @Test
    @DisplayName("RN07: cancelar mantém o título na base com situação CANCELADA")
    void cancela() {
        ContaPagar c = contaAberta();
        when(repository.findById(5L)).thenReturn(Optional.of(c));

        service.cancelar(5L);

        assertThat(c.getSituacao()).isEqualTo(SituacaoConta.CANCELADA);
        verify(repository, never()).delete(any(ContaPagar.class));
        verify(outbox).registrar("CANCELADO", c);
    }

    @Test
    @DisplayName("Título quitado não pode ser cancelado nem alterado")
    void quitadoNaoCancela() {
        ContaPagar c = contaAberta();
        c.setSituacao(SituacaoConta.PAGA);
        when(repository.findById(5L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.cancelar(5L)).isInstanceOf(NegocioException.class);
    }
}
