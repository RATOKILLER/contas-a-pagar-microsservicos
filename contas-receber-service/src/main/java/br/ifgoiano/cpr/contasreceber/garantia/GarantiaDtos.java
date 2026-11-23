package br.ifgoiano.cpr.contasreceber.garantia;

import br.ifgoiano.cpr.contasreceber.domain.ContaReceber;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class GarantiaDtos {

    private GarantiaDtos() {
    }

    /** Resumo do título garantido/protestado, para a tela não precisar buscar o título à parte. */
    public record TituloResumo(Long id, String numeroTitulo, String cliente, BigDecimal valor, LocalDate vencimento,
                               String situacao) {
        static TituloResumo de(ContaReceber c) {
            return c == null ? null : new TituloResumo(c.getId(), c.getNumeroTitulo(), c.getPessoaNome(), c.getValor(),
                    c.getVencimento(), c.getSituacao().name());
        }
    }

    // ---------- Cartório ----------

    public record CartorioRequest(
            @NotBlank(message = "Informe o nome do cartório.") @Size(max = 80) String nome,
            @Size(max = 50) String contato,
            @Size(max = 20) String telefone,
            @Size(max = 50) String cidade) {
    }

    public record CartorioResponse(Long id, String nome, String contato, String telefone, String cidade) {
        static CartorioResponse de(Cartorio c) {
            return new CartorioResponse(c.getId(), c.getNome(), c.getContato(), c.getTelefone(), c.getCidade());
        }
    }

    // ---------- Cheque pré-datado ----------

    public record ChequeRequest(
            @NotNull(message = "Selecione o título a receber.") Long contaReceberId,
            @NotBlank(message = "Informe o número do cheque.") @Size(max = 20) String numeroCheque,
            @NotBlank(message = "Informe o banco.") @Pattern(regexp = "^\\d{3,4}$", message = "Código do banco com 3 ou 4 dígitos.") String banco,
            @NotBlank(message = "Informe a agência.") @Size(max = 10) String agencia,
            @NotBlank(message = "Informe a conta corrente.") @Size(max = 15) String contaCorrente,
            @Size(max = 100) String emitente,
            @NotNull(message = "Informe o valor.") @Positive(message = "O valor deve ser maior que zero.")
            @Digits(integer = 12, fraction = 2) BigDecimal valor,
            @NotNull(message = "Informe a emissão.") LocalDate emissao,
            @NotNull(message = "Informe a data para depósito (bom para).") LocalDate dataDeposito,
            @Size(max = 255) String observacao) {
    }

    public record DataRequest(@NotNull(message = "Informe a data.") LocalDate data) {
    }

    public record ChequeResponse(Long id, TituloResumo titulo, String numeroCheque, String banco, String agencia,
                                 String contaCorrente, String emitente, BigDecimal valor, LocalDate emissao,
                                 LocalDate dataDeposito, LocalDate dataDepositado, SituacaoCheque situacao,
                                 String observacao) {
        static ChequeResponse de(ChequePreDatado c, ContaReceber titulo) {
            return new ChequeResponse(c.getId(), TituloResumo.de(titulo), c.getNumeroCheque(), c.getBanco(),
                    c.getAgencia(), c.getContaCorrente(), c.getEmitente(), c.getValor(), c.getEmissao(),
                    c.getDataDeposito(), c.getDataDepositado(), c.getSituacao(), c.getObservacao());
        }
    }

    // ---------- Remessa para cartório ----------

    public record RemessaRequest(
            @NotNull(message = "Selecione o título vencido.") Long contaReceberId,
            @NotNull(message = "Selecione o cartório.") Long cartorioId,
            @NotNull(message = "Informe a data da remessa.") LocalDate dataRemessa) {
    }

    public record BaixaRequest(@NotNull(message = "Informe a data.") LocalDate data,
                               @NotNull(message = "Informe o motivo (PAGO ou DESISTENCIA).") MotivoBaixa motivo) {
    }

    public record RemessaResponse(Long id, TituloResumo titulo, CartorioResponse cartorio, LocalDate dataRemessa,
                                  LocalDate dataProtesto, LocalDate dataBaixa, MotivoBaixa motivoBaixa,
                                  SituacaoRemessa situacao) {
        static RemessaResponse de(RemessaCartorio r, ContaReceber titulo) {
            return new RemessaResponse(r.getId(), TituloResumo.de(titulo), CartorioResponse.de(r.getCartorio()),
                    r.getDataRemessa(), r.getDataProtesto(), r.getDataBaixa(), r.getMotivoBaixa(), r.getSituacao());
        }
    }
}
