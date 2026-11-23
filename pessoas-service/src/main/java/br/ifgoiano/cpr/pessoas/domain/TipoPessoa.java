package br.ifgoiano.cpr.pessoas.domain;

public enum TipoPessoa {
    CLIENTE, FORNECEDOR, AMBOS;

    public boolean podeSerFornecedor() {
        return this == FORNECEDOR || this == AMBOS;
    }

    public boolean podeSerCliente() {
        return this == CLIENTE || this == AMBOS;
    }
}
