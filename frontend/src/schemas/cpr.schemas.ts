import { z } from 'zod';

/**
 * Schemas Zod dos formulários, usados com React Hook Form via zodResolver.
 * Espelham as validações dos serviços (Bean Validation + regras de negócio),
 * para o usuário ver o erro no campo antes de a requisição sair.
 */

const apenasDigitos = (v: string) => v.replace(/\D/g, '');
const vazioParaUndefined = (v: unknown) => (v === '' || v === null ? undefined : v);

const dataISO = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Informe uma data válida.');
const textoOpcional = (max: number) => z.string().max(max, `Máximo de ${max} caracteres.`).optional();
const dinheiroOpcional = z.preprocess(
  vazioParaUndefined,
  z.coerce.number({ invalid_type_error: 'Informe um número.' }).min(0, 'Não pode ser negativo.').optional()
);

// ---------- Pessoa ----------

export const pessoaSchema = z.object({
  tipo: z.enum(['CLIENTE', 'FORNECEDOR', 'AMBOS']),
  nome: z.string().trim().min(3, 'O nome deve ter ao menos 3 caracteres.').max(100),
  documento: z
    .string()
    .refine((d) => [11, 14].includes(apenasDigitos(d).length), 'Informe um CPF (11) ou CNPJ (14 dígitos).'),
  inscricaoEstadual: textoOpcional(15),
  endereco: textoOpcional(120),
  cidade: textoOpcional(50),
  uf: z
    .string()
    .trim()
    .refine((v) => v === '' || /^[A-Za-z]{2}$/.test(v), 'UF deve ter 2 letras.')
    .optional(),
  cep: z
    .string()
    .refine((v) => v === '' || apenasDigitos(v).length === 8, 'CEP deve ter 8 dígitos.')
    .optional(),
  telefone: textoOpcional(20),
  email: z.union([z.literal(''), z.string().email('E-mail inválido.')]).optional(),
  contato: textoOpcional(50),
});
export type PessoaForm = z.infer<typeof pessoaSchema>;

// ---------- Plano de contas ----------

export const planoContaSchema = z.object({
  codigo: z
    .string()
    .trim()
    .regex(/^\d+(\.\d+)*$/, 'Use o formato 1, 1.1, 1.1.01 ...'),
  descricao: z.string().trim().min(3, 'A descrição deve ter ao menos 3 caracteres.').max(60),
  superiorId: z.coerce.number().int().min(0), // 0 = sem conta superior
  consolidada: z.enum(['true', 'false']),
});
export type PlanoContaForm = z.infer<typeof planoContaSchema>;

// ---------- Título (conta a pagar / receber) ----------

export const contaSchema = z
  .object({
    numeroTitulo: z.string().trim().min(1, 'Informe o número do título.').max(20, 'Máximo de 20 caracteres.'),
    pessoaId: z.coerce.number().int().positive('Selecione a pessoa.'),
    planoContaId: z.coerce.number().int().positive('Selecione a conta do plano.'),
    tipoTitulo: z.enum(['DUPLICATA', 'NOTA_PROMISSORIA', 'PIX', 'CARTAO', 'OUTROS']),
    valor: z.coerce
      .number({ invalid_type_error: 'Informe o valor.' })
      .positive('O valor deve ser maior que zero.'),
    emissao: dataISO,
    vencimento: dataISO,
    desconto: dinheiroOpcional,
    validadeDesc: z.union([z.literal(''), dataISO]).optional(),
    acrescimo: dinheiroOpcional,
    observacao: textoOpcional(255),
  })
  .refine((c) => c.vencimento >= c.emissao, {
    path: ['vencimento'],
    message: 'O vencimento não pode ser anterior à emissão.',
  })
  .refine((c) => (c.desconto ?? 0) < c.valor, {
    path: ['desconto'],
    message: 'O desconto deve ser menor que o valor do título.',
  })
  .refine((c) => !c.validadeDesc || (c.desconto ?? 0) > 0, {
    path: ['validadeDesc'],
    message: 'Informe o desconto ou deixe a validade em branco.',
  });
export type ContaForm = z.infer<typeof contaSchema>;

// ---------- Quitação ----------

export const quitacaoSchema = z.object({
  data: dataISO,
  desconto: dinheiroOpcional, // em branco = o serviço calcula pela validade (RN05)
  acrescimo: dinheiroOpcional, // em branco = o serviço aplica o acréscimo se houver atraso
});
export type QuitacaoForm = z.infer<typeof quitacaoSchema>;

// ---------- Cheque pré-datado (RN08) ----------

export const chequeSchema = z
  .object({
    contaReceberId: z.coerce.number().int().positive('Selecione o título a receber.'),
    numeroCheque: z.string().trim().min(1, 'Informe o número do cheque.').max(20),
    banco: z.string().trim().regex(/^\d{3,4}$/, 'Código do banco com 3 ou 4 dígitos (ex.: 001).'),
    agencia: z.string().trim().min(1, 'Informe a agência.').max(10),
    contaCorrente: z.string().trim().min(1, 'Informe a conta corrente.').max(15),
    emitente: textoOpcional(100),
    valor: z.coerce.number({ invalid_type_error: 'Informe o valor.' }).positive('O valor deve ser maior que zero.'),
    emissao: dataISO,
    dataDeposito: dataISO,
    observacao: textoOpcional(255),
  })
  .refine((c) => c.dataDeposito >= c.emissao, {
    path: ['dataDeposito'],
    message: 'O "bom para" não pode ser anterior à emissão.',
  });
export type ChequeForm = z.infer<typeof chequeSchema>;

// ---------- Cartório e remessa (RN09) ----------

export const cartorioSchema = z.object({
  nome: z.string().trim().min(3, 'Informe o nome do cartório.').max(80),
  contato: textoOpcional(50),
  telefone: textoOpcional(20),
  cidade: textoOpcional(50),
});
export type CartorioForm = z.infer<typeof cartorioSchema>;

export const remessaSchema = z.object({
  contaReceberId: z.coerce.number().int().positive('Selecione o título vencido.'),
  cartorioId: z.coerce.number().int().positive('Selecione o cartório.'),
  dataRemessa: dataISO,
});
export type RemessaForm = z.infer<typeof remessaSchema>;
