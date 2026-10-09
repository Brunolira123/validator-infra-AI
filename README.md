# Validador de Infraestrutura VR

API para levantamento de infraestrutura de clientes VR: cadastro de clientes, levantamentos, upload de fotos dos equipamentos, extração de especificações por IA e validação contra os requisitos e a base de equipamentos homologados.

## Pré-requisitos

- Docker (com Docker Compose): para subir o ambiente completo e para os testes de integração
- Java 17 e Maven (ou o wrapper `./mvnw`, já incluso): para rodar sem Docker e para os testes
- PostgreSQL 14: só para rodar a aplicação sem Docker

## Variáveis de ambiente

Copie `.env.example` para `.env` na raiz e preencha:

| Variável | Uso |
|---|---|
| `OPENROUTER_API_KEY` | Chave do OpenRouter, usada na extração de dados das fotos por IA |
| `DB_PASSWORD` | Senha do PostgreSQL. No Docker Compose, também define a senha do container do banco |
| `JWT_SECRET` | Segredo de assinatura dos tokens JWT, com no mínimo 32 caracteres. Gere um aleatório, por exemplo com `openssl rand -hex 32` |
| `DB_HOST`, `DB_PORT` | Só para rodar sem Docker. Padrão `localhost:5432`. O Compose ignora esses valores para a aplicação |
| `FOTOS_PATH` | Opcional. Pasta das fotos. Padrão `./storage/fotos` (no Docker, `/app/storage/fotos`, num volume) |

O `.env` não deve ser versionado. O Docker Compose o lê automaticamente, e a aplicação também o carrega ao rodar fora do Docker.

## Como rodar com Docker Compose

```bash
# Sobe Postgres 14 + aplicação (o primeiro build leva alguns minutos)
docker compose up -d

# Mesmo, incluindo o Adminer para inspecionar o banco
docker compose --profile dev up -d

# Recompila a imagem depois de mudar o código
docker compose up -d --build

# Acompanha os logs da aplicação
docker compose logs -f app

# Para e remove os containers, mantendo os dados (banco e fotos)
docker compose down

# Para e APAGA os volumes (banco e fotos voltam do zero)
docker compose down -v
```

| Serviço | Endereço |
|---|---|
| API | http://localhost:8080 |
| Health | http://localhost:8080/actuator/health |
| Adminer (profile `dev`) | http://localhost:8081 — sistema PostgreSQL, servidor `postgres`, usuário `postgres`, senha do `DB_PASSWORD`, banco `validator_infra` |
| Postgres (para a IDE) | `localhost:5433` |

As portas podem ser trocadas no `.env` com `APP_PORT`, `ADMINER_PORT` e `COMPOSE_DB_PORT`. O banco e as fotos ficam nos volumes `validator-infra_postgres-data` e `validator-infra_fotos`. A aplicação roda com usuário não-root, e o container é marcado como `healthy` quando o `/actuator/health` responde `UP`.

## Como rodar sem Docker

Com um PostgreSQL 14 local e o banco `validator_infra` criado:

```bash
./mvnw spring-boot:run
```

Se o seu Postgres não estiver em `localhost:5432`, defina `DB_HOST`/`DB_PORT` no `.env`. As fotos ficam em `./storage/fotos`.

## Usando a API

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

Na primeira subida é criado o usuário `admin` com senha `admin` (perfil ADMIN). Troque essa senha antes de qualquer uso fora do ambiente de desenvolvimento.

Login para obter o token:

```bash
curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' -d '{"login":"admin","senha":"admin"}'
```

Use o `token` retornado no header `Authorization: Bearer <token>` nas demais rotas. Só `/api/auth/**`, o Swagger e o `/actuator/health` são públicos.

## Como rodar os testes

```bash
./mvnw test
```

- **Testes unitários** (motor de regras e regras do levantamento): Mockito puro, sem banco.
- **Testes de integração** (`@IntegrationTest`): sobem a aplicação inteira contra um PostgreSQL 14 real e descartável via Testcontainers (mesma versão do Compose e do banco local). Um único container é compartilhado pela suíte e removido ao final.
  - **Exigem Docker rodando.** Sem Docker, falham com erro explícito do Testcontainers; não são ignorados.
  - Não usam o banco local, o `.env` nem a API de IA: a chave da IA e o segredo JWT de teste estão em `src/test/resources/application-test.properties`, e a IA é mockada onde é chamada.
  - A primeira execução baixa a imagem `postgres:14-alpine` (se ainda não estiver no cache do Docker); as seguintes levam cerca de 1 minuto.
