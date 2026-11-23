package br.ifgoiano.cpr.contasreceber.domain;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ContaReceberRepository extends JpaRepository<ContaReceber, Long>, JpaSpecificationExecutor<ContaReceber> {

    boolean existsByNumeroTitulo(String numeroTitulo);

    boolean existsByNumeroTituloAndIdNot(String numeroTitulo, Long id);

    List<ContaReceber> findBySituacaoAndVencimentoBefore(SituacaoConta situacao, LocalDate data);

    /** Atualiza a réplica do nome quando o pessoas-service avisa que a pessoa mudou. */
    @Modifying
    @Query("update ContaReceber c set c.pessoaNome = :nome, c.pessoaDocumento = :documento where c.pessoaId = :pessoaId")
    int atualizarReplicaPessoa(@Param("pessoaId") Long pessoaId, @Param("nome") String nome,
                               @Param("documento") String documento);

    /** Atualiza a réplica da categoria quando o categorias-service avisa que ela mudou. */
    @Modifying
    @Query("update ContaReceber c set c.planoContaCodigo = :codigo, c.planoContaDescricao = :descricao where c.planoContaId = :planoContaId")
    int atualizarReplicaCategoria(@Param("planoContaId") Long planoContaId, @Param("codigo") String codigo,
                                  @Param("descricao") String descricao);

    /** Filtros da listagem: período de vencimento, situação e pessoa (todos opcionais). */
    final class Filtros {
        private Filtros() {
        }

        public static Specification<ContaReceber> de(LocalDate inicio, LocalDate fim, SituacaoConta situacao, Long pessoaId) {
            Specification<ContaReceber> spec = Specification.where(null);
            if (inicio != null) {
                spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("vencimento"), inicio));
            }
            if (fim != null) {
                spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("vencimento"), fim));
            }
            if (situacao != null) {
                spec = spec.and((r, q, cb) -> cb.equal(r.get("situacao"), situacao));
            }
            if (pessoaId != null) {
                spec = spec.and((r, q, cb) -> cb.equal(r.get("pessoaId"), pessoaId));
            }
            return spec;
        }
    }
}
