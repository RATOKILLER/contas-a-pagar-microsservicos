package br.ifgoiano.cpr.contaspagar.api;

import br.ifgoiano.cpr.contaspagar.domain.ContaPagar;
import br.ifgoiano.cpr.contaspagar.domain.SituacaoConta;
import br.ifgoiano.cpr.contaspagar.domain.TipoTitulo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ContaPagarDtos {

    private ContaPagarDtos() {
    }

    /** Referência a um registro de outro serviço: { "id": 3 }. Campos extras enviados pelo front-end são ignorados. */
    public record Referencia(@NotNull(message = "Informe o id.") Long id) {}

    public record ContaRequest(
            @NotBlank(message = "Informe o número do título.") @Size(max = 20, message = "Máximo de 20 caracteres.") String numeroTitulo,
            @NotNull(message = "Selecione a pessoa.") @Valid Referencia pessoa,
            @NotNull(message = "Selecione a conta do plano de contas.") @Valid Referencia planoConta,
            @NotNull(message = "Selecione o tipo do título.") TipoTitulo tipoTitulo,
            @NotNull(message = "Informe o valor.") @Positive(message = "O valor deve ser maior que zero.")
            @Digits(integer = 12, fraction = 2, message = "Valor com no máximo 2 casas decimais.") BigDecimal valor,
            @NotNull(message = "Informe a emissão.") LocalDate emissao,
            @NotNull(message = "Informe o vencimento.") LocalDate vencimento,
            @PositiveOrZero(message = "O desconto não pode ser negativo.") BigDecimal desconto,
            LocalDate validadeDesc,
            @PositiveOrZero(message = "O acréscimo não pode ser negativo.") BigDecimal acrescimo,
            @Size(max = 255, message = "Máximo de 255 caracteres.") String observacao) {
    }

    public record QuitacaoRequest(
            @NotNull(message = "Informe a data.") LocalDate data,
            @PositiveOrZero(message = "O desconto não pode ser negativo.") BigDecimal desconto,
            @PositiveOrZero(message = "O acréscimo não pode ser negativo.") BigDecimal acrescimo) {
    }

    public record PessoaResumo(Long id, String nome, String documento) {}

    public record PlanoContaResumo(Long id, String codigo, String descricao) {}

    public record ContaResponse(Long id, String numeroTitulo, PessoaResumo pessoa, PlanoContaResumo planoConta,
                                TipoTitulo tipoTitulo, SituacaoConta situacao, BigDecimal valor, LocalDate emissao,
                                LocalDate vencimento, BigDecimal desconto, LocalDate validadeDesc, BigDecimal acrescimo,
                                LocalDate dataPagamento, BigDecimal valorPago, String observacao) {

        public static ContaResponse de(ContaPagar c) {
            return new ContaResponse(c.getId(), c.getNumeroTitulo(),
                    new PessoaResumo(c.getPessoaId(), c.getPessoaNome(), c.getPessoaDocumento()),
                    new PlanoContaResumo(c.getPlanoContaId(), c.getPlanoContaCodigo(), c.getPlanoContaDescricao()),
                    c.getTipoTitulo(), c.getSituacao(), c.getValor(), c.getEmissao(), c.getVencimento(),
                    c.getDesconto(), c.getValidadeDesc(), c.getAcrescimo(), c.getDataPagamento(), c.getValorPago(),
                    c.getObservacao());
        }
    }
}
