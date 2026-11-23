package br.ifgoiano.cpr.contasreceber.evento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvento, UUID> {

    /**
     * Próximo lote pendente, travando as linhas lidas (SKIP LOCKED): se o serviço rodar
     * em mais de uma instância, duas instâncias nunca publicam o mesmo evento ao mesmo tempo.
     */
    @Query(value = """
            select * from outbox_evento
            where publicado_em is null
            order by criado_em
            limit 50
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvento> travarPendentes();

    long countByPublicadoEmIsNull();
}
