-- Cliente.db e Fornecedor.db do livro (cap. 5) unificados numa tabela, com o tipo explícito.
CREATE TABLE pessoa (
    id                 BIGSERIAL PRIMARY KEY,
    tipo               VARCHAR(10)  NOT NULL CHECK (tipo IN ('CLIENTE', 'FORNECEDOR', 'AMBOS')),
    nome               VARCHAR(100) NOT NULL,
    documento          VARCHAR(14)  NOT NULL UNIQUE,   -- CPF (11) ou CNPJ/CGC (14), só dígitos
    inscricao_estadual VARCHAR(15),
    endereco           VARCHAR(120),
    cidade             VARCHAR(50),
    uf                 VARCHAR(2),
    cep                VARCHAR(8),
    telefone           VARCHAR(20),
    email              VARCHAR(80),
    contato            VARCHAR(50),
    ativo              BOOLEAN      NOT NULL DEFAULT TRUE,  -- exclusão lógica: títulos antigos seguem consultáveis
    criado_em          TIMESTAMP    NOT NULL DEFAULT now(),
    atualizado_em      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX ix_pessoa_nome ON pessoa (lower(nome));
