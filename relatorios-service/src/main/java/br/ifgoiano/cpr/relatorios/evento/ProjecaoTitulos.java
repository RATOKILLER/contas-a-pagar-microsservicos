package br.ifgoiano.cpr.relatorios.evento;

import br.ifgoiano.cpr.relatorios.domain.EventoProcessado;
import br.ifgoiano.cpr.relatorios.domain.EventoProcessadoRepository;
import br.ifgoiano.cpr.relatorios.domain.TituloResumoRepository;
import br.ifgoiano.cpr.relatorios.domain.TituloResumo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Aplica um evento de título na base de leitura. Duas proteções:
 *  1) evento repetido (mesmo eventoId) é ignorado — a entrega é "pelo menos uma vez";
 *  2) evento com versão menor ou igual à já gravada é ignorado — pode chegar fora de ordem.
 */
@Service
public class ProjecaoTitulos {

    private static final Logger log = LoggerFactory.getLogger(ProjecaoTitulos.class);

    private final TituloResumoRepository titulos;
    private final EventoProcessadoRepository processados;

    public ProjecaoTitulos(TituloResumoRepository titulos, EventoProcessadoRepository processados) {
        this.titulos = titulos;
        this.processados = processados;
    }

    /** @return true se o evento alterou a base de leitura */
    @Transactional
    public boolean aplicar(UUID eventoId, TituloEvento e) {
        UUID id = eventoId != null ? eventoId : e.eventoId();
        if (processados.existsById(id)) {
            log.info("Evento {} já processado; ignorado (entrega repetida)", id);
            return false;
        }
        processados.save(new EventoProcessado(id));

        TituloResumo atual = titulos.findById(new TituloResumo.Chave(e.natureza(), e.tituloId())).orElse(null);
        if (atual != null && atual.getVersao() >= e.versao()) {
            log.info("Evento {} do título {}/{} é antigo (v{} <= v{}); ignorado", id, e.natureza(), e.tituloId(),
                    e.versao(), atual.getVersao());
            return false;
        }
        TituloResumo t = atual != null ? atual : new TituloResumo();
        t.setNatureza(e.natureza());
        t.setTituloId(e.tituloId());
        t.setNumeroTitulo(e.numeroTitulo());
        t.setPessoaId(e.pessoaId());
        t.setPessoaNome(e.pessoaNome());
        t.setPlanoContaId(e.planoContaId());
        t.setPlanoContaCodigo(e.planoContaCodigo());
        t.setPlanoContaDescricao(e.planoContaDescricao());
        t.setValor(e.valor());
        t.setEmissao(e.emissao());
        t.setVencimento(e.vencimento());
        t.setSituacao(e.situacao());
        t.setDataQuitacao(e.dataQuitacao());
        t.setValorQuitado(e.valorQuitado());
        t.setVersao(e.versao());
        t.setAtualizadoEm(LocalDateTime.now());
        titulos.save(t);
        log.info("Título {}/{} {} (v{})", e.natureza(), e.numeroTitulo(), e.tipo().toLowerCase(), e.versao());
        return true;
    }
}
