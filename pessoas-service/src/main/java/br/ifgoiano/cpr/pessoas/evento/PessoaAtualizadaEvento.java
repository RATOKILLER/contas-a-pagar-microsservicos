package br.ifgoiano.cpr.pessoas.evento;

import br.ifgoiano.cpr.pessoas.domain.Pessoa;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento publicado no RabbitMQ (routing key pessoa.atualizada) quando uma pessoa muda ou é inativada.
 * Os serviços de contas guardam uma réplica de nome e documento e usam este evento para atualizá-la.
 */
public record PessoaAtualizadaEvento(UUID eventoId, Long pessoaId, String nome, String documento, String tipo,
                                     boolean ativo, OffsetDateTime ocorridoEm) {

    public static PessoaAtualizadaEvento de(Pessoa p) {
        return new PessoaAtualizadaEvento(UUID.randomUUID(), p.getId(), p.getNome(), p.getDocumento(),
                p.getTipo().name(), p.isAtivo(), OffsetDateTime.now());
    }
}
