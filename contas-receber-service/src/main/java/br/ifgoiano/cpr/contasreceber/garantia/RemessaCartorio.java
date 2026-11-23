package br.ifgoiano.cpr.contasreceber.garantia;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Envio de um título vencido a protesto (RN09 · RemessaCartorio.db): datas de remessa, protesto e baixa. */
@Getter
@Setter
@Entity
@Table(name = "remessa_cartorio")
public class RemessaCartorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conta_receber_id", nullable = false)
    private Long contaReceberId;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "cartorio_id", nullable = false)
    private Cartorio cartorio;

    @Column(nullable = false)
    private LocalDate dataRemessa;
    private LocalDate dataProtesto;
    private LocalDate dataBaixa;

    @Enumerated(EnumType.STRING)
    private MotivoBaixa motivoBaixa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SituacaoRemessa situacao = SituacaoRemessa.ENVIADA;

    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
}
