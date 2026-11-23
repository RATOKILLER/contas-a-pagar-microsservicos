import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useCancelarTitulo, useTitulos } from '../../hooks/useCprQueries';
import { FiltroContas, Natureza, Titulo } from '../../types/cpr';
import { formatCurrency, formatDate, formatSituacao, formatTipoTitulo } from '../../utils/formatters';
import { ErrorBanner } from '../ErrorBanner';
import { StatusBadge } from '../StatusBadge';
import { ContaFormModal } from './ContaFormModal';
import { QuitacaoModal } from './QuitacaoModal';
import { NATUREZA_CONFIG, situacoesDe } from './naturezas';

/** Listagem com filtros + ações de um tipo de título. Usada por Contas a Pagar e Contas a Receber. */
export const ContasView: React.FC<{ natureza: Natureza }> = ({ natureza }) => {
  const config = NATUREZA_CONFIG[natureza];
  const [filtro, setFiltro] = useState<FiltroContas>({});
  const { data: titulos = [], isLoading, error } = useTitulos(natureza, filtro);
  const cancelar = useCancelarTitulo(natureza);

  const [editando, setEditando] = useState<Titulo | null | undefined>(undefined); // undefined = modal fechado
  const [quitando, setQuitando] = useState<Titulo | null>(null);

  const filtroForm = useForm<FiltroContas>({ defaultValues: { inicio: '', fim: '', situacao: '' } });

  const aplicarFiltro = filtroForm.handleSubmit((f) =>
    setFiltro({ inicio: f.inicio || undefined, fim: f.fim || undefined, situacao: f.situacao || undefined })
  );
  const limparFiltro = () => {
    filtroForm.reset({ inicio: '', fim: '', situacao: '' });
    setFiltro({});
  };

  const confirmarCancelamento = (t: Titulo) => {
    // RN10: toda exclusão pede confirmação. Aqui o título é cancelado, não apagado (RN07).
    if (window.confirm(`Confirma o cancelamento do título ${t.numeroTitulo}? Ele continuará nas consultas como CANCELADA.`)) {
      cancelar.mutate(t.id);
    }
  };

  const emAberto = (t: Titulo) => t.situacao === 'ABERTA' || t.situacao === 'VENCIDA';

  return (
    <div>
      <div className="page-header">
        <div>
          <h3>{config.titulo}</h3>
          <p>{config.subtitulo}</p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setEditando(null)}>
          + Novo título
        </button>
      </div>

      <ErrorBanner error={cancelar.error} onClose={() => cancelar.reset()} />
      <ErrorBanner error={error} onClose={() => undefined} />

      <form className="filter-bar" onSubmit={aplicarFiltro}>
        <div className="filter-group">
          <label htmlFor={`inicio-${natureza}`}>Vencimento de</label>
          <input id={`inicio-${natureza}`} type="date" className="input-control" {...filtroForm.register('inicio')} />
        </div>
        <div className="filter-group">
          <label htmlFor={`fim-${natureza}`}>Vencimento até</label>
          <input id={`fim-${natureza}`} type="date" className="input-control" {...filtroForm.register('fim')} />
        </div>
        <div className="filter-group">
          <label htmlFor={`situacao-${natureza}`}>Situação</label>
          <select id={`situacao-${natureza}`} className="select-control" {...filtroForm.register('situacao')}>
            <option value="">Todas as situações</option>
            {situacoesDe(config).map((s) => (
              <option key={s} value={s}>
                {formatSituacao(s)}
              </option>
            ))}
          </select>
        </div>
        <div className="filter-actions">
          <button type="submit" className="btn btn-primary">
            🔍 Filtrar
          </button>
          <button type="button" className="btn btn-outline" onClick={limparFiltro}>
            Limpar
          </button>
        </div>
      </form>

      <div className="card">
        <div className="card-header">
          <h4>
            Títulos ({titulos.length})
          </h4>
        </div>
        <div className="table-responsive">
          <table className="data-table">
            <thead>
              <tr>
                <th>Nº Título</th>
                <th>{config.papelPessoa}</th>
                <th>Plano de Conta</th>
                <th>Tipo</th>
                <th>Emissão / Venc.</th>
                <th className="text-right">Valor Original</th>
                <th className="text-right">{config.colunaQuitado}</th>
                <th>Situação</th>
                <th>Ações</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                Array.from({ length: 4 }).map((_, i) => (
                  <tr key={i}>
                    <td colSpan={9}>
                      <div className="skeleton-row" />
                    </td>
                  </tr>
                ))
              ) : titulos.length === 0 ? (
                <tr>
                  <td colSpan={9}>
                    <div className="state-box">
                      <span className="state-icon">📭</span>
                      <h5>Nenhum título encontrado</h5>
                      <p>Não há títulos para os filtros selecionados.</p>
                      <button type="button" className="btn btn-primary" onClick={() => setEditando(null)}>
                        + Lançar título
                      </button>
                    </div>
                  </td>
                </tr>
              ) : (
                titulos.map((t) => (
                  <tr key={t.id}>
                    <td className="font-mono">{t.numeroTitulo}</td>
                    <td>
                      <span className="cell-main">{t.pessoa.nome}</span>
                      <span className="cell-sub">{t.pessoa.documento}</span>
                    </td>
                    <td>
                      <span className="cell-main">{t.planoConta.descricao}</span>
                      <span className="cell-sub">{t.planoConta.codigo}</span>
                    </td>
                    <td>
                      <span className="badge badge-tipo">{formatTipoTitulo(t.tipoTitulo)}</span>
                    </td>
                    <td>
                      <span className="cell-main">{formatDate(t.vencimento)}</span>
                      <span className="cell-sub">Emissão: {formatDate(t.emissao)}</span>
                    </td>
                    <td className="text-right font-mono">{formatCurrency(t.valor)}</td>
                    <td className="text-right font-mono">
                      {t.valorQuitado != null ? (
                        <div>
                          <strong>{formatCurrency(t.valorQuitado)}</strong>
                          <span className="cell-sub">em {formatDate(t.dataQuitacao)}</span>
                        </div>
                      ) : (
                        '—'
                      )}
                    </td>
                    <td>
                      <StatusBadge situacao={t.situacao} />
                    </td>
                    <td>
                      <div className="row-actions">
                        {emAberto(t) && (
                          <>
                            <button
                              type="button"
                              className="btn btn-sm btn-success"
                              title={`Registrar ${config.acaoSubstantivo.toLowerCase()}`}
                              onClick={() => setQuitando(t)}
                            >
                              {config.botaoQuitar}
                            </button>
                            <button type="button" className="btn btn-sm btn-outline" title="Editar" onClick={() => setEditando(t)}>
                              ✏️
                            </button>
                            <button
                              type="button"
                              className="btn btn-sm btn-outline"
                              title="Cancelar título"
                              onClick={() => confirmarCancelamento(t)}
                            >
                              🗑️
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {editando !== undefined && (
        <ContaFormModal config={config} titulo={editando} onClose={() => setEditando(undefined)} />
      )}
      {quitando && <QuitacaoModal config={config} titulo={quitando} onClose={() => setQuitando(null)} />}
    </div>
  );
};
