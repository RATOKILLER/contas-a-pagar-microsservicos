package br.ifgoiano.cpr.relatorios.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "titulo_resumo")
@IdClass(TituloResumo.Chave.class)
public class TituloResumo {

    @Id
    private String natureza;
    @Id
    private Long tituloId;

    @Column(nullable = false)
    private String numeroTitulo;
    @Column(nullable = false)
    private Long pessoaId;
    @Column(nullable = false)
    private String pessoaNome;
    @Column(nullable = false)
    private Long planoContaId;
    @Column(nullable = false)
    private String planoContaCodigo;
    @Column(nullable = false)
    private String planoContaDescricao;
    @Column(nullable = false)
    private BigDecimal valor;
    @Column(nullable = false)
    private LocalDate emissao;
    @Column(nullable = false)
    private LocalDate vencimento;
    @Column(nullable = false)
    private String situacao;
    private LocalDate dataQuitacao;
    private BigDecimal valorQuitado;
    @Column(nullable = false)
    private long versao;
    @Column(nullable = false)
    private LocalDateTime atualizadoEm;

    /** Chave composta: o mesmo id pode existir em contas a pagar e em contas a receber. */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Chave implements Serializable {
        private String natureza;
        private Long tituloId;
    }
}
