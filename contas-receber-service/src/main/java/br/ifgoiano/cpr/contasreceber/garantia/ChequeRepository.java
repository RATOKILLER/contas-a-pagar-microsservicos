package br.ifgoiano.cpr.contasreceber.garantia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ChequeRepository extends JpaRepository<ChequePreDatado, Long> {

    List<ChequePreDatado> findAllByOrderByDataDepositoAscIdAsc();

    List<ChequePreDatado> findBySituacaoOrderByDataDepositoAscIdAsc(SituacaoCheque situacao);

    List<ChequePreDatado> findByContaReceberIdOrderByDataDeposito(Long contaReceberId);

    boolean existsByBancoAndAgenciaAndContaCorrenteAndNumeroCheque(String banco, String agencia, String conta, String numero);

    /** Soma dos cheques que ainda garantem o título (a depositar ou depositados). */
    @Query("""
            select coalesce(sum(c.valor), 0) from ChequePreDatado c
            where c.contaReceberId = :contaId
              and c.situacao in (br.ifgoiano.cpr.contasreceber.garantia.SituacaoCheque.A_DEPOSITAR,
                                 br.ifgoiano.cpr.contasreceber.garantia.SituacaoCheque.DEPOSITADO)
            """)
    BigDecimal somaVigentes(@Param("contaId") Long contaId);
}
