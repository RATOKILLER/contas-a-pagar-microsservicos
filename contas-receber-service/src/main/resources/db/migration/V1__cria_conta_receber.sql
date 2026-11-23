-- Recebimento.db do livro (cap. 5), com as correções do grupo:
--  * situação explícita (o livro deduzia "paga" pelo campo Pagto preenchido);
--  * pessoa e categoria pertencem a outros serviços: aqui ficam só o id e uma RÉPLICA
--    do nome/documento e do código/descrição (sem chave estrangeira entre bancos).
CREATE TABLE conta_receber (
    id                    BIGSERIAL PRIMARY KEY,
    numero_titulo         VARCHAR(20)    NOT NULL UNIQUE,
    pessoa_id             BIGINT         NOT NULL,
    pessoa_nome           VARCHAR(100)   NOT NULL,
    pessoa_documento      VARCHAR(14)    NOT NULL,
    plano_conta_id        BIGINT         NOT NULL,
    plano_conta_codigo    VARCHAR(18)    NOT NULL,
    plano_conta_descricao VARCHAR(60)    NOT NULL,
    tipo_titulo           VARCHAR(20)    NOT NULL,
    situacao              VARCHAR(10)    NOT NULL DEFAULT 'ABERTA',
    valor                 NUMERIC(14, 2) NOT NULL CHECK (valor > 0),
    emissao               DATE           NOT NULL,
    vencimento            DATE           NOT NULL,
    desconto              NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (desconto >= 0),
    validade_desc         DATE,
    acrescimo             NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (acrescimo >= 0),
    data_recebimento        DATE,
    valor_recebido            NUMERIC(14, 2),
    observacao            VARCHAR(255),
    versao                BIGINT         NOT NULL DEFAULT 0,   -- trava otimista: duas quitações simultâneas não passam
    criado_em             TIMESTAMP      NOT NULL DEFAULT now(),
    atualizado_em         TIMESTAMP      NOT NULL DEFAULT now(),
    CHECK (vencimento >= emissao),
    CHECK (situacao IN ('ABERTA', 'PAGA', 'RECEBIDA', 'VENCIDA', 'CANCELADA'))
);
CREATE INDEX ix_cr_venc ON conta_receber (vencimento, situacao);
CREATE INDEX ix_cr_pessoa ON conta_receber (pessoa_id);
CREATE INDEX ix_cr_plano ON conta_receber (plano_conta_id);

-- Idempotência da quitação: a mesma chave (header Idempotency-Key) nunca recebe duas vezes.
CREATE TABLE quitacao_idempotencia (
    chave     VARCHAR(64) PRIMARY KEY,
    conta_id  BIGINT      NOT NULL REFERENCES conta_receber (id),
    criado_em TIMESTAMP   NOT NULL DEFAULT now()
);

-- Outbox: o evento é gravado na MESMA transação do título e publicado depois no RabbitMQ.
-- Se o broker estiver fora, nada se perde: o publicador tenta de novo até conseguir.
CREATE TABLE outbox_evento (
    id           UUID        PRIMARY KEY,
    routing_key  VARCHAR(60) NOT NULL,
    payload      TEXT        NOT NULL,
    criado_em    TIMESTAMP   NOT NULL DEFAULT now(),
    publicado_em TIMESTAMP,
    tentativas   INT         NOT NULL DEFAULT 0
);
CREATE INDEX ix_outbox_pendente ON outbox_evento (criado_em) WHERE publicado_em IS NULL;
