import { Natureza, SituacaoConta, TipoPessoa, TipoTitulo } from '../../types/cpr';

/** Diferenças entre contas a pagar e a receber; o resto das telas é igual. */
export interface ConfigNatureza {
  natureza: Natureza;
  titulo: string;
  subtitulo: string;
  papelPessoa: string;
  tiposPessoa: TipoPessoa[];
  situacaoQuitada: SituacaoConta;
  acao: string;
  acaoSubstantivo: string;
  botaoQuitar: string;
  prefixoTitulo: string;
  colunaQuitado: string;
}

export const NATUREZA_CONFIG: Record<Natureza, ConfigNatureza> = {
  PAGAR: {
    natureza: 'PAGAR',
    titulo: 'Contas a Pagar',
    subtitulo: 'Obrigações financeiras, filtros por período/situação e registro de pagamento.',
    papelPessoa: 'Fornecedor',
    tiposPessoa: ['FORNECEDOR', 'AMBOS'],
    situacaoQuitada: 'PAGA',
    acao: 'Pagar',
    acaoSubstantivo: 'Pagamento',
    botaoQuitar: '💲 Quitar',
    prefixoTitulo: 'CP',
    colunaQuitado: 'Valor Pago',
  },
  RECEBER: {
    natureza: 'RECEBER',
    titulo: 'Contas a Receber',
    subtitulo: 'Títulos de clientes, filtros por período/situação e registro de recebimento.',
    papelPessoa: 'Cliente',
    tiposPessoa: ['CLIENTE', 'AMBOS'],
    situacaoQuitada: 'RECEBIDA',
    acao: 'Receber',
    acaoSubstantivo: 'Recebimento',
    botaoQuitar: '💰 Receber',
    prefixoTitulo: 'CR',
    colunaQuitado: 'Valor Recebido',
  },
};

export const TIPOS_TITULO: TipoTitulo[] = ['DUPLICATA', 'NOTA_PROMISSORIA', 'PIX', 'CARTAO', 'OUTROS'];

export const situacoesDe = (c: ConfigNatureza): SituacaoConta[] => ['ABERTA', c.situacaoQuitada, 'VENCIDA', 'CANCELADA'];
