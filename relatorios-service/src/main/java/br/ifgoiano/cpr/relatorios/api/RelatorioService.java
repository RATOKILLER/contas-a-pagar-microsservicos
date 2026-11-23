package br.ifgoiano.cpr.relatorios.api;

import br.ifgoiano.cpr.relatorios.api.RelatorioDtos.*;
import br.ifgoiano.cpr.relatorios.domain.TituloResumo;
import br.ifgoiano.cpr.relatorios.domain.TituloResumoRepository;
import br.ifgoiano.cpr.relatorios.shared.NegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RelatorioService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final TituloResumoRepository titulos;

    public RelatorioService(TituloResumoRepository titulos) {
        this.titulos = titulos;
    }

    @Transactional(readOnly = true)
    public FluxoCaixa fluxoCaixa(LocalDate inicio, LocalDate fim) {
        YearMonth atual = YearMonth.now(FUSO);
        LocalDate ini = inicio != null ? inicio : atual.minusMonths(2).atDay(1);
        LocalDate end = fim != null ? fim : atual.plusMonths(3).atEndOfMonth();
        if (end.isBefore(ini)) {
            throw new NegocioException("A data final do período não pode ser anterior à inicial.");
        }
        if (ChronoUnit.MONTHS.between(YearMonth.from(ini), YearMonth.from(end)) > 36) {
            throw new NegocioException("O período máximo do fluxo de caixa é de 36 meses.");
        }

        Map<YearMonth, BigDecimal[]> meses = new LinkedHashMap<>(); // [prevReceber, prevPagar, recebido, pago]
        for (YearMonth m = YearMonth.from(ini); !m.isAfter(YearMonth.from(end)); m = m.plusMonths(1)) {
            meses.put(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        }

        for (TituloResumo t : titulos.doPeriodo(ini, end)) {
            boolean receber = "RECEBER".equals(t.getNatureza());
            if (!t.getVencimento().isBefore(ini) && !t.getVencimento().isAfter(end)) {
                somar(meses, YearMonth.from(t.getVencimento()), receber ? 0 : 1, t.getValor());
            }
            if (t.getDataQuitacao() != null && !t.getDataQuitacao().isBefore(ini) && !t.getDataQuitacao().isAfter(end)) {
                somar(meses, YearMonth.from(t.getDataQuitacao()), receber ? 2 : 3, t.getValorQuitado());
            }
        }

        List<MesFluxo> linhas = new ArrayList<>();
        BigDecimal[] total = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        meses.forEach((mes, v) -> {
            linhas.add(linha(mes.toString(), v));
            for (int i = 0; i < 4; i++) {
                total[i] = total[i].add(v[i]);
            }
        });
        return new FluxoCaixa(ini, end, linhas, linha("TOTAL", total));
    }

    @Transactional(readOnly = true)
    public Inadimplencia inadimplencia(String natureza) {
        String nat = natureza == null ? "RECEBER" : natureza.toUpperCase();
        if (!nat.equals("RECEBER") && !nat.equals("PAGAR")) {
            throw new NegocioException("Natureza deve ser RECEBER ou PAGAR.");
        }
        LocalDate hoje = LocalDate.now(FUSO);
        List<TituloAtrasado> lista = titulos.emAtraso(nat, hoje).stream()
                .map(t -> new TituloAtrasado(t.getNatureza(), t.getTituloId(), t.getNumeroTitulo(), t.getPessoaNome(),
                        t.getPlanoContaCodigo(), t.getVencimento(), t.getValor(),
                        ChronoUnit.DAYS.between(t.getVencimento(), hoje)))
                .toList();
        BigDecimal total = lista.stream().map(TituloAtrasado::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Inadimplencia(nat, hoje, lista.size(), total, lista);
    }

    private static void somar(Map<YearMonth, BigDecimal[]> meses, YearMonth mes, int i, BigDecimal valor) {
        BigDecimal[] v = meses.get(mes);
        if (v != null && valor != null) {
            v[i] = v[i].add(valor);
        }
    }

    private static MesFluxo linha(String mes, BigDecimal[] v) {
        return new MesFluxo(mes, v[0], v[1], v[2], v[3], v[0].subtract(v[1]), v[2].subtract(v[3]));
    }
}
