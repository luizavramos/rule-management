# rule-management

Gestão das regras de detecção de fraude **sem redeploy**. Um analista cria e testa uma regra, um coordenador a ativa, e o
motor de detecção passa a usá-la em segundos, sem reiniciar. Spring Boot 4, Java 21, virtual threads, Postgres. Porta 8082.

Visão geral do projeto: https://github.com/luizavramos/fraud-detection-engine ·
Todos os endpoints, com request e response: `docs/endpoints.md` (seção 2).

## O que faz

- **Cria e versiona regras.** Cada regra tem uma expressão [CEL](https://github.com/google/cel-spec), parâmetros, peso (pontos),
  texto do motivo e casos de teste. Mudar um limite é uma nova versão só com o parâmetro novo.
- **Valida antes de gravar.** A expressão é compilada e os casos de teste são executados com o `rules-kernel`, o mesmo
  componente que o motor usa. Feature inexistente ou tipo errado devolve `422` e nada é gravado.
- **Controla o ciclo de vida:** `DRAFT` (rascunho, não afeta nada), `SHADOW` (o motor avalia e registra, mas não soma
  pontos), `ACTIVE` (soma pontos) e `DISABLED` (desligada, funciona como kill switch por regra).
- **Exige dupla aprovação.** Quem criou a regra não pode ativá-la: tentar devolve `403`.
- **Publica o conjunto de regras (rule set).** Toda mudança gera uma versão nova e inteira do conjunto, com o scoring
  junto. Também é possível voltar a uma versão anterior (`:rollback`).
- **Configura o scoring.** As faixas de severidade (padrão: média a partir de 40 pontos, alta a partir de 70) mudam por
  `PUT /v1/scoring`, sem deploy.
- **Audita.** Cada ação grava quem fez, o quê e quando.

## Como as regras chegam ao motor

O motor **não chama esta API**. A entrega é por evento, com garantia de que nada se perde:

```
API ──► Postgres (regra + linha na tabela outbox, na mesma transação)
        └─► Debezium lê a outbox ──► tópico rule-sets.v1 (compactado) ──► detection-engine
```

O motor lê o tópico, compila o conjunto e troca a quente. Se o conjunto novo não compilar, ele mantém o anterior.
`GET /v1/rule-sets/active` só serve para consultar o que está ativo.

## Endpoints

HTTP Basic. Erros seguem Problem Details (`application/problem+json`, `type` = `urn:motorfraude:problem:*`).

| Método e caminho | Papel | Para que serve |
|---|---|---|
| `GET /v1/rules` | leitor, autor ou aprovador | Lista as regras |
| `GET /v1/rules/{ruleId}` | leitor, autor ou aprovador | Histórico de versões de uma regra |
| `GET /v1/rule-sets/active` | leitor, autor ou aprovador | Conjunto ativo (com `ETag`; `If-None-Match` devolve `304`) |
| `POST /v1/rules:validate` | autor | Testa uma regra sem gravar |
| `POST /v1/rules` | autor | Cria a regra (versão 1, `DRAFT`) |
| `POST /v1/rules/{ruleId}/versions` | autor | Cria uma nova versão |
| `POST /v1/rules/{ruleId}/versions/{n}:shadow` | autor | Põe a versão em modo sombra |
| `POST /v1/rules/{ruleId}/versions/{n}:activate` | **aprovador** | Ativa a versão |
| `POST /v1/rules/{ruleId}:disable` | autor | Desliga a regra |
| `POST /v1/rule-sets/{version}:rollback` | **aprovador** | Volta o conjunto para uma versão anterior |
| `PUT /v1/scoring` | **aprovador** | Muda as faixas de severidade |

Operação: `/actuator/health` (com `liveness` e `readiness`) e `/actuator/prometheus`. Swagger em `/swagger-ui.html`.
Todos os `POST` aceitam `Idempotency-Key`: repetir a mesma chamada devolve a mesma resposta, com `Idempotent-Replayed: true`.

Exemplo de regra (`POST /v1/rules:validate`):

```json
{
  "expression": "features.count5m > params.maxCount",
  "params": { "maxCount": 5 },
  "paramOverrides": { "PJ": { "maxCount": 30 } },
  "weight": 25,
  "reasonTemplate": "Mais de {params.maxCount} transacoes em 5 min",
  "testCases": [
    { "name": "6 dispara", "context": { "features.count5m": 6 }, "expectedHit": true },
    { "name": "5 nao dispara", "context": { "features.count5m": 5 }, "expectedHit": false }
  ]
}
```

Um roteiro completo (criar, testar em sombra, ativar, desligar, reverter) está em `docs/demo-regras.http` no repositório
`motor-fraude`.

## Usuários e papéis

Usuários locais em memória; as senhas mudam por variável de ambiente. **Troque-as fora do ambiente local.**

| Usuário | Senha padrão | Papéis | Pode |
|---|---|---|---|
| `analista` | `analista-local` | `RULE_AUTHOR` | criar, validar, pôr em sombra, desligar |
| `coordenador` | `coordenador-local` | `RULE_AUTHOR`, `RULE_APPROVER` | tudo do analista, mais ativar, reverter e mudar o scoring |
| `motor` | `motor-local` | `RULE_READER` | só ler |

Variáveis: `RULES_ANALISTA_PASSWORD`, `RULES_COORDENADOR_PASSWORD`, `RULES_MOTOR_PASSWORD`.

## Regras iniciais

A migração `V2__seed_rules.sql` publica 8 regras (R01, R03, R04, R05, R07, R09, R10 e R12) e o scoring 40/70 como o
conjunto de versão 1, para o sistema funcionar assim que sobe. As mesmas expressões estão em
`rules-kernel/src/test/resources/initial-rules.json`, e um teste confere que as duas fontes não divergem.

## Configuração

| Variável | Padrão |
|---|---|
| `RULES_DB_URL` | `jdbc:postgresql://localhost:5432/rules_db` |
| `RULES_DB_USER` / `RULES_DB_PASSWORD` | `motorfraude` / `motorfraude` |

O esquema do banco é criado pelo Flyway (`src/main/resources/db/migration`, V1 a V5: esquema, regras iniciais,
chaves de idempotência, dupla aprovação e outbox).

## Rodar

Pré-requisitos: Java 21 e os artefatos `motor-fraude-parent`, `contracts`, `platform-commons` e `rules-kernel`
instalados (`./mvnw install` em cada um, nessa ordem, ou token do GitHub Packages). Postgres no ar com o banco `rules_db`.

```bash
./mvnw verify          # testes unitários, de API, ArchUnit e de ciclo de vida com o kernel
./mvnw package
java -jar target/rule-management-*.jar
```

Com o compose do repositório `fraud-detection-engine`, depois de gerar o jar:

```bash
docker compose --profile app up -d --build rule-management
```

Para que as regras cheguem ao motor, o conector da outbox precisa estar registrado no Debezium
(`infra/connect/register-rule-sets-outbox.sh`). O `infra/run-all.sh` faz isso.

## Estrutura

Clean Architecture, verificada por teste ArchUnit:

```
domain/        Rule, RuleVersion, RuleSet, Scoring, RuleStatus e as regras de transição
application/   casos de uso (CreateRule, ActivateRule, RollbackRuleSet, UpdateScoring...) e interfaces (gateways)
adapters/      controller (REST, idempotência), gateway (Postgres, outbox, kernel) e presenter (Problem Details)
infrastructure/ configuração do Spring
```

## Limitações conhecidas

- Os usuários são fixos em memória (HTTP Basic). Em produção a proposta é OIDC no balanceador (ver `arquitetura-aws.drawio`).
- Não há tela: a interação é pela API e pelo Swagger.
