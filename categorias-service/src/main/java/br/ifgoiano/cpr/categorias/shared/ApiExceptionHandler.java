package br.ifgoiano.cpr.categorias.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Converte exceções em respostas HTTP no formato {@link ErroResposta}:
 * 400 validação · 404 não encontrado · 409 conflito de dados · 422 regra de negócio ·
 * 503 serviço dependente indisponível · 500 erro inesperado.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> validacao(MethodArgumentNotValidException ex) {
        List<ErroResposta.CampoErro> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErroResposta.CampoErro(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ErroResposta(OffsetDateTime.now(), 400, "FALHA_VALIDACAO",
                "Um ou mais campos estão inválidos.", campos));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class})
    public ResponseEntity<ErroResposta> requisicaoInvalida(Exception ex) {
        return ResponseEntity.badRequest().body(ErroResposta.de(400, "REQUISICAO_INVALIDA",
                "A requisição está mal formada ou tem um valor inválido."));
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResposta> naoEncontrado(RecursoNaoEncontradoException ex) {
        return ResponseEntity.status(404).body(ErroResposta.de(404, "NAO_ENCONTRADO", ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResposta> rotaInexistente(NoResourceFoundException ex) {
        return ResponseEntity.status(404).body(ErroResposta.de(404, "NAO_ENCONTRADO", "Rota inexistente."));
    }

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ErroResposta> negocio(NegocioException ex) {
        return ResponseEntity.status(422).body(ErroResposta.de(422, "REGRA_NEGOCIO", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResposta> conflito(DataIntegrityViolationException ex) {
        return ResponseEntity.status(409).body(ErroResposta.de(409, "CONFLITO_DE_DADOS",
                "Já existe um registro com esse valor único (documento, código ou número de título)."));
    }

    @ExceptionHandler(ServicoIndisponivelException.class)
    public ResponseEntity<ErroResposta> indisponivel(ServicoIndisponivelException ex) {
        return ResponseEntity.status(503).body(ErroResposta.de(503, "SERVICO_INDISPONIVEL", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> inesperado(Exception ex) {
        log.error("Erro inesperado", ex);
        return ResponseEntity.status(500).body(ErroResposta.de(500, "ERRO_INTERNO",
                "Erro inesperado no servidor. Consulte os logs pelo traceId."));
    }
}
