package br.ifgoiano.cpr.pessoas.shared;

/** Recurso inexistente (ou inativo). Vira HTTP 404. */
public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
