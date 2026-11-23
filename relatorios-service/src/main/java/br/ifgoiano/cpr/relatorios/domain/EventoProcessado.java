package br.ifgoiano.cpr.relatorios.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Id de cada evento já aplicado: garante que uma entrega repetida não conte duas vezes. */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "evento_processado")
public class EventoProcessado {

    @Id
    private UUID eventoId;
    private LocalDateTime recebidoEm;

    public EventoProcessado(UUID eventoId) {
        this.eventoId = eventoId;
        this.recebidoEm = LocalDateTime.now();
    }
}
