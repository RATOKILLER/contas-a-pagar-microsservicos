package br.ifgoiano.cpr.contaspagar.shared;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Duas operações simultâneas no mesmo título: a segunda perde e recebe 409 (trava otimista por versão). */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ConcorrenciaHandler {

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErroResposta> concorrencia(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(409).body(ErroResposta.de(409, "CONFLITO_CONCORRENCIA",
                "O título foi alterado por outra operação ao mesmo tempo. Recarregue e tente de novo."));
    }
}
