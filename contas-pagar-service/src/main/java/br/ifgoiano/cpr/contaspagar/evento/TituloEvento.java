package br.ifgoiano.cpr.contaspagar.evento;

import br.ifgoiano.cpr.contaspagar.domain.ContaPagar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fotografia do título no momento do evento. O relatorios-service mantém a própria base a partir
 * destes eventos e usa {@code versao} para descartar um evento mais antigo que chegue depois.
 *
 * @param tipo      REGISTRADO, ATUALIZADO, QUITADO ou CANCELADO
 * @param natureza  PAGAR ou RECEBER
 */
public record TituloEvento(UUID eventoId, String tipo, String natureza, Long tituloId, String numeroTitulo,
                           Long pessoaId, String pessoaNome, Long planoContaId, String planoContaCodigo,
                           String planoContaDescricao, BigDecimal valor, LocalDate emissao, LocalDate vencimento,
                           String situacao, LocalDate dataQuitacao, BigDecimal valorQuitado, long versao,
                           OffsetDateTime ocorridoEm) {

    public static final String NATUREZA = "PAGAR";

    public static TituloEvento de(String tipo, ContaPagar c) {
        return new TituloEvento(UUID.randomUUID(), tipo, NATUREZA, c.getId(), c.getNumeroTitulo(),
                c.getPessoaId(), c.getPessoaNome(), c.getPlanoContaId(), c.getPlanoContaCodigo(),
                c.getPlanoContaDescricao(), c.getValor(), c.getEmissao(), c.getVencimento(),
                c.getSituacao().name(), c.getDataPagamento(), c.getValorPago(),
                c.getVersao(), // o serviço faz flush antes de registrar o evento: é a versão já gravada
                OffsetDateTime.now());
    }

    public String routingKey() {
        return "titulo." + tipo.toLowerCase();
    }
}
