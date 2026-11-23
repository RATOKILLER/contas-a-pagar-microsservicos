-- RN08 e RN09 do livro (cap. 3, p. 61-62; cap. 5, p. 73): ChequePre.db, Cartorio.db e RemessaCartorio.db.
-- Ficam no contas-receber-service porque só existem para títulos a receber.

-- Cartório.db: precisa estar cadastrado antes de qualquer remessa (RN09).
CREATE TABLE cartorio (
    id            BIGSERIAL PRIMARY KEY,
    nome          VARCHAR(80)  NOT NULL UNIQUE,
    contato       VARCHAR(50),
    telefone      VARCHAR(20),
    cidade        VARCHAR(50),
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em     TIMESTAMP    NOT NULL DEFAULT now()
);

-- ChequePre.db: cheque pré-datado recebido como garantia de um título (RN08).
-- No livro o cheque se ligava ao cliente pelo CPF/CGC; aqui se liga ao título (que já tem o cliente).
CREATE TABLE cheque_pre_datado (
    id                BIGSERIAL PRIMARY KEY,
    conta_receber_id  BIGINT         NOT NULL REFERENCES conta_receber (id),
    numero_cheque     VARCHAR(20)    NOT NULL,
    banco             VARCHAR(4)     NOT NULL,
    agencia           VARCHAR(10)    NOT NULL,
    conta_corrente    VARCHAR(15)    NOT NULL,
    emitente          VARCHAR(100)   NOT NULL,
    valor             NUMERIC(14, 2) NOT NULL CHECK (valor > 0),
    emissao           DATE           NOT NULL,
    data_deposito     DATE           NOT NULL,   -- "bom para": antes desta data o cheque não pode ser depositado
    data_depositado   DATE,
    situacao          VARCHAR(12)    NOT NULL DEFAULT 'A_DEPOSITAR'
                      CHECK (situacao IN ('A_DEPOSITAR', 'DEPOSITADO', 'DEVOLVIDO', 'CANCELADO')),
    observacao        VARCHAR(255),
    criado_em         TIMESTAMP      NOT NULL DEFAULT now(),
    CHECK (data_deposito >= emissao),
    UNIQUE (banco, agencia, conta_corrente, numero_cheque)
);
CREATE INDEX ix_cheque_deposito ON cheque_pre_datado (data_deposito, situacao);
CREATE INDEX ix_cheque_conta ON cheque_pre_datado (conta_receber_id);

-- RemessaCartorio.db: título vencido e não recebido enviado a protesto (RN09).
CREATE TABLE remessa_cartorio (
    id                BIGSERIAL PRIMARY KEY,
    conta_receber_id  BIGINT       NOT NULL REFERENCES conta_receber (id),
    cartorio_id       BIGINT       NOT NULL REFERENCES cartorio (id),
    data_remessa      DATE         NOT NULL,
    data_protesto     DATE,
    data_baixa        DATE,
    motivo_baixa      VARCHAR(20)  CHECK (motivo_baixa IN ('PAGO', 'DESISTENCIA')),
    situacao          VARCHAR(12)  NOT NULL DEFAULT 'ENVIADA'
                      CHECK (situacao IN ('ENVIADA', 'PROTESTADA', 'BAIXADA')),
    criado_em         TIMESTAMP    NOT NULL DEFAULT now(),
    CHECK (data_protesto IS NULL OR data_protesto >= data_remessa),
    CHECK (data_baixa IS NULL OR data_baixa >= data_remessa)
);
-- No máximo uma remessa em andamento por título.
CREATE UNIQUE INDEX ux_remessa_ativa ON remessa_cartorio (conta_receber_id) WHERE situacao <> 'BAIXADA';
