import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useExcluirPessoa, usePessoas, useSalvarPessoa } from '../hooks/useCprQueries';
import { PessoaForm, pessoaSchema } from '../schemas/cpr.schemas';
import { Pessoa, TipoPessoa } from '../types/cpr';
import { formatTipoPessoa } from '../utils/formatters';
import { ErrorBanner } from './ErrorBanner';
import { Campo } from './form/Campo';

const TIPOS_PESSOA: TipoPessoa[] = ['CLIENTE', 'FORNECEDOR', 'AMBOS'];

const vazio: PessoaForm = {
  tipo: 'FORNECEDOR', nome: '', documento: '', inscricaoEstadual: '', endereco: '', cidade: '', uf: '',
  cep: '', telefone: '', email: '', contato: '',
};

const paraForm = (p: Pessoa): PessoaForm => ({
  tipo: p.tipo,
  nome: p.nome,
  documento: p.documento,
  inscricaoEstadual: p.inscricaoEstadual ?? '',
  endereco: p.endereco ?? '',
  cidade: p.cidade ?? '',
  uf: p.uf ?? '',
  cep: p.cep ?? '',
  telefone: p.telefone ?? '',
  email: p.email ?? '',
  contato: p.contato ?? '',
});

export const PessoasView: React.FC = () => {
  const { data: pessoas = [], isLoading, error } = usePessoas();
  const excluir = useExcluirPessoa();
  const [busca, setBusca] = useState('');
  const [filtroTipo, setFiltroTipo] = useState<TipoPessoa | ''>('');
  const [editando, setEditando] = useState<Pessoa | null | undefined>(undefined);

  const termo = busca.toLowerCase();
  const filtradas = pessoas.filter(
    (p) =>
      (!filtroTipo || p.tipo === filtroTipo || p.tipo === 'AMBOS') &&
      (!termo ||
        p.nome.toLowerCase().includes(termo) ||
        p.documento.includes(termo.replace(/\D/g, '') || termo) ||
        (p.email ?? '').toLowerCase().includes(termo))
  );

  const confirmarExclusao = (p: Pessoa) => {
    if (window.confirm(`Confirma a exclusão de ${p.nome}? Os títulos já lançados continuam consultáveis.`)) {
      excluir.mutate(p.id);
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h3>Pessoas (Clientes e Fornecedores)</h3>
          <p>Cadastro prévio obrigatório para lançar títulos (RN04). Dados mantidos pelo pessoas-service.</p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setEditando(null)}>
          + Nova Pessoa
        </button>
      </div>

      <ErrorBanner error={excluir.error ?? error} onClose={() => excluir.reset()} />

      <div className="filter-bar">
        <div className="filter-group" style={{ flex: 2 }}>
          <label>Buscar por nome, CPF/CNPJ ou e-mail</label>
          <input className="input-control" placeholder="Digite para filtrar..." value={busca} onChange={(e) => setBusca(e.target.value)} />
        </div>
        <div className="filter-group">
          <label>Tipo de vínculo</label>
          <select className="select-control" value={filtroTipo} onChange={(e) => setFiltroTipo(e.target.value as TipoPessoa | '')}>
            <option value="">Todos os tipos</option>
            {TIPOS_PESSOA.map((t) => (
              <option key={t} value={t}>
                {formatTipoPessoa(t)}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <h4>Pessoas cadastradas ({filtradas.length})</h4>
        </div>
        <div className="table-responsive">
          <table className="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Nome / Razão Social</th>
                <th>CPF / CNPJ</th>
                <th>Tipo</th>
                <th>Cidade / UF</th>
                <th>Contato / Telefone</th>
                <th>Ações</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                Array.from({ length: 3 }).map((_, i) => (
                  <tr key={i}>
                    <td colSpan={7}>
                      <div className="skeleton-row" />
                    </td>
                  </tr>
                ))
              ) : filtradas.length === 0 ? (
                <tr>
                  <td colSpan={7}>
                    <div className="state-box">
                      <span className="state-icon">👥</span>
                      <h5>Nenhuma pessoa encontrada</h5>
                      <p>Cadastre clientes e fornecedores para poder lançar títulos.</p>
                      <button type="button" className="btn btn-primary" onClick={() => setEditando(null)}>
                        + Cadastrar Pessoa
                      </button>
                    </div>
                  </td>
                </tr>
              ) : (
                filtradas.map((p) => (
                  <tr key={p.id}>
                    <td className="font-mono">#{p.id}</td>
                    <td>
                      <span className="cell-main">{p.nome}</span>
                      <span className="cell-sub">{p.email ?? ''}</span>
                    </td>
                    <td className="font-mono">{p.documento}</td>
                    <td>
                      <span className="badge badge-tipo">{formatTipoPessoa(p.tipo)}</span>
                    </td>
                    <td>{p.cidade ? `${p.cidade}/${p.uf ?? ''}` : '—'}</td>
                    <td>
                      <span className="cell-main">{p.telefone ?? '—'}</span>
                      <span className="cell-sub">{p.contato ?? ''}</span>
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

      {editando !== undefined && <PessoaModal pessoa={editando} onClose={() => setEditando(undefined)} />}
    </div>
  );
};

const PessoaModal: React.FC<{ pessoa: Pessoa | null; onClose: () => void }> = ({ pessoa, onClose }) => {
  const salvar = useSalvarPessoa();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<PessoaForm>({ resolver: zodResolver(pessoaSchema), defaultValues: pessoa ? paraForm(pessoa) : vazio });

  const enviar = handleSubmit((f) =>
    salvar.mutate(
      {
        id: pessoa?.id,
        dados: { ...f, uf: f.uf?.toUpperCase(), email: f.email || undefined },
      },
      { onSuccess: onClose }
    )
  );

  return (
    <div className="modal-backdrop">
      <div className="modal-panel">
        <div className="modal-header">
          <h4>{pessoa ? 'Editar Pessoa' : 'Cadastrar Nova Pessoa'}</h4>
          <button type="button" className="btn btn-sm btn-outline" onClick={onClose}>
            ✕
          </button>
        </div>
        <form onSubmit={enviar} noValidate>
          <div className="modal-body">
            <ErrorBanner error={salvar.error} onClose={() => salvar.reset()} />
            <div className="form-grid">
              <Campo label="Nome / Razão Social *" erro={errors.nome} full>
                <input className="input-control" maxLength={100} placeholder="Ex: Tech Suprimentos Ltda" {...register('nome')} />
              </Campo>
              <Campo label="CPF ou CNPJ *" erro={errors.documento}>
                <input className="input-control" maxLength={18} placeholder="12.345.678/0001-90" {...register('documento')} />
              </Campo>
              <Campo label="Tipo de Pessoa *" erro={errors.tipo}>
                <select className="select-control" {...register('tipo')}>
                  {TIPOS_PESSOA.map((t) => (
                    <option key={t} value={t}>
                      {formatTipoPessoa(t)}
                    </option>
                  ))}
                </select>
              </Campo>
              <Campo label="Inscrição Estadual" erro={errors.inscricaoEstadual}>
                <input className="input-control" maxLength={15} {...register('inscricaoEstadual')} />
              </Campo>
              <Campo label="Telefone" erro={errors.telefone}>
                <input className="input-control" maxLength={20} placeholder="(64) 99999-0000" {...register('telefone')} />
              </Campo>
              <Campo label="E-mail" erro={errors.email}>
                <input type="email" className="input-control" maxLength={80} {...register('email')} />
              </Campo>
              <Campo label="Pessoa de Contato" erro={errors.contato}>
                <input className="input-control" maxLength={50} {...register('contato')} />
              </Campo>
              <Campo label="Cidade" erro={errors.cidade}>
                <input className="input-control" maxLength={50} {...register('cidade')} />
              </Campo>
              <Campo label="UF" erro={errors.uf}>
                <input className="input-control" maxLength={2} placeholder="GO" {...register('uf')} />
              </Campo>
              <Campo label="CEP" erro={errors.cep}>
                <input className="input-control" maxLength={9} placeholder="75600-000" {...register('cep')} />
              </Campo>
              <Campo label="Endereço" erro={errors.endereco} full>
                <input className="input-control" maxLength={120} {...register('endereco')} />
              </Campo>
            </div>
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-outline" onClick={onClose}>
              Cancelar
            </button>
            <button type="submit" className="btn btn-primary" disabled={salvar.isPending}>
              {salvar.isPending ? 'Salvando...' : 'Salvar Pessoa'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
