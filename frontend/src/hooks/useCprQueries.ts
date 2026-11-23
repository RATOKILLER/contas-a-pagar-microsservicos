import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api } from '../services/api';
import {
  CartorioRequest,
  ChequeRequest,
  MotivoBaixa,
  RemessaRequest,
  ContaRequest,
  FiltroContas,
  Natureza,
  PessoaRequest,
  PlanoContaRequest,
  QuitacaoRequest,
} from '../types/cpr';

/**
 * Chaves de cache. Toda gravação invalida `cprKeys.all`: uma quitação muda listas,
 * dashboard e relatórios ao mesmo tempo. Os relatórios são atualizados por eventos
 * (consistência eventual), por isso também são recarregados alguns segundos depois.
 */
export const cprKeys = {
  all: ['cpr'] as const,
  pessoas: () => [...cprKeys.all, 'pessoas'] as const,
  planosConta: () => [...cprKeys.all, 'planos-conta'] as const,
  titulos: (natureza: Natureza, filtro?: FiltroContas) => [...cprKeys.all, 'titulos', natureza, filtro ?? {}] as const,
  fluxoCaixa: (inicio?: string, fim?: string) => [...cprKeys.all, 'relatorios', 'fluxo', inicio, fim] as const,
  inadimplencia: (natureza: Natureza) => [...cprKeys.all, 'relatorios', 'inadimplencia', natureza] as const,
  cheques: () => [...cprKeys.all, 'cheques'] as const,
  cartorios: () => [...cprKeys.all, 'cartorios'] as const,
  remessas: () => [...cprKeys.all, 'remessas'] as const,
};

// ---------- Leituras ----------

export const usePessoas = () => useQuery({ queryKey: cprKeys.pessoas(), queryFn: api.listarPessoas });

export const usePlanosConta = () => useQuery({ queryKey: cprKeys.planosConta(), queryFn: api.listarPlanosConta });

export const useTitulos = (natureza: Natureza, filtro?: FiltroContas) =>
  useQuery({ queryKey: cprKeys.titulos(natureza, filtro), queryFn: () => api.listarTitulos(natureza, filtro) });

export const useFluxoCaixa = (inicio?: string, fim?: string) =>
  useQuery({ queryKey: cprKeys.fluxoCaixa(inicio, fim), queryFn: () => api.fluxoCaixa(inicio, fim) });

export const useInadimplencia = (natureza: Natureza) =>
  useQuery({ queryKey: cprKeys.inadimplencia(natureza), queryFn: () => api.inadimplencia(natureza) });

// ---------- Escritas ----------

function useCprMutation<TVars, TResult>(mutationFn: (vars: TVars) => Promise<TResult>, mensagem: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: async () => {
      toast.success(mensagem);
      await queryClient.invalidateQueries({ queryKey: cprKeys.all });
      // Relatórios chegam por evento (RabbitMQ): recarrega de novo quando o evento já foi processado.
      setTimeout(() => void queryClient.invalidateQueries({ queryKey: [...cprKeys.all, 'relatorios'] }), 3000);
    },
    // O erro é exibido pelo próprio formulário (com os campos), não por toast.
  });
}

export const useSalvarPessoa = () =>
  useCprMutation(
    ({ id, dados }: { id?: number; dados: PessoaRequest }) =>
      id ? api.atualizarPessoa(id, dados) : api.criarPessoa(dados),
    'Pessoa salva.'
  );

export const useExcluirPessoa = () => useCprMutation((id: number) => api.excluirPessoa(id), 'Pessoa excluída.');

export const useSalvarPlanoConta = () =>
  useCprMutation(
    ({ id, dados }: { id?: number; dados: PlanoContaRequest }) =>
      id ? api.atualizarPlanoConta(id, dados) : api.criarPlanoConta(dados),
    'Conta do plano salva.'
  );

export const useExcluirPlanoConta = () =>
  useCprMutation((id: number) => api.excluirPlanoConta(id), 'Conta do plano excluída.');

export const useSalvarTitulo = (natureza: Natureza) =>
  useCprMutation(
    ({ id, dados }: { id?: number; dados: ContaRequest }) =>
      id ? api.alterarTitulo(natureza, id, dados) : api.lancarTitulo(natureza, dados),
    'Título salvo.'
  );

export const useCancelarTitulo = (natureza: Natureza) =>
  useCprMutation((id: number) => api.cancelarTitulo(natureza, id), 'Título cancelado.');

export const useQuitarTitulo = (natureza: Natureza) =>
  useCprMutation(
    ({ id, dados, chave }: { id: number; dados: QuitacaoRequest; chave: string }) =>
      api.quitarTitulo(natureza, id, dados, chave),
    natureza === 'PAGAR' ? 'Pagamento registrado.' : 'Recebimento registrado.'
  );

// ---------- Cheques pré-datados e cartório (RN08 / RN09) ----------

export const useCheques = () => useQuery({ queryKey: cprKeys.cheques(), queryFn: () => api.listarCheques() });
export const useCartorios = () => useQuery({ queryKey: cprKeys.cartorios(), queryFn: api.listarCartorios });
export const useRemessas = () => useQuery({ queryKey: cprKeys.remessas(), queryFn: () => api.listarRemessas() });

export const useRegistrarCheque = () => useCprMutation((c: ChequeRequest) => api.registrarCheque(c), 'Cheque registrado.');
export const useDepositarCheque = () =>
  useCprMutation(({ id, data }: { id: number; data: string }) => api.depositarCheque(id, data), 'Cheque depositado.');
export const useDevolverCheque = () => useCprMutation((id: number) => api.devolverCheque(id), 'Devolução registrada.');
export const useCancelarCheque = () => useCprMutation((id: number) => api.cancelarCheque(id), 'Cheque cancelado.');

export const useSalvarCartorio = () =>
  useCprMutation(
    ({ id, dados }: { id?: number; dados: CartorioRequest }) => api.salvarCartorio(dados, id),
    'Cartório salvo.'
  );
export const useExcluirCartorio = () => useCprMutation((id: number) => api.excluirCartorio(id), 'Cartório excluído.');
export const useEnviarRemessa = () =>
  useCprMutation((r: RemessaRequest) => api.enviarRemessa(r), 'Título enviado a protesto.');
export const useProtestarRemessa = () =>
  useCprMutation(({ id, data }: { id: number; data: string }) => api.protestarRemessa(id, data), 'Protesto registrado.');
export const useBaixarRemessa = () =>
  useCprMutation(
    ({ id, data, motivo }: { id: number; data: string; motivo: MotivoBaixa }) => api.baixarRemessa(id, data, motivo),
    'Baixa registrada.'
  );
