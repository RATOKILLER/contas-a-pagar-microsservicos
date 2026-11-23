package br.ifgoiano.cpr.categorias.service;

import br.ifgoiano.cpr.categorias.api.PlanoContaDtos.PlanoContaRequest;
import br.ifgoiano.cpr.categorias.domain.PlanoConta;
import br.ifgoiano.cpr.categorias.domain.PlanoContaRepository;
import br.ifgoiano.cpr.categorias.evento.CategoriaAtualizadaEvento;
import br.ifgoiano.cpr.categorias.shared.NegocioException;
import br.ifgoiano.cpr.categorias.shared.RecursoNaoEncontradoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class PlanoContaService {

    private final PlanoContaRepository repository;
    private final ApplicationEventPublisher eventos;

    public PlanoContaService(PlanoContaRepository repository, ApplicationEventPublisher eventos) {
        this.repository = repository;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public List<PlanoConta> listar() {
        return repository.findByAtivaTrueOrderByCodigo();
    }

    @Transactional(readOnly = true)
    public PlanoConta buscarAtiva(Long id) {
        return repository.findByIdAndAtivaTrue(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta do plano " + id + " não encontrada."));
    }

    @Transactional
    public PlanoConta cadastrar(PlanoContaRequest request) {
        if (repository.existsByCodigo(request.codigo())) {
            throw new NegocioException("Já existe uma conta com o código " + request.codigo() + ".");
        }
        PlanoConta conta = new PlanoConta();
        copiar(request, conta);
        return repository.save(conta);
    }

    @Transactional
    public PlanoConta alterar(Long id, PlanoContaRequest request) {
        PlanoConta conta = buscarAtiva(id);
        if (repository.existsByCodigoAndIdNot(request.codigo(), id)) {
            throw new NegocioException("Já existe outra conta com o código " + request.codigo() + ".");
        }
        if (Objects.equals(request.superiorId(), id)) {
            throw new NegocioException("Uma conta não pode ser superior dela mesma.");
        }
        if (!Boolean.TRUE.equals(request.consolidada()) && repository.existsBySuperiorIdAndAtivaTrue(id)) {
            throw new NegocioException("Esta conta tem subcontas; ela precisa continuar consolidada.");
        }
        copiar(request, conta);
        PlanoConta salva = repository.save(conta);
        eventos.publishEvent(CategoriaAtualizadaEvento.de(salva));
        return salva;
    }

    @Transactional
    public void inativar(Long id) {
        PlanoConta conta = buscarAtiva(id);
        if (repository.existsBySuperiorIdAndAtivaTrue(id)) {
            throw new NegocioException("Não é possível excluir uma conta que tem subcontas ativas.");
        }
        conta.setAtiva(false);
        repository.save(conta);
        eventos.publishEvent(CategoriaAtualizadaEvento.de(conta));
    }

    private void copiar(PlanoContaRequest request, PlanoConta conta) {
        PlanoConta superior = null;
        if (request.superiorId() != null) {
            superior = repository.findByIdAndAtivaTrue(request.superiorId())
                    .orElseThrow(() -> new NegocioException("Conta superior " + request.superiorId() + " não existe."));
            // RN01: só uma conta consolidada agrupa outras.
            if (!superior.isConsolidada()) {
                throw new NegocioException("A conta superior " + superior.getCodigo() + " precisa ser consolidada.");
            }
        }
        conta.setCodigo(request.codigo().trim());
        conta.setDescricao(request.descricao().trim());
        conta.setSuperior(superior);
        conta.setConsolidada(request.consolidada());
    }
}
