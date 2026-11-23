package br.ifgoiano.cpr.contaspagar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Registro de uma chave Idempotency-Key já usada numa quitação. */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "quitacao_idempotencia")
public class QuitacaoIdempotencia {

    @Id
    private String chave;

    @Column(name = "conta_id", nullable = false)
    private Long contaId;

    @Column(nullable = false)
    private LocalDateTime criadoEm;

    public QuitacaoIdempotencia(String chave, Long contaId) {
        this.chave = chave;
        this.contaId = contaId;
        this.criadoEm = LocalDateTime.now();
    }
}
