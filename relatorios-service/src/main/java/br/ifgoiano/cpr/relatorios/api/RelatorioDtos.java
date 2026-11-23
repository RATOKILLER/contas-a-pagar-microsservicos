package br.ifgoiano.cpr.relatorios.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class RelatorioDtos {

    private RelatorioDtos() {
    }

    /** Uma linha do fluxo de caixa: previsto (por vencimento) x realizado (por data de quitação). */
    public record MesFluxo(String mes, BigDecimal previstoReceber, BigDecimal previstoPagar, BigDecimal recebido,
                           BigDecimal pago, BigDecimal saldoPrevisto, BigDecimal saldoRealizado) {}

    public record FluxoCaixa(LocalDate inicio, LocalDate fim, List<MesFluxo> meses, MesFluxo totais) {}

    public record TituloAtrasado(String natureza, Long tituloId, String numeroTitulo, String pessoaNome,
                                 String planoContaCodigo, LocalDate vencimento, BigDecimal valor, long diasAtraso) {}

    public record Inadimplencia(String natureza, LocalDate dataBase, int quantidade, BigDecimal total,
                                List<TituloAtrasado> titulos) {}
}
