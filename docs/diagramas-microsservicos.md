# Diagramas — Arquitetura de Microsserviços (etapa 2)

A visão geral da arquitetura e o diagrama de comunicação estão nas seções 9 e 13 da [documentação](DOCUMENTACAO.md).

## 1. Diagrama de classes (por serviço)

Cada serviço tem as **próprias** classes; não há biblioteca compartilhada. As linhas tracejadas entre serviços são contratos de rede (REST ou evento), não referências Java.

```mermaid
classDiagram
    direction LR
    namespace pessoas_service {
        class Pessoa {
            Long id
            TipoPessoa tipo
            String nome, documento
            String uf, cep, email
            boolean ativo
        }
        class PessoaService {
            listar(tipo, nome)
            buscarAtiva(id)
            cadastrar() alterar() inativar()
        }
        class PessoaAtualizadaEvento {
            UUID eventoId
            Long pessoaId
            String nome, documento, tipo
            boolean ativo
        }
    }
    namespace categorias_service {
        class PlanoConta {
            Long id
            String codigo, descricao
            PlanoConta superior
            boolean consolidada, ativa
        }
        class PlanoContaService {
            listar() buscarAtiva(id)
            cadastrar() alterar() inativar()
        }
    }
    namespace contas_pagar_service {
        class ContaPagar {
            Long id
            String numeroTitulo
            Long pessoaId
            String pessoaNome, pessoaDocumento
            Long planoContaId
            String planoContaCodigo, planoContaDescricao
            TipoTitulo tipoTitulo
            SituacaoConta situacao
            BigDecimal valor, desconto, acrescimo, valorPago
            LocalDate emissao, vencimento, validadeDesc, dataPagamento
            Long versao
        }
        class ContaPagarService {
            listar(inicio, fim, situacao, pessoaId)
            lancar(req) alterar(id, req) cancelar(id)
            quitar(id, req, idempotencyKey)
            marcarVencidas()
        }
        class PessoaClient {
            <<Feign>>
            buscar(id) PessoaDTO
        }
        class CategoriaClient {
            <<Feign>>
            buscar(id) CategoriaDTO
        }
        class QuitacaoIdempotencia {
            String chave
            Long contaId
        }
        class OutboxEvento {
            UUID id
            String routingKey
            String payload
            LocalDateTime publicadoEm
        }
        class TituloEvento {
            UUID eventoId
            String tipo, natureza
            Long tituloId
            fotografia do título
            long versao
        }
    }
    namespace relatorios_service {
        class TituloResumo {
            String natureza
            Long tituloId
            campos do título
            long versao
        }
        class EventoProcessado {
            UUID eventoId
        }
        class ProjecaoTitulos {
            aplicar(eventoId, TituloEvento)
        }
        class RelatorioService {
            fluxoCaixa(inicio, fim)
            inadimplencia(natureza)
        }
    }
    PlanoConta --> PlanoConta : superior
    ContaPagarService --> PessoaClient
    ContaPagarService --> CategoriaClient
    ContaPagarService --> QuitacaoIdempotencia
    ContaPagarService --> OutboxEvento : grava na mesma transação
    OutboxEvento ..> TituloEvento : payload JSON
    PessoaClient ..> PessoaService : HTTP GET /api/pessoas/{id}
    CategoriaClient ..> PlanoContaService : HTTP GET /api/plano-contas/{id}
    TituloEvento ..> ProjecaoTitulos : RabbitMQ titulo.*
    PessoaAtualizadaEvento ..> ContaPagar : RabbitMQ (atualiza réplica)
    ProjecaoTitulos --> TituloResumo
    ProjecaoTitulos --> EventoProcessado
    RelatorioService --> TituloResumo
```

O `contas-receber-service` tem exatamente a mesma estrutura (`ContaReceber`, `dataRecebimento`, `valorRecebido`, rota `/recebimento`).

## 2. Diagrama de componentes

```mermaid
flowchart TB
    subgraph Cliente
        NG["frontend<br/>Nginx + React :3000"]
    end
    subgraph Plataforma
        GW["api-gateway :8080<br/>Spring Cloud Gateway<br/>Resilience4j · Retry · Swagger UI"]
        EU["discovery-server :8761<br/>Eureka"]
    end
    subgraph Negócio
        PS["pessoas-service :8081"]
        CS["categorias-service :8082"]
        CP["contas-pagar-service :8083<br/>Feign · Resilience4j · Outbox · Scheduler"]
        CR["contas-receber-service :8084<br/>Feign · Resilience4j · Outbox · Scheduler"]
        RS["relatorios-service :8085<br/>consumidor de eventos"]
    end
    subgraph Infraestrutura
        PG[("PostgreSQL 16 :5433<br/>pessoas_db · categorias_db · contas_pagar_db<br/>contas_receber_db · relatorios_db")]
        MQ{{"RabbitMQ :5672 / :15672<br/>exchange cpr.eventos · DLX cpr.eventos.dlx"}}
        ZK["Zipkin :9411"]
    end

    NG -- "/api/** (proxy)" --> GW
    GW -- "lb://" --> PS & CS & CP & CR & RS
    GW & PS & CS & CP & CR & RS -. "registro / descoberta" .-> EU
    CP & CR -- "Feign (REST)" --> PS & CS
    PS & CS & CP & CR & RS --> PG
    PS & CS & CP & CR -- publicam --> MQ
    MQ -- entrega --> CP & CR & RS
    GW & PS & CS & CP & CR & RS -. spans .-> ZK
```

## 3. Diagrama de pacotes

Todos os serviços de negócio seguem a mesma organização interna (exemplo: `contas-pagar-service`):

```mermaid
flowchart TB
    subgraph svc["br.ifgoiano.cpr.contaspagar"]
        APP["ContasPagarServiceApplication"]
        API["api<br/>ContaPagarController · ContaPagarDtos<br/>(ContaRequest, QuitacaoRequest, ContaResponse)"]
        SRV["service<br/>ContaPagarService · VencimentoAgendador"]
        DOM["domain<br/>ContaPagar · SituacaoConta · TipoTitulo<br/>ContaPagarRepository · QuitacaoIdempotencia(Repository)"]
        INT["integracao<br/>PessoaClient · CategoriaClient<br/>CadastrosFallbacks · IntegracaoConfig"]
        EVT["evento<br/>TituloEvento · Outbox · OutboxEvento<br/>OutboxRepository · OutboxPublisher · CadastrosEventosListener"]
        SH["shared<br/>ApiExceptionHandler · ConcorrenciaHandler · ErroResposta<br/>NegocioException · RecursoNaoEncontradoException<br/>ServicoIndisponivelException · OpenApiConfig · RabbitConfig"]
    end
    API --> SRV
    SRV --> DOM
    SRV --> INT
    SRV --> EVT
    EVT --> DOM
    API & SRV & INT & EVT --> SH
```

| Serviço | Pacotes |
|---|---|
| `pessoas-service` | `api` · `service` · `domain` · `evento` · `shared` |
| `categorias-service` | `api` · `service` · `domain` · `evento` · `shared` |
| `contas-pagar-service` / `contas-receber-service` | `api` · `service` · `domain` · `integracao` · `evento` · `shared` |
| `relatorios-service` | `api` · `domain` · `evento` · `shared` |
| `api-gateway` | `gateway` (configuração de rotas em YAML + `FallbackController`) |
| `discovery-server` | `discovery` |

## 4. Diagrama de casos de uso

```mermaid
flowchart LR
    F(["👤 Operador financeiro"])
    G(["👔 Gestor"])
    T(["⏱️ Agendador"])
    subgraph Sistema["Sistema CPR (microsserviços)"]
        subgraph pessoas["pessoas-service"]
            UC1(["Manter pessoas"])
        end
        subgraph categorias["categorias-service"]
            UC2(["Manter plano de contas"])
        end
        subgraph pagar["contas-pagar-service"]
            UC3(["Lançar / alterar / cancelar título a pagar"])
            UC5(["Registrar pagamento"])
            UC9a(["Marcar títulos vencidos"])
        end
        subgraph receber["contas-receber-service"]
            UC4(["Lançar / alterar / cancelar título a receber"])
            UC6(["Registrar recebimento"])
            UC9b(["Marcar títulos vencidos"])
        end
        subgraph relatorios["relatorios-service"]
            UC7(["Consultar fluxo de caixa"])
            UC8(["Consultar inadimplência"])
        end
    end
    F --- UC1 & UC2 & UC3 & UC4 & UC5 & UC6
    G --- UC7 & UC8
    T --- UC9a & UC9b
    UC3 -. "«include» validar pessoa (REST)" .-> UC1
    UC3 -. "«include» validar categoria (REST)" .-> UC2
    UC4 -. "«include»" .-> UC1
    UC4 -. "«include»" .-> UC2
    UC5 -. "«include» evento titulo.quitado" .-> UC7
    UC6 -. "«include»" .-> UC7
```

## 5. Diagramas de sequência

### 5.1 Lançamento e quitação (caminho feliz, com idempotência)

Ver seção 13 da [documentação](DOCUMENTACAO.md#13-comunicação-entre-os-serviços).

### 5.2 Lançamento com o serviço de pessoas fora do ar

```mermaid
sequenceDiagram
    autonumber
    participant FE as Front-end
    participant GW as API Gateway
    participant CP as contas-pagar-service
    participant CB as Circuit breaker (Resilience4j)
    participant PS as pessoas-service ✖

    FE->>GW: POST /api/contas-pagar
    GW->>CP: encaminha
    CP->>CB: PessoaClient.buscar(1)
    CB->>PS: GET /api/pessoas/1
    PS--xCB: conexão recusada / timeout 3 s
    CB->>CB: registra falha (após 50% de 10 chamadas → circuito ABERTO por 15 s)
    CB-->>CP: fallback → ServicoIndisponivelException
    CP->>CP: rollback (nada gravado, nenhum evento)
    CP-->>FE: 503 SERVICO_INDISPONIVEL "O serviço de Pessoas está indisponível..."
    Note over FE,CP: GET /api/contas-pagar continua 200 (usa a réplica local do nome)
```

### 5.3 Entrega de evento com o RabbitMQ fora do ar (outbox)

```mermaid
sequenceDiagram
    autonumber
    participant CP as contas-pagar-service
    participant DB as contas_pagar_db
    participant OP as OutboxPublisher (a cada 2 s)
    participant MQ as RabbitMQ
    participant RS as relatorios-service

    CP->>DB: BEGIN · UPDATE conta_pagar (PAGA) · INSERT outbox_evento · COMMIT
    OP->>DB: SELECT ... FOR UPDATE SKIP LOCKED (pendentes)
    OP->>MQ: send(titulo.quitado)
    MQ--xOP: broker fora do ar
    OP->>DB: tentativas + 1 (evento continua pendente)
    Note over MQ: broker volta
    OP->>MQ: send(titulo.quitado, messageId = eventoId)
    OP->>DB: publicado_em = agora
    MQ->>RS: entrega
    RS->>RS: eventoId novo? versão maior? → atualiza titulo_resumo
```

## 6. DER — um banco por serviço

Não há chaves estrangeiras **entre** bancos. `pessoa_id` e `plano_conta_id` nos títulos são referências lógicas, validadas por REST na gravação e acompanhadas de uma réplica de nome e código.

```mermaid
erDiagram
    PESSOA {
        BIGSERIAL id PK
        VARCHAR tipo
        VARCHAR nome
        VARCHAR documento UK
        VARCHAR inscricao_estadual
        VARCHAR endereco
        VARCHAR cidade
        VARCHAR uf
        VARCHAR cep
        VARCHAR telefone
        VARCHAR email
        VARCHAR contato
        BOOLEAN ativo
        TIMESTAMP criado_em
        TIMESTAMP atualizado_em
    }
```
<sub>pessoas_db</sub>

```mermaid
erDiagram
    PLANO_CONTA |o--o{ PLANO_CONTA : "superior_id"
    PLANO_CONTA {
        BIGSERIAL id PK
        VARCHAR codigo UK
        VARCHAR descricao
        BIGINT superior_id FK
        BOOLEAN consolidada
        BOOLEAN ativa
        TIMESTAMP criado_em
        TIMESTAMP atualizado_em
    }
```
<sub>categorias_db</sub>

```mermaid
erDiagram
    CONTA_PAGAR ||--o{ QUITACAO_IDEMPOTENCIA : "conta_id"
    CONTA_PAGAR {
        BIGSERIAL id PK
        VARCHAR numero_titulo UK
        BIGINT pessoa_id "ref. lógica → pessoas_db"
        VARCHAR pessoa_nome "réplica"
        VARCHAR pessoa_documento "réplica"
        BIGINT plano_conta_id "ref. lógica → categorias_db"
        VARCHAR plano_conta_codigo "réplica"
        VARCHAR plano_conta_descricao "réplica"
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
        BIGINT versao "trava otimista"
        TIMESTAMP criado_em
        TIMESTAMP atualizado_em
    }
    QUITACAO_IDEMPOTENCIA {
        VARCHAR chave PK
        BIGINT conta_id FK
        TIMESTAMP criado_em
    }
    OUTBOX_EVENTO {
        UUID id PK
        VARCHAR routing_key
        TEXT payload
        TIMESTAMP criado_em
        TIMESTAMP publicado_em
        INT tentativas
    }
```
<sub>contas_pagar_db — o `contas_receber_db` é idêntico, com `conta_receber`, `data_recebimento` e `valor_recebido`.</sub>

```mermaid
erDiagram
    TITULO_RESUMO {
        VARCHAR natureza PK "PAGAR | RECEBER"
        BIGINT titulo_id PK
        VARCHAR numero_titulo
        BIGINT pessoa_id
        VARCHAR pessoa_nome
        BIGINT plano_conta_id
        VARCHAR plano_conta_codigo
        VARCHAR plano_conta_descricao
        NUMERIC valor
        DATE emissao
        DATE vencimento
        VARCHAR situacao
        DATE data_quitacao
        NUMERIC valor_quitado
        BIGINT versao
        TIMESTAMP atualizado_em
    }
    EVENTO_PROCESSADO {
        UUID evento_id PK
        TIMESTAMP recebido_em
    }
```
<sub>relatorios_db — modelo de leitura montado só por eventos.</sub>
