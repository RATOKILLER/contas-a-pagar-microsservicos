package br.ifgoiano.cpr.contasreceber.garantia;

import br.ifgoiano.cpr.contasreceber.domain.ContaReceber;
import br.ifgoiano.cpr.contasreceber.domain.ContaReceberRepository;
import br.ifgoiano.cpr.contasreceber.domain.SituacaoConta;
import br.ifgoiano.cpr.contasreceber.garantia.GarantiaDtos.ChequeRequest;
import br.ifgoiano.cpr.contasreceber.garantia.GarantiaDtos.RemessaRequest;
import br.ifgoiano.cpr.contasreceber.shared.NegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** RN08 (cheque pré-datado) e RN09 (remessa para cartório). Hoje fixo em 20/11/2026. */
@ExtendWith(MockitoExtension.class)
class GarantiaServiceTest {

    static final LocalDate HOJE = LocalDate.of(2026, 11, 20);
    static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    final Clock relogio = Clock.fixed(HOJE.atStartOfDay(SP).toInstant(), SP);

    @Mock ContaReceberRepository contas;

    static ContaReceber titulo(SituacaoConta situacao, LocalDate vencimento) {
        ContaReceber c = new ContaReceber();
        c.setId(7L);
        c.setNumeroTitulo("CR-7");
        c.setPessoaNome("Mercado Beta");
        c.setValor(new BigDecimal("1000.00"));
        c.setVencimento(vencimento);
        c.setSituacao(situacao);
        return c;
    }

    @Nested
    @DisplayName("RN08 — Cheque pré-datado")
    class Cheques {

        @Mock ChequeRepository cheques;
        ChequeService service;

        @BeforeEach
        void setUp() {
            service = new ChequeService(cheques, contas, relogio);
        }

        ChequeRequest req(String valor, LocalDate emissao, LocalDate bomPara) {
            return new ChequeRequest(7L, "000123", "001", "1234", "56789-0", "", new BigDecimal(valor), emissao, bomPara, null);
        }

        @Test
        @DisplayName("Registra cheque para título em aberto; emitente padrão = cliente do título")
        void registra() {
            when(contas.findById(7L)).thenReturn(Optional.of(titulo(SituacaoConta.ABERTA, HOJE.plusDays(30))));
            when(cheques.somaVigentes(7L)).thenReturn(BigDecimal.ZERO);
            when(cheques.save(any())).thenAnswer(i -> i.getArgument(0));

            ChequePreDatado c = service.registrar(req("400.00", HOJE, HOJE.plusDays(15)));

            assertThat(c.getSituacao()).isEqualTo(SituacaoCheque.A_DEPOSITAR);
            assertThat(c.getEmitente()).isEqualTo("Mercado Beta");
        }

        @Test
        @DisplayName("Recusa cheque para título já recebido")
        void tituloRecebido() {
            when(contas.findById(7L)).thenReturn(Optional.of(titulo(SituacaoConta.RECEBIDA, HOJE)));

            assertThatThrownBy(() -> service.registrar(req("100", HOJE, HOJE.plusDays(1))))
                    .isInstanceOf(NegocioException.class).hasMessageContaining("em aberto");
        }

        @Test
        @DisplayName("Recusa cheques que somem mais que o valor do título (700 + 400 > 1000)")
        void ultrapassaValor() {
            when(contas.findById(7L)).thenReturn(Optional.of(titulo(SituacaoConta.ABERTA, HOJE.plusDays(30))));
            when(cheques.somaVigentes(7L)).thenReturn(new BigDecimal("700.00"));

            assertThatThrownBy(() -> service.registrar(req("400.00", HOJE, HOJE.plusDays(15))))
                    .isInstanceOf(NegocioException.class).hasMessageContaining("acima do valor do título");
        }

        @Test
        @DisplayName("Recusa o mesmo cheque duas vezes (banco + agência + conta + número)")
        void chequeRepetido() {
            when(contas.findById(7L)).thenReturn(Optional.of(titulo(SituacaoConta.ABERTA, HOJE.plusDays(30))));
            when(cheques.existsByBancoAndAgenciaAndContaCorrenteAndNumeroCheque("001", "1234", "56789-0", "000123")).thenReturn(true);

            assertThatThrownBy(() -> service.registrar(req("100", HOJE, HOJE.plusDays(1))))
                    .isInstanceOf(NegocioException.class).hasMessageContaining("já foi registrado");
        }

        @Test
        @DisplayName("Data 'bom para' anterior à emissão é recusada")
        void bomParaAntesDaEmissao() {
            when(contas.findById(7L)).thenReturn(Optional.of(titulo(SituacaoConta.ABERTA, HOJE.plusDays(30))));

            assertThatThrownBy(() -> service.registrar(req("100", HOJE, HOJE.minusDays(1))))
                    .isInstanceOf(NegocioException.class).hasMessageContaining("anterior à emissão");
        }

        @Test
        @DisplayName("Depósito antes do 'bom para' é recusado; na data, é aceito")
        void depositoPreDatado() {
            ChequePreDatado c = new ChequePreDatado();
            c.setDataDeposito(HOJE);
            when(cheques.findById(1L)).thenReturn(Optional.of(c));
            when(cheques.save(any())).thenAnswer(i -> i.getArgument(0));

            assertThatThrownBy(() -> service.depositar(1L, HOJE.minusDays(1)))
                    .isInstanceOf(NegocioException.class).hasMessageContaining("a partir de 20/11/2026");

            service.depositar(1L, HOJE);
            assertThat(c.getSituacao()).isEqualTo(SituacaoCheque.DEPOSITADO);
            assertThat(c.getDataDepositado()).isEqualTo(HOJE);
        }

        @Test
        @DisplayName("Devolução só de cheque depositado; cancelamento só de cheque a depositar")
        void transicoes() {
            ChequePreDatado c = new ChequePreDatado();
            when(cheques.findById(1L)).thenReturn(Optional.of(c));

            assertThatThrownBy(() -> service.devolver(1L)).isInstanceOf(NegocioException.class);
            c.setSituacao(SituacaoCheque.DEPOSITADO);
            assertThatThrownBy(() -> service.cancelar(1L)).isInstanceOf(NegocioException.class);
            service.devolver(1L);
            assertThat(c.getSituacao()).isEqualTo(SituacaoCheque.DEVOLVIDO);
        }
    }

    @Nested
    @DisplayName("RN09 — Remessa para cartório")
    class Remessas {

        @Mock CartorioRepository cartorios;
        @Mock RemessaRepository remessas;
        CartorioService service;

        @BeforeEach
        void setUp() {
            service = new CartorioService(cartorios, remessas, contas, relogio);
        }

        Cartorio cartorio() {
            Cartorio c = new Cartorio();
            c.setId(3L);
            c.setNome("1º Tabelionato de Protesto");
            return c;
        }

        @Test
        @DisplayName("Envia título vencido para cartório cadastrado")
        void envia() {
            when(contas.findById(7L)).thenReturn(Optional.of(titulo(SituacaoConta.VENCIDA, HOJE.minusDays(10))));
            when(cartorios.findByIdAndAtivoTrue(3L)).thenReturn(Optional.of(cartorio()));
            when(remessas.findFirstByContaReceberIdAndSituacaoNot(7L, SituacaoRemessa.BAIXADA)).thenReturn(Optional.empty());
            when(remessas.save(any())).thenAnswer(i -> i.getArgument(0));

            RemessaCartorio r = service.enviar(new RemessaRequest(7L, 3L, HOJE));

            assertThat(r.getSituacao()).isEqualTo(SituacaoRemessa.ENVIADA);
        }

        @Test
        @DisplayName("Título ainda não vencido não vai a protesto")
        void naoVencido() {
            when(contas.findById(7L)).thenReturn(Optional.of(titulo(SituacaoConta.ABERTA, HOJE.plusDays(5))));

            assertThatThrownBy(() -> service.enviar(new RemessaRequest(7L, 3L, HOJE)))
                    .isInstanceOf(NegocioException.class).hasMessageContaining("vencido e não recebido");
        }

        @Test
        @DisplayName("Cartório não cadastrado é recusado (precisa existir antes)")
        void cartorioInexistente() {
            when(contas.findById(7L)).thenReturn(Optional.of(titulo(SituacaoConta.VENCIDA, HOJE.minusDays(10))));
            when(cartorios.findByIdAndAtivoTrue(3L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.enviar(new RemessaRequest(7L, 3L, HOJE)))
                    .isInstanceOf(NegocioException.class).hasMessageContaining("Cadastre-o");
        }

        @Test
        @DisplayName("Segunda remessa para o mesmo título é recusada")
        void remessaDuplicada() {
            when(contas.findById(7L)).thenReturn(Optional.of(titulo(SituacaoConta.VENCIDA, HOJE.minusDays(10))));
            when(cartorios.findByIdAndAtivoTrue(3L)).thenReturn(Optional.of(cartorio()));
            when(remessas.findFirstByContaReceberIdAndSituacaoNot(7L, SituacaoRemessa.BAIXADA))
                    .thenReturn(Optional.of(new RemessaCartorio()));

            assertThatThrownBy(() -> service.enviar(new RemessaRequest(7L, 3L, HOJE)))
                    .isInstanceOf(NegocioException.class).hasMessageContaining("já está em cartório");
        }

        @Test
        @DisplayName("Protesto antes da remessa é recusado; protesto e baixa seguem a ordem")
        void protestoEBaixa() {
            RemessaCartorio r = new RemessaCartorio();
            r.setDataRemessa(HOJE.minusDays(5));
            when(remessas.findById(1L)).thenReturn(Optional.of(r));
            when(remessas.save(any())).thenAnswer(i -> i.getArgument(0));

            assertThatThrownBy(() -> service.protestar(1L, HOJE.minusDays(6))).isInstanceOf(NegocioException.class);
            service.protestar(1L, HOJE.minusDays(2));
            assertThat(r.getSituacao()).isEqualTo(SituacaoRemessa.PROTESTADA);
            service.baixar(1L, HOJE, MotivoBaixa.DESISTENCIA);
            assertThat(r.getSituacao()).isEqualTo(SituacaoRemessa.BAIXADA);
            assertThatThrownBy(() -> service.baixar(1L, HOJE, MotivoBaixa.PAGO))
                    .isInstanceOf(NegocioException.class).hasMessageContaining("já foi baixada");
        }

        @Test
        @DisplayName("Recebimento do título dá baixa automática na remessa (motivo PAGO)")
        void baixaAutomatica() {
            RemessaCartorio r = new RemessaCartorio();
            r.setDataRemessa(HOJE.minusDays(5));
            when(remessas.findFirstByContaReceberIdAndSituacaoNot(7L, SituacaoRemessa.BAIXADA)).thenReturn(Optional.of(r));

            service.baixarPorRecebimento(7L, HOJE);

            assertThat(r.getSituacao()).isEqualTo(SituacaoRemessa.BAIXADA);
            assertThat(r.getMotivoBaixa()).isEqualTo(MotivoBaixa.PAGO);
            verify(remessas).save(r);
        }

        @Test
        @DisplayName("Cartório com remessa em andamento não pode ser excluído")
        void excluiCartorioComRemessa() {
            when(cartorios.findByIdAndAtivoTrue(3L)).thenReturn(Optional.of(cartorio()));
            when(remessas.existsByCartorioIdAndSituacaoNot(3L, SituacaoRemessa.BAIXADA)).thenReturn(true);

            assertThatThrownBy(() -> service.inativarCartorio(3L)).isInstanceOf(NegocioException.class);
        }
    }
}
