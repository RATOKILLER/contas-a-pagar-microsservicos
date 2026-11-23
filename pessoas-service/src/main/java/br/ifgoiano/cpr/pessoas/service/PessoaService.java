package br.ifgoiano.cpr.pessoas.service;

import br.ifgoiano.cpr.pessoas.api.PessoaRequest;
import br.ifgoiano.cpr.pessoas.domain.Pessoa;
import br.ifgoiano.cpr.pessoas.domain.PessoaRepository;
import br.ifgoiano.cpr.pessoas.domain.TipoPessoa;
import br.ifgoiano.cpr.pessoas.evento.PessoaAtualizadaEvento;
import br.ifgoiano.cpr.pessoas.shared.NegocioException;
import br.ifgoiano.cpr.pessoas.shared.RecursoNaoEncontradoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PessoaService {

    private final PessoaRepository repository;
    private final ApplicationEventPublisher eventos;

    public PessoaService(PessoaRepository repository, ApplicationEventPublisher eventos) {
        this.repository = repository;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public List<Pessoa> listar(TipoPessoa tipo, String nome) {
        String filtroNome = (nome == null || nome.isBlank()) ? null : nome.trim();
        return repository.buscar(tipo, filtroNome);
    }

    @Transactional(readOnly = true)
    public Pessoa buscarAtiva(Long id) {
        return repository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pessoa " + id + " não encontrada."));
    }

    @Transactional
    public Pessoa cadastrar(PessoaRequest request) {
        String documento = somenteDigitos(request.documento());
        if (repository.existsByDocumento(documento)) {
            throw new NegocioException("Já existe uma pessoa com o documento " + documento + ".");
        }
        Pessoa pessoa = new Pessoa();
        copiar(request, pessoa, documento);
        return repository.save(pessoa);
    }

    @Transactional
    public Pessoa alterar(Long id, PessoaRequest request) {
        Pessoa pessoa = buscarAtiva(id);
        String documento = somenteDigitos(request.documento());
        if (repository.existsByDocumentoAndIdNot(documento, id)) {
            throw new NegocioException("Já existe outra pessoa com o documento " + documento + ".");
        }
        copiar(request, pessoa, documento);
        Pessoa salva = repository.save(pessoa);
        eventos.publishEvent(PessoaAtualizadaEvento.de(salva));
        return salva;
    }

    @Transactional
    public void inativar(Long id) {
        Pessoa pessoa = buscarAtiva(id);
        pessoa.setAtivo(false);
        repository.save(pessoa);
        eventos.publishEvent(PessoaAtualizadaEvento.de(pessoa));
    }

    private static void copiar(PessoaRequest r, Pessoa p, String documento) {
        p.setTipo(r.tipo());
        p.setNome(r.nome().trim());
        p.setDocumento(documento);
        p.setInscricaoEstadual(vazioParaNulo(r.inscricaoEstadual()));
        p.setEndereco(vazioParaNulo(r.endereco()));
        p.setCidade(vazioParaNulo(r.cidade()));
        p.setUf(r.uf() == null || r.uf().isBlank() ? null : r.uf().trim().toUpperCase());
        p.setCep(r.cep() == null || r.cep().isBlank() ? null : somenteDigitos(r.cep()));
        p.setTelefone(vazioParaNulo(r.telefone()));
        p.setEmail(vazioParaNulo(r.email()));
        p.setContato(vazioParaNulo(r.contato()));
    }

    static String somenteDigitos(String valor) {
        return valor == null ? null : valor.replaceAll("\\D", "");
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
