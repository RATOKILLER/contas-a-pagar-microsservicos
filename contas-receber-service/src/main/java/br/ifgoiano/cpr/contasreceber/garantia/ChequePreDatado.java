package br.ifgoiano.cpr.contasreceber.garantia;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Cheque pré-datado recebido como garantia de um título a receber (RN08 · ChequePre.db). */
@Getter
@Setter
@Entity
@Table(name = "cheque_pre_datado")
public class ChequePreDatado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conta_receber_id", nullable = false)
    private Long contaReceberId;

    @Column(nullable = false)
    private String numeroCheque;
    @Column(nullable = false)
    private String banco;
    @Column(nullable = false)
    private String agencia;
    @Column(nullable = false)
    private String contaCorrente;
    @Column(nullable = false)
    private String emitente;
    @Column(nullable = false)
    private BigDecimal valor;
    @Column(nullable = false)
    private LocalDate emissao;
    /** "Bom para": antes desta data o cheque não pode ser depositado. */
    @Column(nullable = false)
    private LocalDate dataDeposito;
    private LocalDate dataDepositado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SituacaoCheque situacao = SituacaoCheque.A_DEPOSITAR;

    private String observacao;

    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
}
