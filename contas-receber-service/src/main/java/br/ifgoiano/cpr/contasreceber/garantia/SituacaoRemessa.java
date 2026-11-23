package br.ifgoiano.cpr.contasreceber.garantia;

/** ENVIADA → PROTESTADA → BAIXADA (ou ENVIADA → BAIXADA). */
public enum SituacaoRemessa {
    ENVIADA, PROTESTADA, BAIXADA;

    public boolean emAndamento() {
        return this != BAIXADA;
    }
}
