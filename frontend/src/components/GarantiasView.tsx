import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  useBaixarRemessa,
  useCancelarCheque,
  useCartorios,
  useCheques,
  useDepositarCheque,
  useDevolverCheque,
  useEnviarRemessa,
  useExcluirCartorio,
  useProtestarRemessa,
  useRegistrarCheque,
  useRemessas,
  useSalvarCartorio,
  useTitulos,
} from '../hooks/useCprQueries';
import {
  CartorioForm,
  cartorioSchema,
  ChequeForm,
  chequeSchema,
  RemessaForm,
  remessaSchema,
} from '../schemas/cpr.schemas';
import { Cartorio, SituacaoCheque, SituacaoRemessa } from '../types/cpr';
import { formatCurrency, formatDate, getTodayISO } from '../utils/formatters';
import { ErrorBanner } from './ErrorBanner';
import { Campo } from './form/Campo';

type Aba = 'cheques' | 'remessas' | 'cartorios';

const ROTULO_CHEQUE: Record<SituacaoCheque, [string, string]> = {
  A_DEPOSITAR: ['A depositar', 'badge-aberta'],
  DEPOSITADO: ['Depositado', 'badge-paga'],
  DEVOLVIDO: ['Devolvido', 'badge-vencida'],
  CANCELADO: ['Cancelado', 'badge-cancelada'],
};

const ROTULO_REMESSA: Record<SituacaoRemessa, [string, string]> = {
  ENVIADA: ['Enviada', 'badge-aberta'],
  PROTESTADA: ['Protestada', 'badge-vencida'],
  BAIXADA: ['Baixada', 'badge-paga'],
};

/** RN08 (cheque pré-datado) e RN09 (remessa para cartório) — menu Contas a Receber do livro. */
export const GarantiasView: React.FC = () => {
  const [aba, setAba] = useState<Aba>('cheques');
  return (
    <div>
      <div className="page-header">
        <div>
          <h3>Cheques Pré-datados e Cartório</h3>
          <p>
            Garantias e cobrança dos títulos a receber: cheques recebidos com data futura de depósito (RN08) e títulos
            vencidos enviados a protesto (RN09).
          </p>
        </div>
      </div>
      <div className="row-actions" style={{ marginBottom: 16 }}>
        {(
          [
            ['cheques', '🧾 Cheques pré-datados'],
            ['remessas', '⚖️ Remessas para cartório'],
            ['cartorios', '🏛️ Cartórios'],
          ] as [Aba, string][]
        ).map(([k, rotulo]) => (
          <button
            key={k}
            type="button"
            className={`btn ${aba === k ? 'btn-primary' : 'btn-outline'}`}
            onClick={() => setAba(k)}
          >
            {rotulo}
          </button>
        ))}
      </div>
      {aba === 'cheques' && <ChequesAba />}
      {aba === 'remessas' && <RemessasAba />}
      {aba === 'cartorios' && <CartoriosAba />}
    </div>
  );
};

// ======================= Cheques =======================

const ChequesAba: React.FC = () => {
  const { data: cheques = [], isLoading, error } = useCheques();
  const depositar = useDepositarCheque();
  const devolver = useDevolverCheque();
  const cancelar = useCancelarCheque();
  const [novo, setNovo] = useState(false);
  const hoje = getTodayISO();
  const erroAcao = depositar.error ?? devolver.error ?? cancelar.error;
  const limparErros = () => {
    depositar.reset();
    devolver.reset();
    cancelar.reset();
  };

  return (
    <div className="card">
      <div className="card-header">
        <h4>Cheques ({cheques.length})</h4>
        <button type="button" className="btn btn-primary" onClick={() => setNovo(true)}>
          + Registrar cheque
        </button>
      </div>
      <div style={{ padding: '0 16px' }}>
        <ErrorBanner error={erroAcao ?? error} onClose={limparErros} />
      </div>
      <div className="table-responsive">
        <table className="data-table">
          <thead>
            <tr>
              <th>Título / Cliente</th>
              <th>Cheque</th>
              <th>Emitente</th>
              <th>Bom para</th>
              <th className="text-right">Valor</th>
              <th>Situação</th>
              <th>Ações</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr>
                <td colSpan={7}>
                  <div className="skeleton-row" />
                </td>
              </tr>
            ) : cheques.length === 0 ? (
              <tr>
                <td colSpan={7}>
                  <div className="state-box">
                    <span className="state-icon">🧾</span>
                    <h5>Nenhum cheque registrado</h5>
                    <p>Registre cheques pré-datados recebidos como garantia de títulos a receber.</p>
                  </div>
                </td>
              </tr>
            ) : (
              cheques.map((c) => (
                <tr key={c.id}>
                  <td>
                    <span className="cell-main font-mono">{c.titulo?.numeroTitulo ?? `#${c.id}`}</span>
                    <span className="cell-sub">{c.titulo?.cliente ?? ''}</span>
                  </td>
                  <td>
                    <span className="cell-main font-mono">nº {c.numeroCheque}</span>
                    <span className="cell-sub">
                      Banco {c.banco} · Ag. {c.agencia} · C/C {c.contaCorrente}
                    </span>
                  </td>
                  <td>{c.emitente}</td>
                  <td>
                    <span className="cell-main">{formatDate(c.dataDeposito)}</span>
                    {c.dataDepositado && <span className="cell-sub">Depositado em {formatDate(c.dataDepositado)}</span>}
                  </td>
                  <td className="text-right font-mono">{formatCurrency(c.valor)}</td>
                  <td>
                    <span className={`badge ${ROTULO_CHEQUE[c.situacao][1]}`}>{ROTULO_CHEQUE[c.situacao][0]}</span>
                  </td>
                  <td>
                    <div className="row-actions">
                      {c.situacao === 'A_DEPOSITAR' && (
                        <>
                          <button
                            type="button"
                            className="btn btn-sm btn-success"
                            disabled={c.dataDeposito > hoje}
                            title={c.dataDeposito > hoje ? `Pré-datado: só a partir de ${formatDate(c.dataDeposito)}` : 'Depositar hoje'}
                            onClick={() => depositar.mutate({ id: c.id, data: hoje })}
                          >
                            🏦 Depositar
                          </button>
                          <button
                            type="button"
                            className="btn btn-sm btn-outline"
                            title="Devolver ao cliente (cancelar)"
                            onClick={() => window.confirm(`Confirma o cancelamento do cheque nº ${c.numeroCheque}?`) && cancelar.mutate(c.id)}
                          >
                            🗑️
                          </button>
                        </>
                      )}
                      {c.situacao === 'DEPOSITADO' && (
                        <button
                          type="button"
                          className="btn btn-sm btn-outline"
                          onClick={() => window.confirm(`O banco devolveu o cheque nº ${c.numeroCheque} (sem fundos)?`) && devolver.mutate(c.id)}
                        >
                          ↩️ Devolvido
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      {novo && <ChequeModal onClose={() => setNovo(false)} />}
    </div>
  );
};

const ChequeModal: React.FC<{ onClose: () => void }> = ({ onClose }) => {
  const { data: titulos = [] } = useTitulos('RECEBER');
  const emAberto = titulos.filter((t) => t.situacao === 'ABERTA' || t.situacao === 'VENCIDA');
  const registrar = useRegistrarCheque();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ChequeForm>({
    resolver: zodResolver(chequeSchema),
    defaultValues: { contaReceberId: 0, emissao: getTodayISO(), dataDeposito: getTodayISO(), banco: '', emitente: '', observacao: '' },
  });

  const enviar = handleSubmit((f) =>
    registrar.mutate(
      { ...f, emitente: f.emitente || undefined, observacao: f.observacao || undefined },
      { onSuccess: onClose }
    )
  );

  return (
    <div className="modal-backdrop">
      <div className="modal-panel">
        <div className="modal-header">
          <h4>Registrar cheque pré-datado</h4>
          <button type="button" className="btn btn-sm btn-outline" onClick={onClose}>
            ✕
          </button>
        </div>
        <form onSubmit={enviar} noValidate>
          <div className="modal-body">
            <ErrorBanner error={registrar.error} onClose={() => registrar.reset()} />
            <div className="form-grid">
              <Campo label="Título a receber garantido *" erro={errors.contaReceberId} full dica="Só títulos em aberto ou vencidos.">
                <select className="select-control" {...register('contaReceberId')}>
                  <option value={0} disabled>
                    Selecione...
                  </option>
                  {emAberto.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.numeroTitulo} · {t.pessoa.nome} · {formatCurrency(t.valor)} · venc. {formatDate(t.vencimento)}
                    </option>
                  ))}
                </select>
              </Campo>
              <Campo label="Nº do cheque *" erro={errors.numeroCheque}>
                <input className="input-control" maxLength={20} {...register('numeroCheque')} />
              </Campo>
              <Campo label="Banco *" erro={errors.banco} dica="Código: 001, 104, 237, 341...">
                <input className="input-control" maxLength={4} {...register('banco')} />
              </Campo>
              <Campo label="Agência *" erro={errors.agencia}>
                <input className="input-control" maxLength={10} {...register('agencia')} />
              </Campo>
              <Campo label="Conta corrente *" erro={errors.contaCorrente}>
                <input className="input-control" maxLength={15} {...register('contaCorrente')} />
              </Campo>
              <Campo label="Valor (R$) *" erro={errors.valor} dica="A soma dos cheques não pode passar do título.">
                <input type="number" step="0.01" className="input-control" {...register('valor')} />
              </Campo>
              <Campo label="Emitente" erro={errors.emitente} dica="Em branco: o cliente do título.">
                <input className="input-control" maxLength={100} {...register('emitente')} />
              </Campo>
              <Campo label="Emissão *" erro={errors.emissao}>
                <input type="date" className="input-control" max={getTodayISO()} {...register('emissao')} />
              </Campo>
              <Campo label="Bom para (depósito) *" erro={errors.dataDeposito}>
                <input type="date" className="input-control" {...register('dataDeposito')} />
              </Campo>
              <Campo label="Observação" erro={errors.observacao} full>
                <input className="input-control" maxLength={255} {...register('observacao')} />
              </Campo>
            </div>
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-outline" onClick={onClose}>
              Cancelar
            </button>
            <button type="submit" className="btn btn-primary" disabled={registrar.isPending}>
              {registrar.isPending ? 'Salvando...' : 'Registrar cheque'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

// ======================= Remessas =======================

const RemessasAba: React.FC = () => {
  const { data: remessas = [], isLoading, error } = useRemessas();
  const protestar = useProtestarRemessa();
  const baixar = useBaixarRemessa();
  const [nova, setNova] = useState(false);
  const hoje = getTodayISO();
  const erroAcao = protestar.error ?? baixar.error;

  return (
    <div className="card">
      <div className="card-header">
        <h4>Remessas para protesto ({remessas.length})</h4>
        <button type="button" className="btn btn-primary" onClick={() => setNova(true)}>
          + Enviar título a protesto
        </button>
      </div>
      <div style={{ padding: '0 16px' }}>
        <ErrorBanner error={erroAcao ?? error} onClose={() => { protestar.reset(); baixar.reset(); }} />
      </div>
      <div className="table-responsive">
        <table className="data-table">
          <thead>
            <tr>
              <th>Título / Cliente</th>
              <th className="text-right">Valor</th>
              <th>Cartório</th>
              <th>Remessa</th>
              <th>Protesto</th>
              <th>Baixa</th>
              <th>Situação</th>
              <th>Ações</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr>
                <td colSpan={8}>
                  <div className="skeleton-row" />
                </td>
              </tr>
            ) : remessas.length === 0 ? (
              <tr>
                <td colSpan={8}>
                  <div className="state-box">
                    <span className="state-icon">⚖️</span>
                    <h5>Nenhuma remessa</h5>
                    <p>Títulos vencidos e não recebidos podem ser enviados a protesto em um cartório cadastrado.</p>
                  </div>
                </td>
              </tr>
            ) : (
              remessas.map((r) => (
                <tr key={r.id}>
                  <td>
                    <span className="cell-main font-mono">{r.titulo?.numeroTitulo}</span>
                    <span className="cell-sub">
                      {r.titulo?.cliente} · venc. {formatDate(r.titulo?.vencimento)}
                    </span>
                  </td>
                  <td className="text-right font-mono">{formatCurrency(r.titulo?.valor)}</td>
                  <td>{r.cartorio.nome}</td>
                  <td>{formatDate(r.dataRemessa)}</td>
                  <td>{formatDate(r.dataProtesto)}</td>
                  <td>
                    <span className="cell-main">{formatDate(r.dataBaixa)}</span>
                    {r.motivoBaixa && <span className="cell-sub">{r.motivoBaixa === 'PAGO' ? 'Pago' : 'Desistência'}</span>}
                  </td>
                  <td>
                    <span className={`badge ${ROTULO_REMESSA[r.situacao][1]}`}>{ROTULO_REMESSA[r.situacao][0]}</span>
                  </td>
                  <td>
                    <div className="row-actions">
                      {r.situacao === 'ENVIADA' && (
                        <button type="button" className="btn btn-sm btn-outline" onClick={() => protestar.mutate({ id: r.id, data: hoje })}>
                          📌 Protestado hoje
                        </button>
                      )}
                      {r.situacao !== 'BAIXADA' && (
                        <button
                          type="button"
                          className="btn btn-sm btn-outline"
                          title="Retirar o título do cartório"
                          onClick={() =>
                            window.confirm(`Desistir do protesto do título ${r.titulo?.numeroTitulo}?`) &&
                            baixar.mutate({ id: r.id, data: hoje, motivo: 'DESISTENCIA' })
                          }
                        >
                          ↩️ Desistir
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      <p className="field-hint" style={{ padding: '0 16px 16px' }}>
        Quando o título é recebido (tela Contas a Receber), a remessa recebe baixa automática com motivo “Pago”.
      </p>
      {nova && <RemessaModal onClose={() => setNova(false)} />}
    </div>
  );
};

const RemessaModal: React.FC<{ onClose: () => void }> = ({ onClose }) => {
  const { data: titulos = [] } = useTitulos('RECEBER', { situacao: 'VENCIDA' });
  const { data: cartorios = [] } = useCartorios();
  const enviarRemessa = useEnviarRemessa();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RemessaForm>({
    resolver: zodResolver(remessaSchema),
    defaultValues: { contaReceberId: 0, cartorioId: 0, dataRemessa: getTodayISO() },
  });

  return (
    <div className="modal-backdrop">
      <div className="modal-panel">
        <div className="modal-header">
          <h4>Enviar título a protesto</h4>
          <button type="button" className="btn btn-sm btn-outline" onClick={onClose}>
            ✕
          </button>
        </div>
        <form onSubmit={handleSubmit((f) => enviarRemessa.mutate(f, { onSuccess: onClose }))} noValidate>
          <div className="modal-body">
            <ErrorBanner error={enviarRemessa.error} onClose={() => enviarRemessa.reset()} />
            {cartorios.length === 0 && (
              <div className="alert alert-warning">
                <span>⚠️ Cadastre um cartório antes (aba Cartórios) — RN09.</span>
              </div>
            )}
            <div className="form-grid">
              <Campo label="Título vencido *" erro={errors.contaReceberId} full dica="Só títulos vencidos e não recebidos.">
                <select className="select-control" {...register('contaReceberId')}>
                  <option value={0} disabled>
                    Selecione...
                  </option>
                  {titulos.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.numeroTitulo} · {t.pessoa.nome} · {formatCurrency(t.valor)} · venc. {formatDate(t.vencimento)}
                    </option>
                  ))}
                </select>
              </Campo>
              <Campo label="Cartório *" erro={errors.cartorioId}>
                <select className="select-control" {...register('cartorioId')}>
                  <option value={0} disabled>
                    Selecione...
                  </option>
                  {cartorios.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.nome}
                    </option>
                  ))}
                </select>
              </Campo>
              <Campo label="Data da remessa *" erro={errors.dataRemessa}>
                <input type="date" className="input-control" max={getTodayISO()} {...register('dataRemessa')} />
              </Campo>
            </div>
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-outline" onClick={onClose}>
              Cancelar
            </button>
            <button type="submit" className="btn btn-primary" disabled={enviarRemessa.isPending}>
              {enviarRemessa.isPending ? 'Enviando...' : 'Enviar a protesto'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

// ======================= Cartórios =======================

const CartoriosAba: React.FC = () => {
  const { data: cartorios = [], isLoading, error } = useCartorios();
  const excluir = useExcluirCartorio();
  const [editando, setEditando] = useState<Cartorio | null | undefined>(undefined);

  return (
    <div className="card">
      <div className="card-header">
        <h4>Cartórios ({cartorios.length})</h4>
        <button type="button" className="btn btn-primary" onClick={() => setEditando(null)}>
          + Novo cartório
        </button>
      </div>
      <div style={{ padding: '0 16px' }}>
        <ErrorBanner error={excluir.error ?? error} onClose={() => excluir.reset()} />
      </div>
      <div className="table-responsive">
        <table className="data-table">
          <thead>
            <tr>
              <th>Nome</th>
              <th>Cidade</th>
              <th>Contato</th>
              <th>Telefone</th>
              <th>Ações</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr>
                <td colSpan={5}>
                  <div className="skeleton-row" />
                </td>
              </tr>
            ) : cartorios.length === 0 ? (
              <tr>
                <td colSpan={5} style={{ textAlign: 'center', padding: 24 }}>
                  Nenhum cartório cadastrado.
                </td>
              </tr>
            ) : (
              cartorios.map((c) => (
                <tr key={c.id}>
                  <td className="cell-main">{c.nome}</td>
                  <td>{c.cidade ?? '—'}</td>
                  <td>{c.contato ?? '—'}</td>
                  <td>{c.telefone ?? '—'}</td>
                  <td>
                    <div className="row-actions">
                      <button type="button" className="btn btn-sm btn-outline" onClick={() => setEditando(c)}>
                        ✏️ Editar
                      </button>
                      <button
                        type="button"
                        className="btn btn-sm btn-outline"
                        onClick={() => window.confirm(`Confirma a exclusão do cartório ${c.nome}?`) && excluir.mutate(c.id)}
                      >
                        🗑️
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      {editando !== undefined && <CartorioModal cartorio={editando} onClose={() => setEditando(undefined)} />}
    </div>
  );
};

const CartorioModal: React.FC<{ cartorio: Cartorio | null; onClose: () => void }> = ({ cartorio, onClose }) => {
  const salvar = useSalvarCartorio();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<CartorioForm>({
    resolver: zodResolver(cartorioSchema),
    defaultValues: {
      nome: cartorio?.nome ?? '',
      contato: cartorio?.contato ?? '',
      telefone: cartorio?.telefone ?? '',
      cidade: cartorio?.cidade ?? '',
    },
  });

  const enviar = handleSubmit((f) =>
    salvar.mutate(
      {
        id: cartorio?.id,
        dados: { nome: f.nome, contato: f.contato || null, telefone: f.telefone || null, cidade: f.cidade || null },
      },
      { onSuccess: onClose }
    )
  );

  return (
    <div className="modal-backdrop">
      <div className="modal-panel">
        <div className="modal-header">
          <h4>{cartorio ? 'Editar cartório' : 'Novo cartório'}</h4>
          <button type="button" className="btn btn-sm btn-outline" onClick={onClose}>
            ✕
          </button>
        </div>
        <form onSubmit={enviar} noValidate>
          <div className="modal-body">
            <ErrorBanner error={salvar.error} onClose={() => salvar.reset()} />
            <div className="form-grid">
              <Campo label="Nome *" erro={errors.nome} full>
                <input className="input-control" maxLength={80} placeholder="Ex.: 1º Tabelionato de Protesto" {...register('nome')} />
              </Campo>
              <Campo label="Cidade" erro={errors.cidade}>
                <input className="input-control" maxLength={50} {...register('cidade')} />
              </Campo>
              <Campo label="Contato" erro={errors.contato}>
                <input className="input-control" maxLength={50} {...register('contato')} />
              </Campo>
              <Campo label="Telefone" erro={errors.telefone}>
                <input className="input-control" maxLength={20} {...register('telefone')} />
              </Campo>
            </div>
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-outline" onClick={onClose}>
              Cancelar
            </button>
            <button type="submit" className="btn btn-primary" disabled={salvar.isPending}>
              {salvar.isPending ? 'Salvando...' : 'Salvar cartório'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
