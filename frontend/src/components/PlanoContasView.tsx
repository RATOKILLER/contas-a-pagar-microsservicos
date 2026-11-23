import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useExcluirPlanoConta, usePlanosConta, useSalvarPlanoConta } from '../hooks/useCprQueries';
import { PlanoContaForm, planoContaSchema } from '../schemas/cpr.schemas';
import { PlanoConta } from '../types/cpr';
import { ErrorBanner } from './ErrorBanner';
import { Campo } from './form/Campo';

/** Recuo visual pelo nível do código (1 → 1.1 → 1.1.01). */
const nivel = (codigo: string) => codigo.split('.').length - 1;

export const PlanoContasView: React.FC = () => {
  const { data: planos = [], isLoading, error } = usePlanosConta();
  const excluir = useExcluirPlanoConta();
  const [editando, setEditando] = useState<PlanoConta | null | undefined>(undefined);

  const confirmarExclusao = (p: PlanoConta) => {
    if (window.confirm(`Confirma a exclusão da conta ${p.codigo} - ${p.descricao}?`)) {
      excluir.mutate(p.id);
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h3>Plano de Contas</h3>
          <p>Categorias financeiras hierárquicas. Conta consolidada agrupa outras e não recebe títulos (RN01).</p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setEditando(null)}>
          + Nova Conta
        </button>
      </div>

      <ErrorBanner error={excluir.error ?? error} onClose={() => excluir.reset()} />

      <div className="card">
        <div className="card-header">
          <h4>Categorias ({planos.length})</h4>
        </div>
        <div className="table-responsive">
          <table className="data-table">
            <thead>
              <tr>
                <th>Código</th>
                <th>Descrição</th>
                <th>Conta Superior</th>
                <th>Natureza</th>
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
              ) : planos.length === 0 ? (
                <tr>
                  <td colSpan={5}>
                    <div className="state-box">
                      <span className="state-icon">📂</span>
                      <h5>Nenhuma conta cadastrada</h5>
                    </div>
                  </td>
                </tr>
              ) : (
                planos.map((p) => (
                  <tr key={p.id}>
                    <td className="font-mono" style={{ paddingLeft: 12 + nivel(p.codigo) * 18 }}>
                      {p.codigo}
                    </td>
                    <td className="cell-main" style={{ fontWeight: p.consolidada ? 600 : 400 }}>
                      {p.descricao}
                    </td>
                    <td>{p.superior ? `${p.superior.codigo} - ${p.superior.descricao}` : '—'}</td>
                    <td>
                      <span className={`badge ${p.consolidada ? 'badge-cancelada' : 'badge-aberta'}`}>
                        {p.consolidada ? 'Consolidada (agrupadora)' : 'Analítica (recebe títulos)'}
                      </span>
                    </td>
                    <td>
                      <div className="row-actions">
                        <button type="button" className="btn btn-sm btn-outline" onClick={() => setEditando(p)}>
                          ✏️ Editar
                        </button>
                        <button type="button" className="btn btn-sm btn-outline" onClick={() => confirmarExclusao(p)}>
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
      </div>

      {editando !== undefined && (
        <PlanoContaModal conta={editando} planos={planos} onClose={() => setEditando(undefined)} />
      )}
    </div>
  );
};

const PlanoContaModal: React.FC<{ conta: PlanoConta | null; planos: PlanoConta[]; onClose: () => void }> = ({
  conta,
  planos,
  onClose,
}) => {
  const salvar = useSalvarPlanoConta();
  const superiores = planos.filter((p) => p.consolidada && p.id !== conta?.id);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<PlanoContaForm>({
    resolver: zodResolver(planoContaSchema),
    defaultValues: conta
      ? { codigo: conta.codigo, descricao: conta.descricao, superiorId: conta.superior?.id ?? 0, consolidada: conta.consolidada ? 'true' : 'false' }
      : { codigo: '', descricao: '', superiorId: 0, consolidada: 'false' },
  });

  const enviar = handleSubmit((f) =>
    salvar.mutate(
      {
        id: conta?.id,
        dados: {
          codigo: f.codigo,
          descricao: f.descricao,
          superior: f.superiorId > 0 ? { id: f.superiorId } : null,
          consolidada: f.consolidada === 'true',
        },
      },
      { onSuccess: onClose }
    )
  );

  return (
    <div className="modal-backdrop">
      <div className="modal-panel">
        <div className="modal-header">
          <h4>{conta ? 'Editar Conta' : 'Nova Conta do Plano'}</h4>
          <button type="button" className="btn btn-sm btn-outline" onClick={onClose}>
            ✕
          </button>
        </div>
        <form onSubmit={enviar} noValidate>
          <div className="modal-body">
            <ErrorBanner error={salvar.error} onClose={() => salvar.reset()} />
            <div className="form-grid">
              <Campo label="Código *" erro={errors.codigo} dica="Ex.: 2.1.03">
                <input className="input-control" maxLength={18} {...register('codigo')} />
              </Campo>
              <Campo label="Tipo de Conta *" erro={errors.consolidada}>
                <select className="select-control" {...register('consolidada')}>
                  <option value="false">Analítica (recebe títulos)</option>
                  <option value="true">Consolidada (agrupa outras)</option>
                </select>
              </Campo>
              <Campo label="Descrição *" erro={errors.descricao} full>
                <input className="input-control" maxLength={60} {...register('descricao')} />
              </Campo>
              <Campo label="Conta Superior" erro={errors.superiorId} full dica="Só contas consolidadas podem ser superiores.">
                <select className="select-control" {...register('superiorId')}>
                  <option value={0}>Nenhuma (conta de primeiro nível)</option>
                  {superiores.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.codigo} - {p.descricao}
                    </option>
                  ))}
                </select>
              </Campo>
            </div>
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-outline" onClick={onClose}>
              Cancelar
            </button>
            <button type="submit" className="btn btn-primary" disabled={salvar.isPending}>
              {salvar.isPending ? 'Salvando...' : 'Salvar Conta'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
