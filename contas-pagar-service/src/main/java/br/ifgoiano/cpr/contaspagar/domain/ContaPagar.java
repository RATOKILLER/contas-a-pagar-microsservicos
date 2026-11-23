package br.ifgoiano.cpr.contaspagar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "conta_pagar")
public class ContaPagar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String numeroTitulo;

    // Pessoa pertence ao pessoas-service: aqui só o id + réplica para exibir sem chamar outro serviço.
    @Column(nullable = false)
    private Long pessoaId;
    @Column(nullable = false)
    private String pessoaNome;
    @Column(nullable = false)
    private String pessoaDocumento;

    // Categoria pertence ao categorias-service: mesma estratégia.
    @Column(nullable = false)
    private Long planoContaId;
    @Column(nullable = false)
    private String planoContaCodigo;
    @Column(nullable = false)
    private String planoContaDescricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoTitulo tipoTitulo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SituacaoConta situacao = SituacaoConta.ABERTA;

    @Column(nullable = false)
    private BigDecimal valor;
    @Column(nullable = false)
    private LocalDate emissao;
    @Column(nullable = false)
    private LocalDate vencimento;
    @Column(nullable = false)
    private BigDecimal desconto = BigDecimal.ZERO;
    private LocalDate validadeDesc;
    @Column(nullable = false)
    private BigDecimal acrescimo = BigDecimal.ZERO;

    private LocalDate dataPagamento;
    private BigDecimal valorPago;
    private String observacao;

    @Version
    @Column(nullable = false)
    private Long versao;

    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;
    @Column(nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    void aoCriar() {
        criadoEm = LocalDateTime.now();
        atualizadoEm = criadoEm;
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = LocalDateTime.now();
    }
}
