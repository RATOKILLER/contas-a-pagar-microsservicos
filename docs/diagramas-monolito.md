# Diagramas — Arquitetura Monolítica (etapa 1)

Versão monolítica: repositório https://github.com/guilherme22112/contas-a-pagar
(Spring Boot 3.3 · Java 21 · SQLite · React). Pacote base `br.ifgoiano.cpr`.

## 1. Diagrama de classes

```mermaid
classDiagram
    direction TB
    class Pessoa {
        Long id
        TipoPessoa tipo
        String nome
        String documento
        String inscricaoEstadual
        String endereco, cidade, uf, cep
        String telefone, email, contato
        LocalDateTime criadoEm
    }
    class PlanoConta {
        Long id
        String codigo
        String descricao
        PlanoConta superior
        Boolean consolidada
    }
    class ContaPagar {
        Long id
        String numeroTitulo
        Pessoa pessoa
        PlanoConta planoConta
        TipoTitulo tipoTitulo
        SituacaoConta situacao
        BigDecimal valor, desconto, acrescimo
        LocalDate emissao, vencimento, validadeDesc
        LocalDate dataPagamento
        BigDecimal valorPago
    }
    class ContaReceber {
        mesmos campos de ContaPagar
        LocalDate dataRecebimento
        BigDecimal valorRecebido
    }
    class ContaPagarService {
        listar(inicio, fim, situacao)
        lancar(conta)
        atualizar(id, conta)
        excluir(id)
        quitar(id, data, desconto, acrescimo)
    }
    class ContaReceberService
    class PessoaService {
        listarTodas()
        buscarPorId(id)
        salvar() atualizar() excluir()
    }
    class PlanoContaService
    class ContaPagarController
    class ApiExceptionHandler {
        422 NegocioException
        404 EntityNotFoundException
        400 MethodArgumentNotValidException
        409 DataIntegrityViolationException
    }
    class TipoPessoa {
        <<enumeration>>
        CLIENTE
        FORNECEDOR
        AMBOS
    }
    class TipoTitulo {
        <<enumeration>>
        DUPLICATA
        NOTA_PROMISSORIA
        PIX
        CARTAO
        OUTROS
    }
    class SituacaoConta {
        <<enumeration>>
        ABERTA
        PAGA
        RECEBIDA
        VENCIDA
        CANCELADA
    }

    ContaPagar --> "1" Pessoa : @ManyToOne
    ContaPagar --> "1" PlanoConta : @ManyToOne
    ContaReceber --> "1" Pessoa : @ManyToOne
    ContaReceber --> "1" PlanoConta : @ManyToOne
    PlanoConta --> "0..1" PlanoConta : superior
    ContaPagarController --> ContaPagarService
    ContaPagarService --> PessoaService : chamada de método
    ContaPagarService --> PlanoContaService : chamada de método
    ContaReceberService --> PessoaService
    ContaReceberService --> PlanoContaService
```

## 2. Diagrama de componentes

```mermaid
flowchart LR
    subgraph Navegador
        FE["Front-end React<br/>(Vite :5173)"]
    end
    subgraph JVM["Processo único · Spring Boot :8080"]
        API["API REST<br/>(controllers)"]
        DOC["springdoc OpenAPI<br/>/swagger-ui"]
        MP["Módulo Pessoas"]
        MC["Módulo Plano de Contas"]
        MCP["Módulo Contas a Pagar"]
        MCR["Módulo Contas a Receber"]
        SH["Shared<br/>exceções · DTOs · config"]
        JPA["Spring Data JPA / Hibernate"]
    end
    DB[("SQLite<br/>cpr_database.sqlite")]

    FE -- "HTTP JSON /api/**" --> API
    API --> MP & MC & MCP & MCR
    MCP --> MP & MC
    MCR --> MP & MC
    MP & MC & MCP & MCR --> SH
    MP & MC & MCP & MCR --> JPA --> DB
```

## 3. Diagrama de pacotes

```mermaid
flowchart TB
    subgraph root["br.ifgoiano.cpr"]
        APP["CprApplication"]
        subgraph pessoa["pessoa"]
            P1["Pessoa · PessoaController · PessoaService · PessoaRepository"]
        end
        subgraph planoconta["planoconta"]
            P2["PlanoConta · PlanoContaController · PlanoContaService · PlanoContaRepository"]
        end
        subgraph contapagar["contapagar"]
            P3["ContaPagar · ContaPagarController · ContaPagarService · ContaPagarRepository"]
        end
        subgraph contareceber["contareceber"]
            P4["ContaReceber · ContaReceberController · ContaReceberService · ContaReceberRepository"]
        end
        subgraph shared["shared"]
            P5["config (OpenApiConfig) · dto (ErroResposta, QuitacaoRequest)<br/>enums (TipoPessoa, TipoTitulo, SituacaoConta)<br/>exception (ApiExceptionHandler, NegocioException)"]
        end
    end
    contapagar --> pessoa
    contapagar --> planoconta
    contareceber --> pessoa
    contareceber --> planoconta
    pessoa & planoconta & contapagar & contareceber --> shared
```

O pacote é organizado **por domínio**: cada pasta de primeiro nível virou um microsserviço na etapa 2.

## 4. Diagrama de casos de uso

```mermaid
flowchart LR
    F(["👤 Operador financeiro"])
    subgraph Sistema["Sistema CPR (monolítico)"]
        UC1(["Manter pessoas"])
        UC2(["Manter plano de contas"])
        UC3(["Lançar / alterar / excluir título a pagar"])
        UC4(["Lançar / alterar / excluir título a receber"])
        UC5(["Registrar pagamento"])
        UC6(["Registrar recebimento"])
        UC7(["Consultar títulos por período e situação"])
        UC8(["Ver dashboard"])
    end
    F --- UC1 & UC2 & UC3 & UC4 & UC5 & UC6 & UC7 & UC8
    UC3 -. "«include» validar pessoa e plano" .-> UC1
    UC4 -. "«include»" .-> UC1
    UC5 -. "«extend» aplicar desconto/acréscimo" .-> UC3
    UC6 -. "«extend»" .-> UC4
```

## 5. Diagrama de sequência — lançar e pagar um título

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuário
    participant FE as Front-end
    participant C as ContaPagarController
    participant S as ContaPagarService
    participant PS as PessoaService
    participant PL as PlanoContaService
    participant R as ContaPagarRepository
    participant DB as SQLite

    U->>FE: Preenche e salva o título
    FE->>C: POST /api/contas-pagar
    C->>S: lancar(conta)
    S->>PS: buscarPorId(pessoaId)
    PS->>DB: SELECT pessoa
    S->>PL: buscarPorId(planoId)
    PL->>DB: SELECT plano_conta
    S->>R: save(conta) [situacao = ABERTA]
    R->>DB: INSERT conta_pagar (FK pessoa_id, plano_conta_id)
    C-->>FE: 201 Created
    U->>FE: Quitar
    FE->>C: POST /api/contas-pagar/{id}/pagamento
    C->>S: quitar(id, data, desconto, acrescimo)
    S->>S: valida validade do desconto · calcula valor efetivo
    S->>R: save(conta) [situacao = PAGA]
    C-->>FE: 200 OK
```

## 6. DER do banco de dados (SQLite)

```mermaid
erDiagram
    PESSOA ||--o{ CONTA_PAGAR : "pessoa_id (FK)"
    PESSOA ||--o{ CONTA_RECEBER : "pessoa_id (FK)"
    PLANO_CONTA ||--o{ CONTA_PAGAR : "plano_conta_id (FK)"
    PLANO_CONTA ||--o{ CONTA_RECEBER : "plano_conta_id (FK)"
    PLANO_CONTA |o--o{ PLANO_CONTA : "superior_id"

    PESSOA {
        INTEGER id PK
        VARCHAR tipo
        VARCHAR nome
        VARCHAR documento UK
        VARCHAR inscricao_est
        VARCHAR endereco
        VARCHAR cidade
        CHAR uf
        VARCHAR cep
        VARCHAR telefone
        VARCHAR email
        VARCHAR contato
        DATETIME criado_em
    }
    PLANO_CONTA {
        INTEGER id PK
        VARCHAR codigo UK
        VARCHAR descricao
        INTEGER superior_id FK
        BOOLEAN consolidada
    }
    CONTA_PAGAR {
        INTEGER id PK
        VARCHAR numero_titulo UK
        INTEGER pessoa_id FK
        INTEGER plano_conta_id FK
        VARCHAR tipo_titulo
        VARCHAR situacao
        NUMERIC valor
        DATE emissao
        DATE vencimento
        NUMERIC desconto
        DATE validade_desc
        NUMERIC acrescimo
        DATE data_pagamento
        NUMERIC valor_pago
        VARCHAR observacao
    }
    CONTA_RECEBER {
        INTEGER id PK
        VARCHAR numero_titulo UK
        INTEGER pessoa_id FK
        INTEGER plano_conta_id FK
        VARCHAR tipo_titulo
        VARCHAR situacao
        NUMERIC valor
        DATE emissao
        DATE vencimento
        NUMERIC desconto
        DATE validade_desc
        NUMERIC acrescimo
        DATE data_recebimento
        NUMERIC valor_recebido
        VARCHAR observacao
    }
```
