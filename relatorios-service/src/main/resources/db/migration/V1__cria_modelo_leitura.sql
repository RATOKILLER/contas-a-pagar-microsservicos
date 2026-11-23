-- Modelo de leitura do relatorios-service: uma linha por título (a pagar ou a receber),
-- montada só a partir dos eventos. Nenhuma tabela de outro serviço é lida.
CREATE TABLE titulo_resumo (
    natureza              VARCHAR(7)     NOT NULL CHECK (natureza IN ('PAGAR', 'RECEBER')),
    titulo_id             BIGINT         NOT NULL,
    numero_titulo         VARCHAR(20)    NOT NULL,
    pessoa_id             BIGINT         NOT NULL,
    pessoa_nome           VARCHAR(100)   NOT NULL,
    plano_conta_id        BIGINT         NOT NULL,
    plano_conta_codigo    VARCHAR(18)    NOT NULL,
    plano_conta_descricao VARCHAR(60)    NOT NULL,
    valor                 NUMERIC(14, 2) NOT NULL,
    emissao               DATE           NOT NULL,
    vencimento            DATE           NOT NULL,
    situacao              VARCHAR(10)    NOT NULL,
    data_quitacao         DATE,
    valor_quitado         NUMERIC(14, 2),
    versao                BIGINT         NOT NULL,  -- descarta evento antigo que chegue depois de um mais novo
    atualizado_em         TIMESTAMP      NOT NULL,
    PRIMARY KEY (natureza, titulo_id)
);
CREATE INDEX ix_tr_venc ON titulo_resumo (vencimento);
CREATE INDEX ix_tr_quit ON titulo_resumo (data_quitacao);

-- Consumidor idempotente: o mesmo evento entregue duas vezes é aplicado uma vez só.
CREATE TABLE evento_processado (
    evento_id   UUID      PRIMARY KEY,
    recebido_em TIMESTAMP NOT NULL DEFAULT now()
);
