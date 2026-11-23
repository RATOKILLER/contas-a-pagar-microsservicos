package br.ifgoiano.cpr.contaspagar.evento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Evento ainda não (ou já) entregue ao RabbitMQ. Gravado na mesma transação do título. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "outbox_evento")
public class OutboxEvento {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String routingKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private LocalDateTime criadoEm;

    private LocalDateTime publicadoEm;

    @Column(nullable = false)
    private int tentativas;

    public OutboxEvento(UUID id, String routingKey, String payload) {
        this.id = id;
        this.routingKey = routingKey;
        this.payload = payload;
        this.criadoEm = LocalDateTime.now();
    }
}
