package br.ifgoiano.cpr.contasreceber.garantia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartorioRepository extends JpaRepository<Cartorio, Long> {

    List<Cartorio> findByAtivoTrueOrderByNome();

    Optional<Cartorio> findByIdAndAtivoTrue(Long id);

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);
}
