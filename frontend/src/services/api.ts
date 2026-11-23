import {
  Cartorio,
  CartorioRequest,
  Cheque,
  ChequeRequest,
  MotivoBaixa,
  Remessa,
  RemessaRequest,
  SituacaoCheque,
  SituacaoRemessa,
  ContaPagar,
  ContaReceber,
  ContaRequest,
  ErroResposta,
  FiltroContas,
  FluxoCaixa,
  Inadimplencia,
  Natureza,
  Pessoa,
  PessoaRequest,
  PlanoConta,
  PlanoContaRequest,
  QuitacaoRequest,
  Titulo,
} from '../types/cpr';

/**
 * Cliente HTTP do front-end. Todas as chamadas vão para /api/** no API Gateway
 * (em desenvolvimento, o Vite faz proxy para localhost:8080; no Docker, o Nginx).
 */
export class ApiError extends Error {
  public readonly status: number;
  public readonly codigoErro: string;
  public readonly campos: { nome: string; mensagem: string }[];

  constructor(erroResposta: ErroResposta) {
    super(erroResposta.mensagem || 'Erro na comunicação com o servidor.');
    this.name = 'ApiError';
    this.status = erroResposta.status;
    this.codigoErro = erroResposta.erro;
    this.campos = erroResposta.campos ?? [];
  }
}

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(url, {
      ...options,
      headers: { 'Content-Type': 'application/json', ...(options?.headers ?? {}) },
    });
  } catch {
    throw new ApiError({
      timestamp: new Date().toISOString(),
      status: 0,
      erro: 'SEM_CONEXAO',
      mensagem: 'Não foi possível falar com o API Gateway (porta 8080). Verifique se os serviços estão rodando.',
    });
  }

  if (!response.ok) {
    let erro: ErroResposta | null = null;
    try {
      erro = (await response.json()) as ErroResposta;
    } catch {
      // resposta de erro sem corpo JSON
    }
    if (erro?.mensagem) throw new ApiError(erro);
    throw new ApiError({
      timestamp: new Date().toISOString(),
      status: response.status,
      erro: 'ERRO_HTTP',
      mensagem: `Falha na requisição (HTTP ${response.status}).`,
    });
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

function query(params: Record<string, string | undefined>): string {
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v) p.append(k, v);
  });
  const s = p.toString();
  return s ? `?${s}` : '';
}

const json = (body: unknown) => JSON.stringify(body);

/** Caminho e nomes de campos de cada serviço de contas. */
export const NATUREZAS = {
  PAGAR: { base: '/api/contas-pagar', quitacao: 'pagamento' },
  RECEBER: { base: '/api/contas-receber', quitacao: 'recebimento' },
} as const;

/** Converte a resposta de cada serviço para a visão comum {@link Titulo}. */
export function paraTitulo(c: ContaPagar | ContaReceber): Titulo {
  if ('valorPago' in c) {
    const { dataPagamento, valorPago, ...resto } = c;
    return { ...resto, dataQuitacao: dataPagamento, valorQuitado: valorPago };
  }
  const { dataRecebimento, valorRecebido, ...resto } = c;
  return { ...resto, dataQuitacao: dataRecebimento, valorQuitado: valorRecebido };
}

export const api = {
  // pessoas-service
  listarPessoas: () => request<Pessoa[]>('/api/pessoas'),
  criarPessoa: (p: PessoaRequest) => request<Pessoa>('/api/pessoas', { method: 'POST', body: json(p) }),
  atualizarPessoa: (id: number, p: PessoaRequest) =>
    request<Pessoa>(`/api/pessoas/${id}`, { method: 'PUT', body: json(p) }),
  excluirPessoa: (id: number) => request<void>(`/api/pessoas/${id}`, { method: 'DELETE' }),

  // categorias-service
  listarPlanosConta: () => request<PlanoConta[]>('/api/plano-contas'),
  criarPlanoConta: (p: PlanoContaRequest) =>
    request<PlanoConta>('/api/plano-contas', { method: 'POST', body: json(p) }),
  atualizarPlanoConta: (id: number, p: PlanoContaRequest) =>
    request<PlanoConta>(`/api/plano-contas/${id}`, { method: 'PUT', body: json(p) }),
  excluirPlanoConta: (id: number) => request<void>(`/api/plano-contas/${id}`, { method: 'DELETE' }),

  // contas-pagar-service / contas-receber-service
  listarTitulos: async (natureza: Natureza, filtro?: FiltroContas): Promise<Titulo[]> => {
    const lista = await request<(ContaPagar | ContaReceber)[]>(
      NATUREZAS[natureza].base + query({ inicio: filtro?.inicio, fim: filtro?.fim, situacao: filtro?.situacao || undefined })
    );
    return lista.map(paraTitulo);
  },
  lancarTitulo: (natureza: Natureza, c: ContaRequest) =>
    request<ContaPagar | ContaReceber>(NATUREZAS[natureza].base, { method: 'POST', body: json(c) }),
  alterarTitulo: (natureza: Natureza, id: number, c: ContaRequest) =>
    request<ContaPagar | ContaReceber>(`${NATUREZAS[natureza].base}/${id}`, { method: 'PUT', body: json(c) }),
  cancelarTitulo: (natureza: Natureza, id: number) =>
    request<void>(`${NATUREZAS[natureza].base}/${id}`, { method: 'DELETE' }),
  /**
   * Quitação idempotente: a mesma chave (gerada quando o modal abre) é reenviada em cada tentativa,
   * então um clique duplo ou uma nova tentativa após falha de rede nunca paga duas vezes.
   */
  quitarTitulo: (natureza: Natureza, id: number, payload: QuitacaoRequest, idempotencyKey: string) =>
    request<ContaPagar | ContaReceber>(`${NATUREZAS[natureza].base}/${id}/${NATUREZAS[natureza].quitacao}`, {
      method: 'POST',
      body: json(payload),
      headers: { 'Idempotency-Key': idempotencyKey },
    }),

  // contas-receber-service: cheques pré-datados (RN08)
  listarCheques: (situacao?: SituacaoCheque) => request<Cheque[]>('/api/contas-receber/cheques' + query({ situacao })),
  registrarCheque: (c: ChequeRequest) => request<Cheque>('/api/contas-receber/cheques', { method: 'POST', body: json(c) }),
  depositarCheque: (id: number, data: string) =>
    request<Cheque>(`/api/contas-receber/cheques/${id}/deposito`, { method: 'POST', body: json({ data }) }),
  devolverCheque: (id: number) =>
    request<Cheque>(`/api/contas-receber/cheques/${id}/devolucao`, { method: 'POST', body: '{}' }),
  cancelarCheque: (id: number) => request<void>(`/api/contas-receber/cheques/${id}`, { method: 'DELETE' }),

  // contas-receber-service: cartórios e remessas para protesto (RN09)
  listarCartorios: () => request<Cartorio[]>('/api/contas-receber/cartorios'),
  salvarCartorio: (c: CartorioRequest, id?: number) =>
    request<Cartorio>(`/api/contas-receber/cartorios${id ? `/${id}` : ''}`, {
      method: id ? 'PUT' : 'POST',
      body: json(c),
    }),
  excluirCartorio: (id: number) => request<void>(`/api/contas-receber/cartorios/${id}`, { method: 'DELETE' }),
  listarRemessas: (situacao?: SituacaoRemessa) =>
    request<Remessa[]>('/api/contas-receber/remessas' + query({ situacao })),
  enviarRemessa: (r: RemessaRequest) =>
    request<Remessa>('/api/contas-receber/remessas', { method: 'POST', body: json(r) }),
  protestarRemessa: (id: number, data: string) =>
    request<Remessa>(`/api/contas-receber/remessas/${id}/protesto`, { method: 'POST', body: json({ data }) }),
  baixarRemessa: (id: number, data: string, motivo: MotivoBaixa) =>
    request<Remessa>(`/api/contas-receber/remessas/${id}/baixa`, { method: 'POST', body: json({ data, motivo }) }),

  // relatorios-service
  fluxoCaixa: (inicio?: string, fim?: string) =>
    request<FluxoCaixa>('/api/relatorios/fluxo-caixa' + query({ inicio, fim })),
  inadimplencia: (natureza: Natureza) =>
    request<Inadimplencia>('/api/relatorios/inadimplencia' + query({ natureza })),
};
