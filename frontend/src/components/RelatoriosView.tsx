import React, { useState } from 'react';
import { format, parse } from 'date-fns';
import { ptBR } from 'date-fns/locale';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useFluxoCaixa, useInadimplencia } from '../hooks/useCprQueries';
import { Natureza } from '../types/cpr';
import { formatCurrency, formatDate } from '../utils/formatters';
import { ErrorBanner } from './ErrorBanner';

const nomeMes = (mes: string) => format(parse(mes, 'yyyy-MM', new Date()), 'MMM/yy', { locale: ptBR });

/** Relatórios do relatorios-service: base própria, alimentada por eventos (pode atrasar alguns segundos). */
export const RelatoriosView: React.FC = () => {
  const [inicio, setInicio] = useState('');
  const [fim, setFim] = useState('');
  const [periodo, setPeriodo] = useState<{ inicio?: string; fim?: string }>({});
  const [natureza, setNatureza] = useState<Natureza>('RECEBER');

  const fluxo = useFluxoCaixa(periodo.inicio, periodo.fim);
  const inad = useInadimplencia(natureza);

  const dadosGrafico = (fluxo.data?.meses ?? []).map((m) => ({ ...m, rotulo: nomeMes(m.mes) }));

  return (
    <div>
      <div className="page-header">
        <div>
          <h3>Relatórios Financeiros</h3>
          <p>
            Fluxo de caixa previsto x realizado e inadimplência. Dados do relatorios-service, atualizados pelos eventos de
            título (consistência eventual: uma quitação aparece aqui em poucos segundos).
          </p>
        </div>
        <button type="button" className="btn btn-outline" onClick={() => { void fluxo.refetch(); void inad.refetch(); }}>
          ↻ Atualizar
        </button>
      </div>

      <ErrorBanner error={fluxo.error ?? inad.error} onClose={() => undefined} />

      <form
        className="filter-bar"
        onSubmit={(e) => {
          e.preventDefault();
          setPeriodo({ inicio: inicio || undefined, fim: fim || undefined });
        }}
      >
        <div className="filter-group">
          <label>Período de</label>
          <input type="date" className="input-control" value={inicio} onChange={(e) => setInicio(e.target.value)} />
        </div>
        <div className="filter-group">
          <label>até</label>
          <input type="date" className="input-control" value={fim} onChange={(e) => setFim(e.target.value)} />
        </div>
        <div className="filter-actions">
          <button type="submit" className="btn btn-primary">
            🔍 Aplicar
          </button>
          <button type="button" className="btn btn-outline" onClick={() => { setInicio(''); setFim(''); setPeriodo({}); }}>
            Padrão (−2 a +3 meses)
          </button>
        </div>
      </form>

      <div className="card">
        <div className="card-header">
          <h4>Fluxo de caixa mensal</h4>
        </div>
        <div style={{ width: '100%', height: 300, padding: '8px 16px 16px' }}>
          <ResponsiveContainer>
            <BarChart data={dadosGrafico} margin={{ top: 8, right: 8, left: 8, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--border-color)" />
              <XAxis dataKey="rotulo" tickLine={false} axisLine={false} />
              <YAxis
                tickLine={false}
                axisLine={false}
                width={80}
                tickFormatter={(v: number) => new Intl.NumberFormat('pt-BR', { notation: 'compact' }).format(v)}
              />
              <Tooltip formatter={(v: number) => formatCurrency(v)} />
              <Legend />
              <Bar dataKey="previstoReceber" name="A receber (previsto)" fill="#86efac" radius={[4, 4, 0, 0]} />
              <Bar dataKey="recebido" name="Recebido" fill="var(--success)" radius={[4, 4, 0, 0]} />
              <Bar dataKey="previstoPagar" name="A pagar (previsto)" fill="#fca5a5" radius={[4, 4, 0, 0]} />
              <Bar dataKey="pago" name="Pago" fill="var(--danger)" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
        <div className="table-responsive">
          <table className="data-table">
            <thead>
              <tr>
                <th>Mês</th>
                <th className="text-right">A receber</th>
                <th className="text-right">A pagar</th>
                <th className="text-right">Saldo previsto</th>
                <th className="text-right">Recebido</th>
                <th className="text-right">Pago</th>
                <th className="text-right">Saldo realizado</th>
              </tr>
            </thead>
            <tbody>
              {[...(fluxo.data?.meses ?? []), ...(fluxo.data ? [fluxo.data.totais] : [])].map((m) => (
                <tr key={m.mes} style={m.mes === 'TOTAL' ? { fontWeight: 700 } : undefined}>
                  <td>{m.mes === 'TOTAL' ? 'Total' : nomeMes(m.mes)}</td>
                  <td className="text-right font-mono">{formatCurrency(m.previstoReceber)}</td>
                  <td className="text-right font-mono">{formatCurrency(m.previstoPagar)}</td>
                  <td className={`text-right font-mono ${m.saldoPrevisto < 0 ? 'text-danger' : ''}`}>{formatCurrency(m.saldoPrevisto)}</td>
                  <td className="text-right font-mono">{formatCurrency(m.recebido)}</td>
                  <td className="text-right font-mono">{formatCurrency(m.pago)}</td>
                  <td className={`text-right font-mono ${m.saldoRealizado < 0 ? 'text-danger' : ''}`}>{formatCurrency(m.saldoRealizado)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="card" style={{ marginTop: 24 }}>
        <div className="card-header">
          <h4>
            {natureza === 'RECEBER' ? 'Clientes inadimplentes' : 'Contas a pagar em atraso'} — {inad.data?.quantidade ?? 0} título(s),{' '}
            {formatCurrency(inad.data?.total ?? 0)}
          </h4>
          <select className="select-control" style={{ width: 'auto' }} value={natureza} onChange={(e) => setNatureza(e.target.value as Natureza)}>
            <option value="RECEBER">A receber</option>
            <option value="PAGAR">A pagar</option>
          </select>
        </div>
        <div className="table-responsive">
          <table className="data-table">
            <thead>
              <tr>
                <th>Título</th>
                <th>{natureza === 'RECEBER' ? 'Cliente' : 'Fornecedor'}</th>
                <th>Conta</th>
                <th>Vencimento</th>
                <th className="text-right">Dias em atraso</th>
                <th className="text-right">Valor</th>
              </tr>
            </thead>
            <tbody>
              {(inad.data?.titulos ?? []).length === 0 ? (
                <tr>
                  <td colSpan={6} style={{ textAlign: 'center', padding: 24 }}>
                    Nenhum título em atraso. 🎉
                  </td>
                </tr>
              ) : (
                inad.data!.titulos.map((t) => (
                  <tr key={`${t.natureza}-${t.tituloId}`}>
                    <td className="font-mono">{t.numeroTitulo}</td>
                    <td>{t.pessoaNome}</td>
                    <td className="font-mono">{t.planoContaCodigo}</td>
                    <td>{formatDate(t.vencimento)}</td>
                    <td className="text-right">
                      <span className="badge badge-vencida">{t.diasAtraso} dia(s)</span>
                    </td>
                    <td className="text-right font-mono">{formatCurrency(t.valor)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
