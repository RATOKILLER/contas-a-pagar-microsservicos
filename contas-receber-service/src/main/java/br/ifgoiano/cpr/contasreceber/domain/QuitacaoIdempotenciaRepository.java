package br.ifgoiano.cpr.contasreceber.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuitacaoIdempotenciaRepository extends JpaRepository<QuitacaoIdempotencia, String> {
}
