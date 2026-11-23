package br.ifgoiano.cpr.categorias.shared;

import java.time.OffsetDateTime;
import java.util.List;

/** Formato único de erro de todos os serviços (e do fallback do gateway). */
public record ErroResposta(OffsetDateTime timestamp, int status, String erro, String mensagem, List<CampoErro> campos) {

    public record CampoErro(String nome, String mensagem) {}

    public static ErroResposta de(int status, String erro, String mensagem) {
        return new ErroResposta(OffsetDateTime.now(), status, erro, mensagem, List.of());
    }
}
