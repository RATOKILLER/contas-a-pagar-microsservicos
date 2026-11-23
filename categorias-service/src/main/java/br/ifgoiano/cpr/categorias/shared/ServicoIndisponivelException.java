package br.ifgoiano.cpr.categorias.shared;

/** Um serviço do qual esta operação depende não respondeu. Vira HTTP 503. */
public class ServicoIndisponivelException extends RuntimeException {
    public ServicoIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
