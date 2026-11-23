package br.ifgoiano.cpr.contasreceber.service;

import br.ifgoiano.cpr.contasreceber.api.ContaReceberDtos.ContaRequest;
import br.ifgoiano.cpr.contasreceber.api.ContaReceberDtos.QuitacaoRequest;
import br.ifgoiano.cpr.contasreceber.domain.*;
import br.ifgoiano.cpr.contasreceber.evento.Outbox;
import br.ifgoiano.cpr.contasreceber.integracao.CategoriaClient;
import br.ifgoiano.cpr.contasreceber.integracao.CategoriaClient.CategoriaDTO;
import br.ifgoiano.cpr.contasreceber.integracao.PessoaClient;
import br.ifgoiano.cpr.contasreceber.integracao.PessoaClient.PessoaDTO;
import br.ifgoiano.cpr.contasreceber.shared.NegocioException;
import br.ifgoiano.cpr.contasreceber.shared.RecursoNaoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/** Regras de negócio dos títulos a receber (RN03 a RN07 do livro + extensões do grupo). */
@Service
public class ContaReceberService {

    private static final Logger log = LoggerFactory.getLogger(ContaReceberService.class);
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ContaReceberRepository repository;
    private final QuitacaoIdempotenciaRepository idempotencia;
    private final PessoaClient pessoas;
    private final CategoriaClient categorias;
    private final Outbox outbox;
    private final Clock relogio;

    public ContaReceberService(ContaReceberRepository repository, QuitacaoIdempotenciaRepository idempotencia,
                             PessoaClient pessoas, CategoriaClient categorias, Outbox outbox, Clock relogio) {
        this.repository = repository;
        this.idempotencia = idempotencia;
        this.pessoas = pessoas;
        this.categorias = categorias;
        this.outbox = outbox;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<ContaReceber> listar(LocalDate inicio, LocalDate fim, SituacaoConta situacao, Long pessoaId) {
        if (inicio != null && fim != null && fim.isBefore(inicio)) {
            throw new NegocioException("A data final do período não pode ser anterior à inicial.");
        }
        return repository.findAll(ContaReceberRepository.Filtros.de(inicio, fim, situacao, pessoaId),
                Sort.by("vencimento", "id"));
    }

    @Transactional(readOnly = true)
    public ContaReceber buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta a receber " + id + " não encontrada."));
    }

    @Transactional
    public ContaReceber lancar(ContaRequest req) {
        if (repository.existsByNumeroTitulo(req.numeroTitulo().trim())) {
            throw new NegocioException("Já existe um título com o número " + req.numeroTitulo().trim() + ".");
        }
        ContaReceber conta = new ContaReceber();
        preencher(conta, req);
        conta.setSituacao(conta.getVencimento().isBefore(hoje()) ? SituacaoConta.VENCIDA : SituacaoConta.ABERTA);
        repository.saveAndFlush(conta);
        outbox.registrar("REGISTRADO", conta);
        return conta;
    }

    @Transactional
    public ContaReceber alterar(Long id, ContaRequest req) {
        ContaReceber conta = buscar(id);
        exigirEmAberto(conta, "alterar");
        if (repository.existsByNumeroTituloAndIdNot(req.numeroTitulo().trim(), id)) {
            throw new NegocioException("Já existe outro título com o número " + req.numeroTitulo().trim() + ".");
        }
        preencher(conta, req);
        conta.setSituacao(conta.getVencimento().isBefore(hoje()) ? SituacaoConta.VENCIDA : SituacaoConta.ABERTA);
        repository.saveAndFlush(conta);
        outbox.registrar("ATUALIZADO", conta);
        return conta;
    }

    /** RN07: o título nunca some da base. "Excluir" um título em aberto = cancelá-lo. */
    @Transactional
    public ContaReceber cancelar(Long id) {
        ContaReceber conta = buscar(id);
        exigirEmAberto(conta, "cancelar");
        conta.setSituacao(SituacaoConta.CANCELADA);
        repository.saveAndFlush(conta);
        outbox.registrar("CANCELADO", conta);
        return conta;
    }

    /**
     * Registra o recebimento (RN05 e RN07). Com a mesma Idempotency-Key, uma segunda chamada
     * devolve o resultado da primeira sem receber de novo.
     */
    @Transactional
    public Resultado quitar(Long id, QuitacaoRequest req, String chaveIdempotencia) {
        if (chaveIdempotencia != null && !chaveIdempotencia.isBlank()) {
            Optional<QuitacaoIdempotencia> jaUsada = idempotencia.findById(chaveIdempotencia);
            if (jaUsada.isPresent()) {
                if (!jaUsada.get().getContaId().equals(id)) {
                    throw new NegocioException("Esta Idempotency-Key já foi usada para outro título.");
                }
                log.info("Quitação repetida do título {} (mesma Idempotency-Key): devolvendo o resultado anterior", id);
                return new Resultado(buscar(id), true);
            }
        }

        ContaReceber conta = buscar(id);
        if (conta.getSituacao() == SituacaoConta.RECEBIDA) {
            throw new NegocioException("Título já quitado em " + conta.getDataRecebimento().format(BR) + ".");
        }
        exigirEmAberto(conta, "quitar");

        LocalDate data = req.data();
        if (data.isBefore(conta.getEmissao())) {
            throw new NegocioException("A data do recebimento não pode ser anterior à emissão do título.");
        }
        if (data.isAfter(hoje())) {
            throw new NegocioException("Não é possível registrar um recebimento com data futura.");
        }

        boolean dentroDaValidade = conta.getValidadeDesc() == null || !data.isAfter(conta.getValidadeDesc());
        BigDecimal desconto;
        if (req.desconto() != null) {
            desconto = req.desconto();
            if (desconto.signum() > 0 && !dentroDaValidade) {
                throw new NegocioException("O desconto só vale até " + conta.getValidadeDesc().format(BR) + ".");
            }
        } else {
            desconto = dentroDaValidade ? conta.getDesconto() : BigDecimal.ZERO;
        }
        BigDecimal acrescimo;
        if (req.acrescimo() != null) {
            acrescimo = req.acrescimo();
        } else {
            acrescimo = data.isAfter(conta.getVencimento()) ? conta.getAcrescimo() : BigDecimal.ZERO;
        }

        BigDecimal valorEfetivo = conta.getValor().subtract(desconto).add(acrescimo);
        if (valorEfetivo.signum() <= 0) {
            throw new NegocioException("O desconto não pode ser maior ou igual ao valor do título.");
        }

        conta.setDataRecebimento(data);
        conta.setDesconto(desconto);
        conta.setAcrescimo(acrescimo);
        conta.setValorRecebido(valorEfetivo);
        conta.setSituacao(SituacaoConta.RECEBIDA);
        repository.saveAndFlush(conta);
        if (chaveIdempotencia != null && !chaveIdempotencia.isBlank()) {
            idempotencia.save(new QuitacaoIdempotencia(chaveIdempotencia, id));
        }
        outbox.registrar("QUITADO", conta);
        return new Resultado(conta, false);
    }

    /** Marca como VENCIDA todo título em aberto cujo vencimento já passou. Chamado pelo agendador. */
    @Transactional
    public int marcarVencidas() {
        List<ContaReceber> vencidas = repository.findBySituacaoAndVencimentoBefore(SituacaoConta.ABERTA, hoje());
        for (ContaReceber conta : vencidas) {
            conta.setSituacao(SituacaoConta.VENCIDA);
            repository.saveAndFlush(conta);
            outbox.registrar("ATUALIZADO", conta);
        }
        return vencidas.size();
    }

    public record Resultado(ContaReceber conta, boolean repetida) {}

    private void preencher(ContaReceber conta, ContaRequest req) {
        if (req.vencimento().isBefore(req.emissao())) {
            throw new NegocioException("O vencimento não pode ser anterior à emissão.");
        }
        BigDecimal desconto = req.desconto() == null ? BigDecimal.ZERO : req.desconto();
        if (desconto.compareTo(req.valor()) >= 0) {
            throw new NegocioException("O desconto deve ser menor que o valor do título.");
        }
        if (req.validadeDesc() != null && desconto.signum() == 0) {
            throw new NegocioException("Informe o valor do desconto ou retire a validade do desconto.");
        }

        // RN04: não se lança título para pessoa inexistente. Chamada síncrona (precisa da resposta antes de gravar).
        PessoaDTO pessoa = pessoas.buscar(req.pessoa().id());
        if (!pessoa.podeSerCliente()) {
            throw new NegocioException(pessoa.nome() + " está cadastrada como fornecedor; títulos a receber exigem um cliente.");
        }
        // RN01: conta consolidada só agrupa outras; não recebe título.
        CategoriaDTO categoria = categorias.buscar(req.planoConta().id());
        if (categoria.consolidada()) {
            throw new NegocioException("A conta " + categoria.codigo() + " é consolidada e não pode receber títulos; escolha uma subconta.");
        }

        conta.setNumeroTitulo(req.numeroTitulo().trim());
        conta.setPessoaId(pessoa.id());
        conta.setPessoaNome(pessoa.nome());
        conta.setPessoaDocumento(pessoa.documento());
        conta.setPlanoContaId(categoria.id());
        conta.setPlanoContaCodigo(categoria.codigo());
        conta.setPlanoContaDescricao(categoria.descricao());
        conta.setTipoTitulo(req.tipoTitulo());
        conta.setValor(req.valor());
        conta.setEmissao(req.emissao());
        conta.setVencimento(req.vencimento());
        conta.setDesconto(desconto);
        conta.setValidadeDesc(req.validadeDesc());
        conta.setAcrescimo(req.acrescimo() == null ? BigDecimal.ZERO : req.acrescimo());
        conta.setObservacao(req.observacao() == null || req.observacao().isBlank() ? null : req.observacao().trim());
    }

    private static void exigirEmAberto(ContaReceber conta, String acao) {
        if (!conta.getSituacao().emAberto()) {
            throw new NegocioException("Não é possível " + acao + " um título " + conta.getSituacao().name().toLowerCase() + ".");
        }
    }

    private LocalDate hoje() {
        return LocalDate.now(relogio);
    }
}
