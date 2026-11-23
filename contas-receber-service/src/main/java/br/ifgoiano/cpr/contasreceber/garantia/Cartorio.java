package br.ifgoiano.cpr.contasreceber.garantia;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Cartório de protesto (Cartorio.db do livro). */
@Getter
@Setter
@Entity
@Table(name = "cartorio")
public class Cartorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nome;
    private String contato;
    private String telefone;
    private String cidade;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
}
