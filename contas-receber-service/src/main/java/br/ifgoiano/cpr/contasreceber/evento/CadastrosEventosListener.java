package br.ifgoiano.cpr.contasreceber.evento;

import br.ifgoiano.cpr.contasreceber.domain.ContaReceberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Mantém a réplica local de pessoa e categoria atualizada (consistência eventual).
 * A operação é idempotente: aplicar o mesmo evento duas vezes dá o mesmo resultado.
 */
@Configuration
public class CadastrosEventosListener {

    private static final Logger log = LoggerFactory.getLogger(CadastrosEventosListener.class);
    static final String FILA = "contas-receber.cadastros";

    private final ContaReceberRepository repository;

    public CadastrosEventosListener(ContaReceberRepository repository) {
        this.repository = repository;
    }

    @Bean
    Queue filaCadastros() {
        return QueueBuilder.durable(FILA).build();
    }

    @Bean
    Binding ligaPessoa(Queue filaCadastros, TopicExchange eventosExchange) {
        return BindingBuilder.bind(filaCadastros).to(eventosExchange).with("pessoa.atualizada");
    }

    @Bean
    Binding ligaCategoria(Queue filaCadastros, TopicExchange eventosExchange) {
        return BindingBuilder.bind(filaCadastros).to(eventosExchange).with("categoria.atualizada");
    }

    @RabbitListener(queues = FILA)
    @Transactional
    public void aoReceber(Map<String, Object> evento, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String chave) {
        if ("pessoa.atualizada".equals(chave)) {
            Long id = ((Number) evento.get("pessoaId")).longValue();
            int n = repository.atualizarReplicaPessoa(id, (String) evento.get("nome"), (String) evento.get("documento"));
            log.info("Réplica da pessoa {} atualizada em {} título(s)", id, n);
        } else if ("categoria.atualizada".equals(chave)) {
            Long id = ((Number) evento.get("planoContaId")).longValue();
            int n = repository.atualizarReplicaCategoria(id, (String) evento.get("codigo"), (String) evento.get("descricao"));
            log.info("Réplica da categoria {} atualizada em {} título(s)", id, n);
        }
    }
}
