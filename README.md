# Validador de Infraestrutura VR

API para levantamento de infraestrutura de clientes VR: cadastro de clientes, levantamentos, upload de fotos dos equipamentos, extração de especificações por IA e validação contra os requisitos e a base de equipamentos homologados.

## Pré-requisitos

- Java 17
- Docker rodando (obrigatório para os testes de integração)
- Maven (ou o wrapper `./mvnw`, já incluso)
- PostgreSQL para rodar a aplicação localmente (o padrão é `localhost:8745`, banco `validator_infra`, usuário `postgres`)

## Variáveis de ambiente

Copie `.env.example` para `.env` na raiz e preencha:

| Variável | Uso |
|---|---|
| `OPENROUTER_API_KEY` | Chave do OpenRouter, usada na extração de dados das fotos por IA |
| `DB_PASSWORD` | Senha do PostgreSQL |
| `JWT_SECRET` | Segredo de assinatura dos tokens JWT, com no mínimo 32 caracteres. Gere um aleatório, por exemplo com `openssl rand -hex 32` |

O `.env` é carregado automaticamente na inicialização e não deve ser versionado.

## Como rodar a aplicação

```bash
./mvnw spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

Na primeira subida é criado o usuário `admin` com senha `admin` (perfil ADMIN). Troque essa senha antes de qualquer uso fora do ambiente de desenvolvimento.

Login para obter o token:

```bash
curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' -d '{"login":"admin","senha":"admin"}'
```

Use o `token` retornado no header `Authorization: Bearer <token>` nas demais rotas.

## Como rodar os testes

```bash
./mvnw test
```

- **Testes unitários** (motor de regras e regras do levantamento): Mockito puro, sem banco.
- **Testes de integração** (`@IntegrationTest`): sobem a aplicação inteira contra um PostgreSQL 16 real e descartável via Testcontainers. Um único container é compartilhado pela suíte e removido ao final.
  - **Exigem Docker rodando.** Sem Docker, falham com erro explícito do Testcontainers; não são ignorados.
  - Não usam o banco local, o `.env` nem a API de IA: a chave da IA e o segredo JWT de teste estão em `src/test/resources/application-test.properties`, e a IA é mockada onde é chamada.
  - A primeira execução baixa a imagem `postgres:16-alpine`; as seguintes levam cerca de 1 minuto.
