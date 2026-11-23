package br.ifgoiano.cpr.contasreceber.garantia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RemessaRepository extends JpaRepository<RemessaCartorio, Long> {

    List<RemessaCartorio> findAllByOrderByDataRemessaDescIdDesc();

    List<RemessaCartorio> findBySituacaoOrderByDataRemessaDescIdDesc(SituacaoRemessa situacao);

    Optional<RemessaCartorio> findFirstByContaReceberIdAndSituacaoNot(Long contaReceberId, SituacaoRemessa situacao);

    boolean existsByCartorioIdAndSituacaoNot(Long cartorioId, SituacaoRemessa situacao);
}
