package br.ifgoiano.cpr.categorias.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanoContaRepository extends JpaRepository<PlanoConta, Long> {

    @EntityGraph(attributePaths = "superior")
    List<PlanoConta> findByAtivaTrueOrderByCodigo();

    @EntityGraph(attributePaths = "superior")
    Optional<PlanoConta> findByIdAndAtivaTrue(Long id);

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);

    boolean existsBySuperiorIdAndAtivaTrue(Long superiorId);
}
