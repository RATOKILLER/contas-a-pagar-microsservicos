package br.ifgoiano.cpr.pessoas.evento;

import br.ifgoiano.cpr.pessoas.shared.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica o evento só DEPOIS do commit: nunca avisa os outros serviços de uma alteração que foi desfeita.
 * Se o RabbitMQ estiver fora, a alteração da pessoa vale mesmo assim (o cadastro não pode parar);
 * a réplica nos serviços de contas também é renovada a cada novo lançamento ou alteração de título.
 */
@Component
public class PessoaEventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(PessoaEventoPublisher.class);

    private final RabbitTemplate rabbit;

    public PessoaEventoPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(PessoaAtualizadaEvento evento) {
        try {
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, "pessoa.atualizada", evento);
        } catch (AmqpException e) {
            log.warn("RabbitMQ indisponível; evento pessoa.atualizada da pessoa {} não publicado: {}",
                    evento.pessoaId(), e.getMessage());
        }
    }
}
