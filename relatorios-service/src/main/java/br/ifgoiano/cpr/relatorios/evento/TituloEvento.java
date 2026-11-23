package br.ifgoiano.cpr.relatorios.evento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Cópia local do contrato do evento publicado pelos serviços de contas (cada serviço tem a sua). */
public record TituloEvento(UUID eventoId, String tipo, String natureza, Long tituloId, String numeroTitulo,
                           Long pessoaId, String pessoaNome, Long planoContaId, String planoContaCodigo,
                           String planoContaDescricao, BigDecimal valor, LocalDate emissao, LocalDate vencimento,
                           String situacao, LocalDate dataQuitacao, BigDecimal valorQuitado, long versao,
                           OffsetDateTime ocorridoEm) {
}
