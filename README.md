# RATE.IO — Arquitetura de Microsserviços Distribuídos (Trabalho 2)

Sistema distribuído de gestão, divisão inteligente e liquidação de despesas em grupo com **Frontend SPA**, **API Gateway centralizador em Spring Boot**, **dois microsserviços internos comunicando-se via gRPC (Protocol Buffers)**, **autenticação com JSON Web Token (JWT)** e **persistência real em banco relacional PostgreSQL**.

---

## 🏛️ Arquitetura do Sistema

```
                  ┌────────────────────────────────────────┐
                  │          🌐 Frontend (SPA)             │
                  │   HTML5 / CSS3 / Vanilla JS + JWT      │
                  └───────────────────┬────────────────────┘
                                      │ HTTP / JSON
                                      │ Authorization: Bearer <JWT>
                                      ▼
                  ┌────────────────────────────────────────┐
                  │    🚪 API Gateway (Spring Boot :8080)  │
                  │  - Filtro JWT (401 Unauthorized)       │
                  │  - Validação de Payload (400 Bad Req)  │
                  │  - Respostas Semânticas (201 Created)  │
                  │  - Tradução JSON ⇄ gRPC / Protobuf     │
                  └───────────┬────────────────┬───────────┘
                              │                │
            gRPC (Protobuf)   │                │   gRPC (Protobuf)
            Netty NIO :50051  │                │   Netty NIO :50052
                              ▼                ▼
┌──────────────────────────────────────┐     ┌──────────────────────────────────────┐
│  Microsserviço 1: Usuários & Grupos  │     │       Microsserviço 2: Finanças      │
│  - Cadastro e Autenticação (BCrypt)  │     │  - Lançamento de Despesas & Splits   │
│  - Gestão de Grupos e Participantes  │     │  - Algoritmo Guloso de Dívidas       │
│  - Porta gRPC: 50051                 │     │  - Liquidação de Dívidas (Settle Up) │
│                                      │     │  - Porta gRPC: 50052                 │
└──────────────────┬───────────────────┘     └──────────────────┬───────────────────┘
                   │                                            │
                   │ JDBC                                       │ JDBC
                   ▼                                            ▼
┌──────────────────────────────────────┐     ┌──────────────────────────────────────┐
│       🐘 PostgreSQL (rateio_users)   │     │      🐘 PostgreSQL (rateio_finance)  │
│  Tabelas: users, groups,             │     │  Tabelas: expenses, expense_splits,  │
│           group_members              │     │           settlements                │
└──────────────────────────────────────┘     └──────────────────────────────────────┘
```

---

## 📋 Atendimento aos Requisitos do Trabalho 2

| # | Requisito do Trabalho | Como foi Atendido no RATE.IO |
|---|---|---|
| 1 | **Frontend Funcional** | Interface SPA elegante em `api-gateway/src/main/resources/static/` com login/cadastro, seleção de grupos, métricas em tempo real, adição de despesas e liquidação. Fala **exclusivamente com o API Gateway**. |
| 2 | **API Gateway (REST / HTTP)** | Desenvolvido com **Spring Boot 3.2.5** (`spring-boot-starter-web`), expondo endpoints REST semânticos, traduzindo requisições HTTP/JSON para chamadas binárias gRPC/Protobuf. |
| 3 | **Mínimo 2 Microsserviços gRPC** | **Dois serviços independentes**: (1) `service-usergroup` (porta `:50051`) e (2) `service-finance` (porta `:50052`), ambos implementados em Java com **Netty NIO**. |
| 4 | **Banco de Dados Real** | **PostgreSQL 16** via Docker Compose com bancos reais `rateio_users` e `rateio_finance`. **Zero dados estáticos, listas em memória ou mocks**. |
| 5 | **Validação no Backend & Status Codes** | Validação rigorosa dos campos no API Gateway retornando explicitamente **`400 Bad Request`** em caso de dados inválidos/ausentes e **`201 Created`** no sucesso de cadastros. |
| 6 | **Segurança (Autenticação JWT)** | Filtro `JwtAuthenticationFilter` no Spring Security interceptando o cabeçalho `Authorization: Bearer <token>`. Requisições não autenticadas são **imediatamente barradas com `401 Unauthorized`**. |
| 7 | **Tradução e Delegação de Protocolo** | O Gateway desserializa o JSON, valida regras de negócio, autentica o usuário, constrói os objetos Protobuf e despacha via stubs gRPC dedicados. |

---

## 📁 Estrutura do Repositório

```text
sd-2026-2/
├── proto/
│   └── rateio.proto               # Contrato Protobuf dividido (UserGroupService e FinanceService)
├── api-gateway/                   # API Gateway Centralizador (Spring Boot)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/sd/rateio/gateway/
│       │   ├── GatewayApplication.java
│       │   ├── config/SecurityConfig.java           # Spring Security + CORS + 401 Handler
│       │   ├── security/JwtTokenProvider.java       # Geração e validação de tokens JWT
│       │   ├── security/JwtAuthenticationFilter.java# Interceptador Bearer Token
│       │   ├── controller/AuthController.java       # /api/auth (register, login)
│       │   ├── controller/GroupController.java      # /api/groups
│       │   ├── controller/FinanceController.java    # /api/expenses, /api/settle, etc.
│       │   └── grpc/GrpcClientManager.java          # Stubs gRPC Netty NIO
│       └── resources/
│           ├── application.properties
│           └── static/                              # Frontend SPA (index.html, style.css, app.js)
├── service-usergroup/             # Microsserviço 1: Usuários & Grupos (gRPC :50051)
│   ├── pom.xml
│   └── src/main/java/com/sd/rateio/usergroup/
│       ├── UserGroupMain.java
│       ├── UserGroupServiceImpl.java
│       └── db/ (PostgresManager, UserGroupRepository)
├── service-finance/               # Microsserviço 2: Finanças (gRPC :50052)
│   ├── pom.xml
│   └── src/main/java/com/sd/rateio/finance/
│       ├── FinanceMain.java
│       ├── FinanceServiceImpl.java
│       ├── DebtSimplifier.java                      # Algoritmo guloso de simplificação de dívidas
│       └── db/ (PostgresManager, FinanceRepository)
├── docker-compose.yml             # Container PostgreSQL 16
├── init-db.sql                    # Inicialização dos bancos rateio_users e rateio_finance
└── README.md
```
