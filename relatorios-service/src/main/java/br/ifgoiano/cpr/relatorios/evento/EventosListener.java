package br.ifgoiano.cpr.relatorios.evento;

import br.ifgoiano.cpr.relatorios.domain.TituloResumoRepository;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Filas do relatorios-service. A fila de títulos tem uma fila de erro (DLQ): mensagem que falhar
 * três vezes vai para relatorios.titulos.dlq em vez de travar a fila ou se perder.
 */
@Configuration
public class EventosListener {

    static final String FILA_TITULOS = "relatorios.titulos";
    static final String FILA_CADASTROS = "relatorios.cadastros";
    static final String DLX = "cpr.eventos.dlx";

    private final ProjecaoTitulos projecao;
    private final TituloResumoRepository titulos;

    public EventosListener(ProjecaoTitulos projecao, TituloResumoRepository titulos) {
        this.projecao = projecao;
        this.titulos = titulos;
    }

    @Bean
    DirectExchange dlx() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Queue filaTitulosDlq() {
        return QueueBuilder.durable(FILA_TITULOS + ".dlq").build();
    }

    @Bean
    Binding ligaDlq(Queue filaTitulosDlq, DirectExchange dlx) {
        return BindingBuilder.bind(filaTitulosDlq).to(dlx).with(FILA_TITULOS);
    }

    @Bean
    Queue filaTitulos() {
        return QueueBuilder.durable(FILA_TITULOS)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(FILA_TITULOS)
                .build();
    }

    @Bean
    Binding ligaTitulos(Queue filaTitulos, TopicExchange eventosExchange) {
        return BindingBuilder.bind(filaTitulos).to(eventosExchange).with("titulo.#");
    }

    @Bean
    Queue filaCadastros() {
        return QueueBuilder.durable(FILA_CADASTROS).build();
    }

    @Bean
    Binding ligaPessoas(Queue filaCadastros, TopicExchange eventosExchange) {
        return BindingBuilder.bind(filaCadastros).to(eventosExchange).with("pessoa.atualizada");
    }

    @Bean
    Binding ligaCategorias(Queue filaCadastros, TopicExchange eventosExchange) {
        return BindingBuilder.bind(filaCadastros).to(eventosExchange).with("categoria.atualizada");
    }

    @RabbitListener(queues = FILA_TITULOS)
    public void aoReceberTitulo(TituloEvento evento,
                                @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        projecao.aplicar(messageId == null ? null : UUID.fromString(messageId), evento);
    }

    @RabbitListener(queues = FILA_CADASTROS)
    @Transactional
    public void aoReceberCadastro(Map<String, Object> evento, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String chave) {
        if ("pessoa.atualizada".equals(chave)) {
            titulos.atualizarNomePessoa(((Number) evento.get("pessoaId")).longValue(), (String) evento.get("nome"));
        } else if ("categoria.atualizada".equals(chave)) {
            titulos.atualizarCategoria(((Number) evento.get("planoContaId")).longValue(),
                    (String) evento.get("codigo"), (String) evento.get("descricao"));
        }
    }
}
