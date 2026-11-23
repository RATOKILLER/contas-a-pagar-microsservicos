package br.ifgoiano.cpr.relatorios.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventoProcessadoRepository extends JpaRepository<EventoProcessado, UUID> {
}
