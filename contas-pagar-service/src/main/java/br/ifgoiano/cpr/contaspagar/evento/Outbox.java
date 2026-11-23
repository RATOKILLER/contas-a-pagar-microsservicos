package br.ifgoiano.cpr.contaspagar.evento;

import br.ifgoiano.cpr.contaspagar.domain.ContaPagar;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Grava o evento na tabela outbox, sempre dentro da transação de quem alterou o título. */
@Component
public class Outbox {

    private final OutboxRepository repository;
    private final ObjectMapper json;

    public Outbox(OutboxRepository repository, ObjectMapper json) {
        this.repository = repository;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(String tipo, ContaPagar conta) {
        TituloEvento evento = TituloEvento.de(tipo, conta);
        try {
            repository.save(new OutboxEvento(evento.eventoId(), evento.routingKey(), json.writeValueAsString(evento)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar evento de título", e);
        }
    }
}
