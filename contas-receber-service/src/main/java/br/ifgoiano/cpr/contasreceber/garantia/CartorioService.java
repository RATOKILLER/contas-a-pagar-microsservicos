package br.ifgoiano.cpr.contasreceber.garantia;

import br.ifgoiano.cpr.contasreceber.domain.ContaReceber;
import br.ifgoiano.cpr.contasreceber.domain.ContaReceberRepository;
import br.ifgoiano.cpr.contasreceber.domain.SituacaoConta;
import br.ifgoiano.cpr.contasreceber.garantia.GarantiaDtos.CartorioRequest;
import br.ifgoiano.cpr.contasreceber.garantia.GarantiaDtos.RemessaRequest;
import br.ifgoiano.cpr.contasreceber.shared.NegocioException;
import br.ifgoiano.cpr.contasreceber.shared.RecursoNaoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/** RN09 — Cartórios e remessa de títulos vencidos e não recebidos para protesto. */
@Service
public class CartorioService {

    private static final Logger log = LoggerFactory.getLogger(CartorioService.class);
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CartorioRepository cartorios;
    private final RemessaRepository remessas;
    private final ContaReceberRepository contas;
    private final Clock relogio;

    public CartorioService(CartorioRepository cartorios, RemessaRepository remessas, ContaReceberRepository contas,
                           Clock relogio) {
        this.cartorios = cartorios;
        this.remessas = remessas;
        this.contas = contas;
        this.relogio = relogio;
    }

    // ---------- Cartórios ----------

    @Transactional(readOnly = true)
    public List<Cartorio> listarCartorios() {
        return cartorios.findByAtivoTrueOrderByNome();
    }

    @Transactional(readOnly = true)
    public Cartorio buscarCartorio(Long id) {
        return cartorios.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cartório " + id + " não encontrado."));
    }

    @Transactional
    public Cartorio cadastrarCartorio(CartorioRequest req) {
        if (cartorios.existsByNomeIgnoreCase(req.nome().trim())) {
            throw new NegocioException("Já existe um cartório com o nome " + req.nome().trim() + ".");
        }
        Cartorio c = new Cartorio();
        copiar(req, c);
        return cartorios.save(c);
    }

    @Transactional
    public Cartorio alterarCartorio(Long id, CartorioRequest req) {
        Cartorio c = buscarCartorio(id);
        if (cartorios.existsByNomeIgnoreCaseAndIdNot(req.nome().trim(), id)) {
            throw new NegocioException("Já existe outro cartório com o nome " + req.nome().trim() + ".");
        }
        copiar(req, c);
        return cartorios.save(c);
    }

    @Transactional
    public void inativarCartorio(Long id) {
        Cartorio c = buscarCartorio(id);
        if (remessas.existsByCartorioIdAndSituacaoNot(id, SituacaoRemessa.BAIXADA)) {
            throw new NegocioException("O cartório " + c.getNome() + " tem remessas em andamento; dê baixa nelas antes.");
        }
        c.setAtivo(false);
        cartorios.save(c);
    }

    private static void copiar(CartorioRequest r, Cartorio c) {
        c.setNome(r.nome().trim());
        c.setContato(vazioParaNulo(r.contato()));
        c.setTelefone(vazioParaNulo(r.telefone()));
        c.setCidade(vazioParaNulo(r.cidade()));
    }

    // ---------- Remessas ----------

    @Transactional(readOnly = true)
    public List<RemessaCartorio> listarRemessas(SituacaoRemessa situacao) {
        return situacao == null ? remessas.findAllByOrderByDataRemessaDescIdDesc()
                : remessas.findBySituacaoOrderByDataRemessaDescIdDesc(situacao);
    }

    @Transactional(readOnly = true)
    public RemessaCartorio buscarRemessa(Long id) {
        return remessas.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Remessa " + id + " não encontrada."));
    }

    /** RN09: só título vencido e não recebido vai a protesto, e o cartório precisa estar cadastrado. */
    @Transactional
    public RemessaCartorio enviar(RemessaRequest req) {
        ContaReceber titulo = contas.findById(req.contaReceberId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Título a receber " + req.contaReceberId() + " não encontrado."));
        boolean vencido = titulo.getSituacao() == SituacaoConta.VENCIDA
                || (titulo.getSituacao() == SituacaoConta.ABERTA && titulo.getVencimento().isBefore(hoje()));
        if (!vencido) {
            throw new NegocioException("Só um título vencido e não recebido pode ir a protesto (o título "
                    + titulo.getNumeroTitulo() + " está " + titulo.getSituacao().name().toLowerCase() + ").");
        }
        Cartorio cartorio = cartorios.findByIdAndAtivoTrue(req.cartorioId())
                .orElseThrow(() -> new NegocioException("O cartório " + req.cartorioId() + " não está cadastrado. Cadastre-o antes da remessa."));
        if (remessas.findFirstByContaReceberIdAndSituacaoNot(titulo.getId(), SituacaoRemessa.BAIXADA).isPresent()) {
            throw new NegocioException("O título " + titulo.getNumeroTitulo() + " já está em cartório.");
        }
        if (!req.dataRemessa().isAfter(titulo.getVencimento())) {
            throw new NegocioException("A remessa deve ser posterior ao vencimento (" + titulo.getVencimento().format(BR) + ").");
        }
        if (req.dataRemessa().isAfter(hoje())) {
            throw new NegocioException("A data da remessa não pode ser futura.");
        }
        RemessaCartorio r = new RemessaCartorio();
        r.setContaReceberId(titulo.getId());
        r.setCartorio(cartorio);
        r.setDataRemessa(req.dataRemessa());
        return remessas.save(r);
    }

    @Transactional
    public RemessaCartorio protestar(Long id, LocalDate data) {
        RemessaCartorio r = buscarRemessa(id);
        if (r.getSituacao() != SituacaoRemessa.ENVIADA) {
            throw new NegocioException("Só uma remessa enviada pode ser protestada (esta está " + r.getSituacao().name().toLowerCase() + ").");
        }
        validarData(r, data, "protesto");
        r.setDataProtesto(data);
        r.setSituacao(SituacaoRemessa.PROTESTADA);
        return remessas.save(r);
    }

    @Transactional
    public RemessaCartorio baixar(Long id, LocalDate data, MotivoBaixa motivo) {
        RemessaCartorio r = buscarRemessa(id);
        if (!r.getSituacao().emAndamento()) {
            throw new NegocioException("Esta remessa já foi baixada em " + r.getDataBaixa().format(BR) + ".");
        }
        validarData(r, data, "baixa");
        r.setDataBaixa(data);
        r.setMotivoBaixa(motivo);
        r.setSituacao(SituacaoRemessa.BAIXADA);
        return remessas.save(r);
    }

    /** Chamado pelo recebimento: título pago com remessa em andamento → baixa automática (motivo PAGO). */
    @Transactional
    public void baixarPorRecebimento(Long contaReceberId, LocalDate data) {
        remessas.findFirstByContaReceberIdAndSituacaoNot(contaReceberId, SituacaoRemessa.BAIXADA).ifPresent(r -> {
            r.setDataBaixa(data.isBefore(r.getDataRemessa()) ? r.getDataRemessa() : data);
            r.setMotivoBaixa(MotivoBaixa.PAGO);
            r.setSituacao(SituacaoRemessa.BAIXADA);
            remessas.save(r);
            log.info("Remessa {} baixada automaticamente: título {} recebido", r.getId(), contaReceberId);
        });
    }

    /** O título está em cartório? (bloqueia cancelamento e alteração) */
    @Transactional(readOnly = true)
    public Optional<RemessaCartorio> remessaEmAndamento(Long contaReceberId) {
        return remessas.findFirstByContaReceberIdAndSituacaoNot(contaReceberId, SituacaoRemessa.BAIXADA);
    }

    private void validarData(RemessaCartorio r, LocalDate data, String evento) {
        if (data.isBefore(r.getDataRemessa())) {
            throw new NegocioException("A data de " + evento + " não pode ser anterior à remessa (" + r.getDataRemessa().format(BR) + ").");
        }
        if (data.isAfter(hoje())) {
            throw new NegocioException("A data de " + evento + " não pode ser futura.");
        }
    }

    private static String vazioParaNulo(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private LocalDate hoje() {
        return LocalDate.now(relogio);
    }
}
