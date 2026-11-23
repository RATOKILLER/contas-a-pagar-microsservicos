import React, { useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ErrorBanner } from '../ErrorBanner';
import { Campo } from '../form/Campo';
import { ContaForm, contaSchema } from '../../schemas/cpr.schemas';
import { usePessoas, usePlanosConta, useSalvarTitulo } from '../../hooks/useCprQueries';
import { Pessoa, PlanoConta, Titulo } from '../../types/cpr';
import { formatTipoTitulo, getTodayISO } from '../../utils/formatters';
import { ConfigNatureza, TIPOS_TITULO } from './naturezas';

interface Props {
  config: ConfigNatureza;
  titulo: Titulo | null; // null = novo lançamento
  onClose: () => void;
}

/**
 * Lançamento / edição de título com React Hook Form + Zod. Um formulário para pagar e receber.
 * Só monta o formulário depois que pessoas e plano de contas chegaram: assim os selects da edição
 * já nascem com a opção certa.
 */
export const ContaFormModal: React.FC<Props> = (props) => {
  const pessoasQ = usePessoas();
  const planosQ = usePlanosConta();

  if (pessoasQ.isSuccess && planosQ.isSuccess) {
    return <ContaFormConteudo {...props} pessoas={pessoasQ.data} planos={planosQ.data} />;
  }
  const erro = pessoasQ.error ?? planosQ.error;
  return (
    <div className="modal-backdrop">
      <div className="modal-panel">
        <div className="modal-header">
          <h4>Lançamento de título</h4>
          <button type="button" className="btn btn-sm btn-outline" onClick={props.onClose}>
            ✕
          </button>
        </div>
        <div className="modal-body">
          {erro ? (
            <ErrorBanner error={erro} onClose={props.onClose} />
          ) : (
            <div className="skeleton-row" />
          )}
        </div>
      </div>
    </div>
  );
};

const ContaFormConteudo: React.FC<Props & { pessoas: Pessoa[]; planos: PlanoConta[] }> = ({
  config,
  titulo,
  onClose,
  pessoas,
  planos,
}) => {
  const salvar = useSalvarTitulo(config.natureza);

  // RN04: só pessoas do papel certo; RN01: contas consolidadas não recebem título.
  const pessoasValidas = pessoas.filter((p) => config.tiposPessoa.includes(p.tipo));
  const planosValidos = planos.filter((p) => !p.consolidada);

  const valoresIniciais = useMemo<Partial<ContaForm>>(
    () =>
      titulo
        ? {
            numeroTitulo: titulo.numeroTitulo,
            pessoaId: titulo.pessoa.id,
            planoContaId: titulo.planoConta.id,
            tipoTitulo: titulo.tipoTitulo,
            valor: titulo.valor,
            emissao: titulo.emissao,
            vencimento: titulo.vencimento,
            desconto: titulo.desconto,
            validadeDesc: titulo.validadeDesc ?? '',
            acrescimo: titulo.acrescimo,
            observacao: titulo.observacao ?? '',
          }
        : {
            numeroTitulo: `${config.prefixoTitulo}-${Math.floor(1000 + Math.random() * 9000)}`,
            pessoaId: 0,
            planoContaId: 0,
            tipoTitulo: 'DUPLICATA',
            emissao: getTodayISO(),
            vencimento: getTodayISO(),
            validadeDesc: '',
            observacao: '',
          },
    [titulo, config.prefixoTitulo]
  );

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ContaForm>({ resolver: zodResolver(contaSchema), defaultValues: valoresIniciais });

  const enviar = handleSubmit((f) => {
    salvar.mutate(
      {
        id: titulo?.id,
        dados: {
          numeroTitulo: f.numeroTitulo,
          pessoa: { id: f.pessoaId },
          planoConta: { id: f.planoContaId },
          tipoTitulo: f.tipoTitulo,
          valor: f.valor,
          emissao: f.emissao,
          vencimento: f.vencimento,
          desconto: f.desconto ?? 0,
          validadeDesc: f.validadeDesc || null,
          acrescimo: f.acrescimo ?? 0,
          observacao: f.observacao || undefined,
        },
      },
      { onSuccess: onClose }
    );
  });

  return (
    <div className="modal-backdrop">
      <div className="modal-panel">
        <div className="modal-header">
          <h4>{titulo ? `Editar título ${titulo.numeroTitulo}` : `Lançar ${config.titulo.replace('Contas', 'Conta')}`}</h4>
          <button type="button" className="btn btn-sm btn-outline" onClick={onClose}>
            ✕
          </button>
        </div>
        <form onSubmit={enviar} noValidate>
          <div className="modal-body">
            <ErrorBanner error={salvar.error} onClose={() => salvar.reset()} />

            {(pessoasValidas.length === 0 || planosValidos.length === 0) && (
              <div className="alert alert-warning">
                <span>
                  ⚠️ Para lançar um título é preciso ter um <strong>{config.papelPessoa.toLowerCase()}</strong> e uma
                  conta <strong>não consolidada</strong> do plano de contas cadastrados (RN01 e RN04).
                </span>
              </div>
            )}

            <div className="form-grid">
              <Campo label="Número do Título *" erro={errors.numeroTitulo}>
                <input className="input-control" maxLength={20} {...register('numeroTitulo')} />
              </Campo>

              <Campo label="Tipo de Título *" erro={errors.tipoTitulo}>
                <select className="select-control" {...register('tipoTitulo')}>
                  {TIPOS_TITULO.map((t) => (
                    <option key={t} value={t}>
                      {formatTipoTitulo(t)}
                    </option>
                  ))}
                </select>
              </Campo>

              <Campo label={`${config.papelPessoa} *`} erro={errors.pessoaId}>
                <select className="select-control" {...register('pessoaId')}>
                  <option value={0} disabled>
                    Selecione...
                  </option>
                  {pessoasValidas.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.nome} ({p.documento})
                    </option>
                  ))}
                </select>
              </Campo>

              <Campo label="Plano de Conta *" erro={errors.planoContaId} dica="Contas consolidadas só agrupam outras.">
                <select className="select-control" {...register('planoContaId')}>
                  <option value={0} disabled>
                    Selecione...
                  </option>
                  {planosValidos.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.codigo} - {p.descricao}
                    </option>
                  ))}
                </select>
              </Campo>

              <Campo label="Valor do Título (R$) *" erro={errors.valor}>
                <input type="number" step="0.01" className="input-control" placeholder="0,00" {...register('valor')} />
              </Campo>

              <Campo label="Data de Emissão *" erro={errors.emissao}>
                <input type="date" className="input-control" {...register('emissao')} />
              </Campo>

              <Campo label="Data de Vencimento *" erro={errors.vencimento}>
                <input type="date" className="input-control" {...register('vencimento')} />
              </Campo>

              <Campo label="Desconto (R$)" erro={errors.desconto}>
                <input type="number" step="0.01" className="input-control" placeholder="0,00" {...register('desconto')} />
              </Campo>

              <Campo label="Desconto válido até" erro={errors.validadeDesc} dica="RN05: depois desta data, sem desconto.">
                <input type="date" className="input-control" {...register('validadeDesc')} />
              </Campo>

              <Campo label="Acréscimo por atraso (R$)" erro={errors.acrescimo}>
                <input type="number" step="0.01" className="input-control" placeholder="0,00" {...register('acrescimo')} />
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
            <button type="submit" className="btn btn-primary" disabled={salvar.isPending}>
              {salvar.isPending ? 'Salvando...' : 'Salvar Título'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
