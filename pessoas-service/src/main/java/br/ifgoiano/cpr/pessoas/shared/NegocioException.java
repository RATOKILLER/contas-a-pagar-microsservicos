package br.ifgoiano.cpr.pessoas.shared;

/** Violação de regra de negócio. Vira HTTP 422. */
public class NegocioException extends RuntimeException {
    public NegocioException(String mensagem) {
        super(mensagem);
    }
}
