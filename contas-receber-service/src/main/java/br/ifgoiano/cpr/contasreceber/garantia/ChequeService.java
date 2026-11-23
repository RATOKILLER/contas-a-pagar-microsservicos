package br.ifgoiano.cpr.contasreceber.garantia;

import br.ifgoiano.cpr.contasreceber.domain.ContaReceber;
import br.ifgoiano.cpr.contasreceber.domain.ContaReceberRepository;
import br.ifgoiano.cpr.contasreceber.garantia.GarantiaDtos.ChequeRequest;
import br.ifgoiano.cpr.contasreceber.shared.NegocioException;
import br.ifgoiano.cpr.contasreceber.shared.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * RN08 — Cheque pré-datado: recebido do cliente como garantia de um título a receber,
 * com data futura de depósito ("bom para").
 */
@Service
public class ChequeService {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ChequeRepository cheques;
    private final ContaReceberRepository contas;
    private final Clock relogio;

    public ChequeService(ChequeRepository cheques, ContaReceberRepository contas, Clock relogio) {
        this.cheques = cheques;
        this.contas = contas;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<ChequePreDatado> listar(SituacaoCheque situacao) {
        return situacao == null ? cheques.findAllByOrderByDataDepositoAscIdAsc()
                : cheques.findBySituacaoOrderByDataDepositoAscIdAsc(situacao);
    }

    @Transactional(readOnly = true)
    public ChequePreDatado buscar(Long id) {
        return cheques.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cheque " + id + " não encontrado."));
    }

    @Transactional
    public ChequePreDatado registrar(ChequeRequest req) {
        ContaReceber titulo = contas.findById(req.contaReceberId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Título a receber " + req.contaReceberId() + " não encontrado."));
        if (!titulo.getSituacao().emAberto()) {
            throw new NegocioException("Só é possível receber cheque para um título em aberto (o título "
                    + titulo.getNumeroTitulo() + " está " + titulo.getSituacao().name().toLowerCase() + ").");
        }
        if (req.emissao().isAfter(hoje())) {
            throw new NegocioException("A emissão do cheque não pode ser futura.");
        }
        if (req.dataDeposito().isBefore(req.emissao())) {
            throw new NegocioException("A data para depósito não pode ser anterior à emissão do cheque.");
        }
        String banco = req.banco().trim();
        String agencia = req.agencia().trim();
        String conta = req.contaCorrente().trim();
        String numero = req.numeroCheque().trim();
        if (cheques.existsByBancoAndAgenciaAndContaCorrenteAndNumeroCheque(banco, agencia, conta, numero)) {
            throw new NegocioException("Este cheque (banco " + banco + ", ag. " + agencia + ", c/c " + conta
                    + ", nº " + numero + ") já foi registrado.");
        }
        BigDecimal jaGarantido = cheques.somaVigentes(titulo.getId());
        if (jaGarantido.add(req.valor()).compareTo(titulo.getValor()) > 0) {
            throw new NegocioException("Os cheques do título somariam R$ " + jaGarantido.add(req.valor())
                    + ", acima do valor do título (R$ " + titulo.getValor() + ").");
        }

        ChequePreDatado c = new ChequePreDatado();
        c.setContaReceberId(titulo.getId());
        c.setNumeroCheque(numero);
        c.setBanco(banco);
        c.setAgencia(agencia);
        c.setContaCorrente(conta);
        c.setEmitente(req.emitente() == null || req.emitente().isBlank() ? titulo.getPessoaNome() : req.emitente().trim());
        c.setValor(req.valor());
        c.setEmissao(req.emissao());
        c.setDataDeposito(req.dataDeposito());
        c.setObservacao(req.observacao() == null || req.observacao().isBlank() ? null : req.observacao().trim());
        return cheques.save(c);
    }

    /** Depósito só a partir do "bom para". */
    @Transactional
    public ChequePreDatado depositar(Long id, LocalDate data) {
        ChequePreDatado c = buscar(id);
        if (c.getSituacao() != SituacaoCheque.A_DEPOSITAR) {
            throw new NegocioException("Só um cheque a depositar pode ser depositado (este está " + rotulo(c) + ").");
        }
        if (data.isBefore(c.getDataDeposito())) {
            throw new NegocioException("Cheque pré-datado: só pode ser depositado a partir de "
                    + c.getDataDeposito().format(BR) + ".");
        }
        if (data.isAfter(hoje())) {
            throw new NegocioException("A data do depósito não pode ser futura.");
        }
        c.setSituacao(SituacaoCheque.DEPOSITADO);
        c.setDataDepositado(data);
        return cheques.save(c);
    }

    /** Cheque devolvido pelo banco (sem fundos): deixa de garantir o título. */
    @Transactional
    public ChequePreDatado devolver(Long id) {
        ChequePreDatado c = buscar(id);
        if (c.getSituacao() != SituacaoCheque.DEPOSITADO) {
            throw new NegocioException("Só um cheque depositado pode ser devolvido (este está " + rotulo(c) + ").");
        }
        c.setSituacao(SituacaoCheque.DEVOLVIDO);
        return cheques.save(c);
    }

    /** Devolve o cheque ao cliente antes do depósito. */
    @Transactional
    public ChequePreDatado cancelar(Long id) {
        ChequePreDatado c = buscar(id);
        if (c.getSituacao() != SituacaoCheque.A_DEPOSITAR) {
            throw new NegocioException("Só um cheque ainda não depositado pode ser cancelado (este está " + rotulo(c) + ").");
        }
        c.setSituacao(SituacaoCheque.CANCELADO);
        return cheques.save(c);
    }

    private static String rotulo(ChequePreDatado c) {
        return c.getSituacao().name().toLowerCase().replace('_', ' ');
    }

    private LocalDate hoje() {
        return LocalDate.now(relogio);
    }
}
