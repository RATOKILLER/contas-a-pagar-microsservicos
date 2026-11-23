-- PlanoContas.db do livro (cap. 5 e 11): hierárquico, com marca de "consolidada" (RN01).
CREATE TABLE plano_conta (
    id            BIGSERIAL PRIMARY KEY,
    codigo        VARCHAR(18) NOT NULL UNIQUE,   -- 1, 1.1, 1.1.01 ...
    descricao     VARCHAR(60) NOT NULL,
    superior_id   BIGINT REFERENCES plano_conta (id),
    consolidada   BOOLEAN     NOT NULL DEFAULT FALSE,  -- agrupa outras contas e não recebe título
    ativa         BOOLEAN     NOT NULL DEFAULT TRUE,
    criado_em     TIMESTAMP   NOT NULL DEFAULT now(),
    atualizado_em TIMESTAMP   NOT NULL DEFAULT now(),
    CHECK (superior_id IS NULL OR superior_id <> id)
);

-- Carga inicial: estrutura básica de receitas e despesas (exemplo do livro, adaptado).
INSERT INTO plano_conta (codigo, descricao, superior_id, consolidada) VALUES
    ('1', 'Receitas', NULL, TRUE),
    ('2', 'Despesas', NULL, TRUE);
INSERT INTO plano_conta (codigo, descricao, superior_id, consolidada) VALUES
    ('1.1', 'Vendas de mercadorias', (SELECT id FROM plano_conta WHERE codigo = '1'), FALSE),
    ('1.2', 'Prestação de serviços', (SELECT id FROM plano_conta WHERE codigo = '1'), FALSE),
    ('2.1', 'Despesas fixas', (SELECT id FROM plano_conta WHERE codigo = '2'), TRUE),
    ('2.2', 'Fornecedores de mercadorias', (SELECT id FROM plano_conta WHERE codigo = '2'), FALSE);
INSERT INTO plano_conta (codigo, descricao, superior_id, consolidada) VALUES
    ('2.1.01', 'Água, luz e telefone', (SELECT id FROM plano_conta WHERE codigo = '2.1'), FALSE),
    ('2.1.02', 'Aluguel e condomínio', (SELECT id FROM plano_conta WHERE codigo = '2.1'), FALSE);
