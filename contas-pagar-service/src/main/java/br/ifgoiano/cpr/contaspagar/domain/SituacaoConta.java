package br.ifgoiano.cpr.contaspagar.domain;

/** Situação explícita do título (extensão do grupo: o livro deduzia a situação pelas datas). */
public enum SituacaoConta {
    ABERTA, PAGA, RECEBIDA, VENCIDA, CANCELADA;

    /** Só títulos em aberto ou vencidos podem ser alterados, quitados ou cancelados. */
    public boolean emAberto() {
        return this == ABERTA || this == VENCIDA;
    }
}
