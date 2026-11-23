package br.ifgoiano.cpr.categorias.evento;

import br.ifgoiano.cpr.categorias.shared.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Publica depois do commit; falha do RabbitMQ não desfaz o cadastro (ver PessoaEventoPublisher). */
@Component
public class CategoriaEventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(CategoriaEventoPublisher.class);

    private final RabbitTemplate rabbit;

    public CategoriaEventoPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(CategoriaAtualizadaEvento evento) {
        try {
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, "categoria.atualizada", evento);
        } catch (AmqpException e) {
            log.warn("RabbitMQ indisponível; evento categoria.atualizada da conta {} não publicado: {}",
                    evento.planoContaId(), e.getMessage());
        }
    }
}
