package br.ifgoiano.cpr.contaspagar.evento;

import br.ifgoiano.cpr.contaspagar.shared.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entrega os eventos pendentes do outbox ao RabbitMQ a cada poucos segundos.
 * Garantia: "pelo menos uma vez". Se o broker cair no meio, o evento é reenviado;
 * por isso o consumidor descarta mensagens repetidas pelo messageId (= eventoId).
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository repository;
    private final RabbitTemplate rabbit;

    public OutboxPublisher(OutboxRepository repository, RabbitTemplate rabbit) {
        this.repository = repository;
        this.rabbit = rabbit;
    }

    @Scheduled(fixedDelayString = "${cpr.outbox.intervalo-ms:2000}")
    @Transactional
    public void publicarPendentes() {
        List<OutboxEvento> pendentes = repository.travarPendentes();
        for (OutboxEvento evento : pendentes) {
            try {
                Message mensagem = MessageBuilder.withBody(evento.getPayload().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setContentEncoding(StandardCharsets.UTF_8.name())
                        .setMessageId(evento.getId().toString())
                        .build();
                rabbit.send(RabbitConfig.EXCHANGE, evento.getRoutingKey(), mensagem);
                evento.setPublicadoEm(LocalDateTime.now());
            } catch (AmqpException e) {
                evento.setTentativas(evento.getTentativas() + 1);
                log.warn("RabbitMQ indisponível; {} evento(s) aguardando no outbox. Nova tentativa em instantes. ({})",
                        pendentes.size(), e.getMessage());
                break; // mantém a ordem: não pula para o próximo evento
            }
        }
    }
}
