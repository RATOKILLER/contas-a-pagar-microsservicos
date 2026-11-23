import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ErrorBanner } from '../ErrorBanner';
import { Campo } from '../form/Campo';
import { QuitacaoForm, quitacaoSchema } from '../../schemas/cpr.schemas';
import { useQuitarTitulo } from '../../hooks/useCprQueries';
import { Titulo } from '../../types/cpr';
import { formatCurrency, formatDate, getTodayISO } from '../../utils/formatters';
import { ConfigNatureza } from './naturezas';

interface Props {
  config: ConfigNatureza;
  titulo: Titulo;
  onClose: () => void;
}

/** Valor que o serviço vai calcular (RN05), mostrado antes da confirmação. */
function previsao(t: Titulo, f: Partial<QuitacaoForm>) {
  const data = f.data ?? getTodayISO();
  const dentroDaValidade = !t.validadeDesc || data <= t.validadeDesc;
  const desconto = f.desconto ?? (dentroDaValidade ? Number(t.desconto) : 0);
  const acrescimo = f.acrescimo ?? (data > t.vencimento ? Number(t.acrescimo) : 0);
  return { desconto, acrescimo, total: Number(t.valor) - desconto + acrescimo, dentroDaValidade };
}

export const QuitacaoModal: React.FC<Props> = ({ config, titulo, onClose }) => {
  const quitar = useQuitarTitulo(config.natureza);
  // Chave de idempotência criada UMA vez por abertura do modal: reenvios usam a mesma chave.
  const [chave] = useState(() => crypto.randomUUID());

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<QuitacaoForm>({ resolver: zodResolver(quitacaoSchema), defaultValues: { data: getTodayISO() } });

  const valores = watch();
  const p = previsao(titulo, {
    data: valores.data,
    desconto: valores.desconto === undefined || String(valores.desconto) === '' ? undefined : Number(valores.desconto),
    acrescimo: valores.acrescimo === undefined || String(valores.acrescimo) === '' ? undefined : Number(valores.acrescimo),
  });

  const enviar = handleSubmit((f) => {
    quitar.mutate({ id: titulo.id, dados: f, chave }, { onSuccess: onClose });
  });

  return (
    <div className="modal-backdrop">
      <div className="modal-panel">
        <div className="modal-header">
          <h4>
            {config.acaoSubstantivo} do título {titulo.numeroTitulo}
          </h4>
          <button type="button" className="btn btn-sm btn-outline" onClick={onClose}>
            ✕
          </button>
        </div>
        <form onSubmit={enviar} noValidate>
          <div className="modal-body">
            <ErrorBanner error={quitar.error} onClose={() => quitar.reset()} />

            <div className="summary-box">
              <div>
                <span>{config.papelPessoa}</span>
                <strong>{titulo.pessoa.nome}</strong>
              </div>
              <div>
                <span>Valor original</span>
                <strong>{formatCurrency(titulo.valor)}</strong>
              </div>
              <div>
                <span>Vencimento</span>
                <strong>{formatDate(titulo.vencimento)}</strong>
              </div>
              <div>
                <span>Desconto previsto</span>
                <strong>
                  {Number(titulo.desconto) > 0
                    ? `${formatCurrency(titulo.desconto)} até ${titulo.validadeDesc ? formatDate(titulo.validadeDesc) : 'o pagamento'}`
                    : 'Sem desconto'}
                </strong>
              </div>
            </div>

            <div className="form-grid">
              <Campo label={`Data do ${config.acaoSubstantivo.toLowerCase()} *`} erro={errors.data} full>
                <input type="date" className="input-control" max={getTodayISO()} {...register('data')} />
              </Campo>
              <Campo label="Desconto (R$)" erro={errors.desconto} dica="Em branco: calculado pela validade.">
                <input type="number" step="0.01" className="input-control" placeholder="automático" {...register('desconto')} />
              </Campo>
              <Campo label="Acréscimo / juros (R$)" erro={errors.acrescimo} dica="Em branco: aplicado se houver atraso.">
                <input type="number" step="0.01" className="input-control" placeholder="automático" {...register('acrescimo')} />
              </Campo>
            </div>

            {!p.dentroDaValidade && Number(titulo.desconto) > 0 && (
              <div className="alert alert-warning" style={{ marginTop: 12 }}>
                <span>⚠️ A validade do desconto já passou (RN05): o desconto não será aplicado.</span>
              </div>
            )}

            <div className="summary-box" style={{ marginTop: 16, marginBottom: 0 }}>
              <div>
                <span>
                  {formatCurrency(titulo.valor)} − {formatCurrency(p.desconto)} + {formatCurrency(p.acrescimo)}
                </span>
                <strong>Valor efetivo:</strong>
              </div>
              <div style={{ textAlign: 'right' }}>
                <span>Situação após confirmar: {config.situacaoQuitada}</span>
                <strong style={{ fontSize: 16, color: '#15803d' }}>{formatCurrency(p.total)}</strong>
              </div>
            </div>
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-outline" onClick={onClose}>
              Cancelar
            </button>
            <button type="submit" className="btn btn-success" disabled={quitar.isPending}>
              {quitar.isPending ? 'Processando...' : `Confirmar ${config.acaoSubstantivo.toLowerCase()}`}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
