package br.ifgoiano.cpr.categorias.evento;

import br.ifgoiano.cpr.categorias.domain.PlanoConta;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Publicado (routing key categoria.atualizada) para os serviços de contas renovarem a réplica de código e descrição. */
public record CategoriaAtualizadaEvento(UUID eventoId, Long planoContaId, String codigo, String descricao,
                                        boolean consolidada, boolean ativa, OffsetDateTime ocorridoEm) {

    public static CategoriaAtualizadaEvento de(PlanoConta p) {
        return new CategoriaAtualizadaEvento(UUID.randomUUID(), p.getId(), p.getCodigo(), p.getDescricao(),
                p.isConsolidada(), p.isAtiva(), OffsetDateTime.now());
    }
}
