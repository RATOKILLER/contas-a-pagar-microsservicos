# Testes realizados

Dois grupos de testes:

1. **Automatizados (62)**: rodam com `./mvnw verify` (Windows: `mvnw.cmd verify`). Os testes de integração sobem um PostgreSQL 16 real em container (Testcontainers), então é preciso ter o Docker rodando.
2. **Manuais de sistema (9 cenários)**: feitos com o `docker compose` no ar, chamando a API pelo gateway com `curl`, inclusive derrubando serviços de propósito.

Resultado da última execução: **62 testes, 0 falhas, 0 erros** (BUILD SUCCESS em todos os 7 projetos).

```
[INFO] Reactor Summary for CPR - Microsserviços (agregador) 1.0.0:
[INFO] discovery-server ................................... SUCCESS
[INFO] api-gateway ........................................ SUCCESS   (1 teste)
[INFO] pessoas-service .................................... SUCCESS   (3 testes)
[INFO] categorias-service ................................. SUCCESS   (4 testes)
[INFO] contas-pagar-service ............................... SUCCESS   (25 testes)
[INFO] contas-receber-service ............................. SUCCESS   (25 testes)
[INFO] relatorios-service ................................. SUCCESS   (4 testes)
[INFO] BUILD SUCCESS
```

---

## 1. Testes das regras de negócio (unitários)

`ContaPagarServiceTest` e `ContaReceberServiceTest` (JUnit 5 + Mockito). Os outros serviços (Feign) e o banco são simulados, e a data "hoje" é fixa em 20/11/2026. Para o recebimento, os papéis de cliente e fornecedor se invertem.

| # | Cenário | Dados de entrada | Resultado esperado | Obtido |
|---|---|---|---|---|
| U01 | Lançar título válido | Fornecedor ativo; categoria 2.2 não consolidada; valor 300 | Situação ABERTA; réplica do nome e do código gravada; evento REGISTRADO no outbox | ✅ |
| U02 | Lançar título já vencido | Vencimento 10 dias antes de hoje | Nasce com situação VENCIDA | ✅ |
| U03 | RN04 — pessoa do papel errado | Título a pagar com pessoa CLIENTE (a receber: FORNECEDOR) | 422; nada gravado | ✅ |
| U04 | RN01 — categoria consolidada | Conta "2 - Despesas" (consolidada) | 422 "consolidada" | ✅ |
| U05 | Serviço de pessoas indisponível | `PessoaClient` lança `ServicoIndisponivelException` | Exceção 503; nada gravado; nenhum evento | ✅ |
| U06 | Entrada inválida — datas | Vencimento um dia antes da emissão | 422 "vencimento" | ✅ |
| U07 | Entrada inválida — desconto | Valor 100, desconto 100 | 422 "desconto" | ✅ |
| U08 | RN05 — quitação dentro da validade | Valor 1000, desconto 50 até 10/11, pago em 09/11 | Valor efetivo 950; acréscimo 0; situação PAGA; evento QUITADO | ✅ |
| U09 | RN05 — quitação em atraso | Mesmo título pago em 18/11 (vencimento 15/11, acréscimo 20) | Valor efetivo 1020; desconto 0 | ✅ |
| U10 | RN05 — desconto fora da validade | Desconto 50 informado em 12/11 (validade 10/11) | 422 "desconto só vale até 10/11/2026" | ✅ |
| U11 | Operação duplicada sem chave | Quitar duas vezes o mesmo título | 2ª: 422 "Título já quitado em..."; só 1 evento | ✅ |
| U12 | Operação duplicada com a mesma `Idempotency-Key` | Duas quitações com a chave "chave-1" | 2ª devolve o mesmo resultado (`repetida=true`), 950; 1 evento, 1 registro de chave | ✅ |
| U13 | Chave reaproveitada em outro título | Chave já usada no título 99, enviada para o título 5 | 422 "outro título" | ✅ |
| U14 | Quitação com data futura | Data = hoje + 1 | 422 "futura" | ✅ |
| U15 | RN07 — cancelar | DELETE de título vencido | Situação CANCELADA; `delete` nunca chamado; evento CANCELADO | ✅ |
| U16 | Título quitado não pode ser cancelado | Título PAGA | 422 | ✅ |

## 2. Testes dos controladores e endpoints

`ContaPagarControllerTest` e `ContaReceberControllerTest` (`@WebMvcTest` + MockMvc; serviço simulado).

| # | Cenário | Requisição | Resultado esperado | Obtido |
|---|---|---|---|---|
| C01 | Lançamento válido | `POST /api/contas-pagar` com `pessoa:{id:1, nome:"ignorado"}` | 201; `pessoa.nome` vem da réplica, não do corpo | ✅ |
| C02 | Campos inválidos | `numeroTitulo:""`, `valor:-5`, sem pessoa | 400 `FALHA_VALIDACAO` com `campos` contendo `valor` e `pessoa` | ✅ |
| C03 | JSON com enum inexistente | `tipoTitulo:"CHEQUE_VOADOR"` | 400 `REQUISICAO_INVALIDA` | ✅ |
| C04 | Título inexistente | `GET /api/contas-pagar/99` | 404 `NAO_ENCONTRADO` | ✅ |
| C05 | Regra de negócio | Serviço lança `NegocioException` | 422 `REGRA_NEGOCIO` | ✅ |
| C06 | Dependência fora do ar | Serviço lança `ServicoIndisponivelException` | 503 `SERVICO_INDISPONIVEL` | ✅ |
| C07 | Quitação repetida | `POST .../1/pagamento` + `Idempotency-Key: abc` (replay) | 200 + header `Idempotent-Replay: true` | ✅ |
| C08 | Cancelamento | `DELETE /api/contas-pagar/1` | 204 | ✅ |
| G01 | Fallback do gateway | `FallbackController.fallback("pessoas")` | 503, `erro=SERVICO_INDISPONIVEL`, mensagem cita "Pessoas" | ✅ |

## 3. Testes de integração com o banco de dados

`@SpringBootTest` + Testcontainers (PostgreSQL 16 real, schema criado pelas migrations Flyway e validado pelo Hibernate).

| # | Serviço | Cenário | Dados | Resultado esperado | Obtido |
|---|---|---|---|---|---|
| I01 | contas-pagar / receber | Fluxo completo HTTP → banco | Lança INT-1 (500, desconto 25), repete o lançamento, quita com chave `int-chave-1`, repete com a mesma chave, tenta com `int-chave-2`, consulta por situação e período | 201; 422 (número repetido); 200 `Replay:false` com valor 475; 200 `Replay:true` com 475; 422; **exatamente 2 eventos** no outbox; título encontrado na consulta | ✅ |
| I02 | pessoas | Cadastro e unicidade | Documento `11.222.333/0001-44`, UF `go`, CEP `75900-000`; depois o mesmo documento | 201 com `11222333000144`, `GO`, `75900000`; depois 422 | ✅ |
| I03 | pessoas | Entradas inválidas | Nome "Jo", documento "123", e-mail "sem-arroba", UF "GOI", sem tipo | 400 com os 5 campos listados | ✅ |
| I04 | pessoas | Filtro e exclusão lógica | Pessoa AMBOS + FORNECEDOR; filtra CLIENTE; exclui o fornecedor | AMBOS aparece no filtro CLIENTE; excluído some da lista; GET → 404; 2º DELETE → 404 | ✅ |
| I05 | categorias | Carga inicial (Flyway) | — | `2.1.01` presente, com superior `2.1` | ✅ |
| I06 | categorias | RN01 — superior consolidada | Subconta de 1.1 (analítica) e de 2.1 (consolidada) | 422 e 201 | ✅ |
| I07 | categorias | Conta com filhos | DELETE e PUT `consolidada:false` na conta 2 | 422 nos dois | ✅ |
| I08 | categorias | Validação | Código "abc"; código "1" repetido | 400 (campo `codigo`); 422 | ✅ |
| I09 | relatórios | Evento duplicado | Mesmo evento aplicado 2 vezes | 1ª aplica, 2ª ignora; previsto a pagar = 300 (não 600) | ✅ |
| I10 | relatórios | Evento fora de ordem | QUITADO v1 chega antes de REGISTRADO v0 | REGISTRADO é descartado; recebido = 950 | ✅ |
| I11 | relatórios | Fluxo de caixa | A receber 500 (jan), a pagar 200 (venc. jan, pago 210 em fev), cancelado 999 (jan) | Jan: previsto receber 500, previsto pagar 200 (cancelado fora), saldo 300; fev: pago 210 | ✅ |
| I12 | relatórios | Período invertido | início 01/02, fim 01/01 | Erro "anterior" | ✅ |

## 4. Testes manuais de sistema (comunicação entre serviços e falhas)

Executados em 23/11/2026 com `docker compose up -d --build` (11 containers), chamando `http://localhost:8080`.

| # | Cenário | Passos | Esperado | Obtido |
|---|---|---|---|---|
| M01 | Descoberta de serviços | `GET http://localhost:8761/eureka/apps` | 6 aplicações registradas | `CONTAS-RECEBER-SERVICE, CATEGORIAS-SERVICE, API-GATEWAY, PESSOAS-SERVICE, RELATORIOS-SERVICE, CONTAS-PAGAR-SERVICE` ✅ |
| M02 | Comunicação síncrona — RN04 | Lançar título a pagar para a pessoa 2 (CLIENTE) | 422 vindo do contas-pagar após consultar o pessoas-service | `422 "Mercado Beta está cadastrada como cliente; títulos a pagar exigem um fornecedor."` ✅ |
| M03 | Comunicação síncrona — RN01 | Lançar com a conta 2 (consolidada) | 422 após consultar o categorias-service | `422 "A conta 2 é consolidada e não pode receber títulos; escolha uma subconta."` ✅ |
| M04 | Quitação repetida (rede reenviando) | `POST /api/contas-pagar/1/pagamento` duas vezes com `Idempotency-Key: smoke-1` | 200 nas duas; a 2ª com `Idempotent-Replay: true`; valor pago 950 | `HTTP 200 · Idempotent-Replay: false` e depois `HTTP 200 · Idempotent-Replay: true` ✅ |
| M05 | Comunicação assíncrona | Lançar CP-1, CP-2, CR-1, CR-2; quitar CP-1 e CR-1; `GET /api/relatorios/fluxo-caixa` | Relatório reflete os 4 títulos e as 2 quitações em segundos | Nov/26: recebido 2500, pago 950; Dez/26: a receber 2500, a pagar 1000 ✅ |
| M06 | Inadimplência e vencimento automático | CR-2 lançado com vencimento 10/11/2026 | Nasce VENCIDA e aparece em `/inadimplencia` | `quantidade 1, total 800, diasAtraso 13` ✅ |
| M07 | **Serviço indisponível** | `docker compose stop pessoas-service`; `GET /api/pessoas`; `POST /api/contas-pagar`; `GET /api/contas-pagar` | Gateway: 503 claro; lançamento: 503 sem gravar; listagem: 200 | `503 "O serviço de Pessoas está temporariamente indisponível. Os demais módulos continuam funcionando..."` · `503 "...não foi possível confirmar a pessoa do título."` · `200` com o nome da pessoa (réplica) ✅ |
| M08 | **Mensageria indisponível** | `docker compose stop rabbitmq`; quitar CP-2; consultar outbox e relatório; `start rabbitmq`; aguardar | Quitação aceita; evento pendente no outbox; relatório desatualizado; após voltar: evento publicado e relatório atualizado | Quitação 200; outbox `titulo.quitado` pendente; relatório Nov pago 950 → após o broker voltar: 0 pendentes e pago **1300** ✅ |
| M09 | Front-end ponta a ponta | Em http://localhost:3000: enviar formulário vazio; lançar CP (450, desconto 30 até 30/11); pagar pelo modal; abrir Relatórios | Erros do Zod no campo; só fornecedores e contas analíticas nos selects; prévia 420; relatório atualizado | Validações exibidas; título salvo com notificação; prévia "R$ 420,00"; Nov/26 pago 1720 (950 + 350 + 420) ✅ |

### Evidência (trechos das respostas)

```text
### pessoas-service PARADO
-- GET /api/pessoas pelo gateway:
{"status":503,"erro":"SERVICO_INDISPONIVEL","mensagem":"O serviço de Pessoas está temporariamente
 indisponível. Os demais módulos continuam funcionando; tente novamente em instantes."} [HTTP 503]
-- lançar título (precisa confirmar a pessoa):
{"status":503,"erro":"SERVICO_INDISPONIVEL","mensagem":"O serviço de Pessoas está indisponível;
 não foi possível confirmar a pessoa do título. Tente novamente em instantes."} [HTTP 503]
-- listar contas a pagar continua funcionando (réplica local do nome):
[{"id":2,"numeroTitulo":"CP-2","pessoa":{"id":1,"nome":"Distribuidora Alfa Ltda",...}] [HTTP 200]

### RabbitMQ PARADO
-- quitar CP-2 com broker fora: {"id":2,"numeroTitulo":"CP-2",...}  (HTTP 200)
-- outbox: titulo.quitado | pendente = t
-- relatório (ainda sem o pagamento): {'mes': '2026-11', ..., 'pago': 950.0}
-- broker voltou; aguardando...
-- outbox pendentes: 0
-- relatório (atualizado):            {'mes': '2026-11', ..., 'pago': 1300.0}
```

## 5. Como repetir os testes manuais

```bash
docker compose up -d --build
# aguarde ~1 min até todos aparecerem em http://localhost:8761
curl -s localhost:8080/api/plano-contas
curl -s -X POST localhost:8080/api/pessoas -H "Content-Type: application/json" \
     -d '{"tipo":"FORNECEDOR","nome":"Distribuidora Alfa","documento":"12345678000199"}'
docker compose stop pessoas-service      # M07
docker compose start pessoas-service
docker compose stop rabbitmq             # M08
docker compose start rabbitmq
```

A coleção [`api/CPR.postman_collection.json`](api/CPR.postman_collection.json) tem todas as requisições prontas.
