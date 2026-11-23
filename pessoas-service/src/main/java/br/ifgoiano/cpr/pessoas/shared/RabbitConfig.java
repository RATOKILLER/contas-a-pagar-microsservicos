package br.ifgoiano.cpr.pessoas.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Exchange único de eventos do sistema. Cada consumidor liga a sua própria fila nele. */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "cpr.eventos";

    @Bean
    public TopicExchange eventosExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter conversor = new Jackson2JsonMessageConverter(objectMapper);
        // O tipo vem do parâmetro do @RabbitListener: cada serviço tem as próprias classes de evento.
        conversor.setAlwaysConvertToInferredType(true);
        return conversor;
    }
}
