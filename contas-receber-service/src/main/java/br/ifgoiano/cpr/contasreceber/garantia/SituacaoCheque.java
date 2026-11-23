package br.ifgoiano.cpr.contasreceber.garantia;

/** A_DEPOSITAR → DEPOSITADO → (DEVOLVIDO, se voltar sem fundos). A_DEPOSITAR → CANCELADO. */
public enum SituacaoCheque {
    A_DEPOSITAR, DEPOSITADO, DEVOLVIDO, CANCELADO;

    /** Cheques que ainda garantem o título (entram na soma contra o valor do título). */
    public boolean vigente() {
        return this == A_DEPOSITAR || this == DEPOSITADO;
    }
}
