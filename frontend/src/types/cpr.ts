// Tipagens espelhadas nos DTOs dos microsserviços (sem uso de 'any').

export type TipoPessoa = 'CLIENTE' | 'FORNECEDOR' | 'AMBOS';

export type SituacaoConta = 'ABERTA' | 'PAGA' | 'RECEBIDA' | 'VENCIDA' | 'CANCELADA';

export type TipoTitulo = 'DUPLICATA' | 'NOTA_PROMISSORIA' | 'PIX' | 'CARTAO' | 'OUTROS';

/** PAGAR = contas-pagar-service · RECEBER = contas-receber-service */
export type Natureza = 'PAGAR' | 'RECEBER';

// ---------- pessoas-service ----------

export interface Pessoa {
  id: number;
  tipo: TipoPessoa;
  nome: string;
  documento: string;
  inscricaoEstadual?: string | null;
  endereco?: string | null;
  cidade?: string | null;
  uf?: string | null;
  cep?: string | null;
  telefone?: string | null;
  email?: string | null;
  contato?: string | null;
  criadoEm?: string;
}

export type PessoaRequest = Omit<Pessoa, 'id' | 'criadoEm'>;

// ---------- categorias-service ----------

export interface Referencia {
  id: number;
}

export interface PlanoContaResumo {
  id: number;
  codigo: string;
  descricao: string;
}

export interface PlanoConta extends PlanoContaResumo {
  superior: PlanoContaResumo | null;
  consolidada: boolean;
}

export interface PlanoContaRequest {
  codigo: string;
  descricao: string;
  superior: Referencia | null;
  consolidada: boolean;
}

// ---------- contas-pagar-service / contas-receber-service ----------

/** Réplica guardada no próprio serviço de contas (não exige chamar o pessoas-service para listar). */
export interface PessoaResumo {
  id: number;
  nome: string;
  documento: string;
}

interface TituloBase {
  id: number;
  numeroTitulo: string;
  pessoa: PessoaResumo;
  planoConta: PlanoContaResumo;
  tipoTitulo: TipoTitulo;
  situacao: SituacaoConta;
  valor: number;
  emissao: string;
  vencimento: string;
  desconto: number;
  validadeDesc: string | null;
  acrescimo: number;
  observacao: string | null;
}

export interface ContaPagar extends TituloBase {
  dataPagamento: string | null;
  valorPago: number | null;
}

export interface ContaReceber extends TituloBase {
  dataRecebimento: string | null;
  valorRecebido: number | null;
}

/** Visão comum de um título, independente da natureza. */
export interface Titulo extends TituloBase {
  dataQuitacao: string | null;
  valorQuitado: number | null;
}

export interface ContaRequest {
  numeroTitulo: string;
  pessoa: Referencia;
  planoConta: Referencia;
  tipoTitulo: TipoTitulo;
  valor: number;
  emissao: string;
  vencimento: string;
  desconto?: number;
  validadeDesc?: string | null;
  acrescimo?: number;
  observacao?: string;
}

/** Desconto e acréscimo omitidos = o serviço calcula pela data (RN05). */
export interface QuitacaoRequest {
  data: string;
  desconto?: number;
  acrescimo?: number;
}

export interface FiltroContas {
  inicio?: string;
  fim?: string;
  situacao?: SituacaoConta | '';
}

// ---------- relatorios-service ----------

export interface MesFluxo {
  mes: string;
  previstoReceber: number;
  previstoPagar: number;
  recebido: number;
  pago: number;
  saldoPrevisto: number;
  saldoRealizado: number;
}

export interface FluxoCaixa {
  inicio: string;
  fim: string;
  meses: MesFluxo[];
  totais: MesFluxo;
}

export interface TituloAtrasado {
  natureza: Natureza;
  tituloId: number;
  numeroTitulo: string;
  pessoaNome: string;
  planoContaCodigo: string;
  vencimento: string;
  valor: number;
  diasAtraso: number;
}

export interface Inadimplencia {
  natureza: Natureza;
  dataBase: string;
  quantidade: number;
  total: number;
  titulos: TituloAtrasado[];
}

// ---------- erro padrão (todos os serviços e o gateway) ----------

export interface CampoErro {
  nome: string;
  mensagem: string;
}

export interface ErroResposta {
  timestamp: string;
  status: number;
  erro: string;
  mensagem: string;
  campos?: CampoErro[] | null;
}
