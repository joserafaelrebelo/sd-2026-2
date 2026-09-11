# RATE.IO 

## Overview

RATE.IO e uma aplicacao distribuida para gestao e rateio de despesas em grupo, inspirada no modelo Splitwise.

<img width="1600" height="808" alt="WhatsApp Image 2026-09-10 at 20 56 10" src="https://github.com/user-attachments/assets/8cb0f1dc-cc2b-4a1e-9e77-25251185fdab" />

A solucao usa arquitetura de microsservicos com comunicacao gRPC:
- Microsservico B (server): motor de negocio + persistencia SQLite.
- Microsservico A (client): cliente gRPC + web gateway HTTP + interface web (SPA).

Fluxo principal:
1. Usuario acessa a interface web no navegador.
2. Web Gateway recebe requisicoes REST/JSON.
3. Gateway converte para chamadas gRPC ao servidor.
4. Servidor processa regras de rateio e salva dados no SQLite.

## Principais funcionalidades

- Criacao e listagem de grupos.
- Registro de despesas com divisao entre participantes.
- Consulta de extrato de despesas por grupo.
- Simplificacao de dividas (quem paga para quem).
- Liquidacao de dividas (settle up).

## Stack tecnologica

- Java 17
- Maven
- gRPC + Protocol Buffers
- Netty (transporte gRPC)
- SQLite (sqlite-jdbc)
- Frontend web estatico (HTML, CSS, JS)

## Requirements

### Desenvolvimento local

- Linux/macOS/WSL (ou ambiente equivalente)
- Java 17+
- Maven 3.8+
- Git
- lsof (usado pelos scripts)

Pacotes sugeridos em Ubuntu:

bash
sudo apt update && sudo apt install -y openjdk-17-jdk maven git lsof


### Execucao em GCP (opcional)

- Conta GCP com permissao em Compute Engine e Firewall
- gcloud CLI configurado
- 2 VMs (cliente e servidor)

Para deploy em nuvem, consulte DEPLOY_GCP_v2.md.

## Instalacao

1. Clone o repositorio:

bash
git clone <URL_DO_REPOSITORIO> rateio
cd rateio


2. Build dos modulos:

bash
mvn -q -f service-b-server/pom.xml clean package
mvn -q -f service-a-client/pom.xml clean package


## Execucao local

### Opcao 1: Subida unificada (recomendada)

bash
bash run.sh


Acesse a interface em:

text
http://localhost:8080


### Opcao 2: Teste automatizado (CLI)

bash
bash run.sh --test


### Opcao 3: Subida separada por servico

Terminal 1 (Servidor B):

bash
./run-server.sh


Terminal 2 (Cliente A / Web):

bash
./run-client.sh


## Variaveis de ambiente uteis

Servidor B:
- GRPC_SERVER_BIND_ADDRESS (padrao: 0.0.0.0)
- GRPC_SERVER_PORT (padrao: 50051)
- RATEIO_DB_PATH (padrao: data/rateio.db)

Cliente A:
- GRPC_SERVER_HOST (padrao: 127.0.0.1)
- GRPC_SERVER_PORT (padrao: 50051)
- WEB_BIND_ADDRESS (padrao: 0.0.0.0)
- WEB_PORT (padrao: 8080)

## Estrutura do projeto

text
sd-2026-2/
├── proto/rateio.proto
├── service-b-server/
│   ├── pom.xml
│   └── src/main/java/com/sd/rateio/server/
├── service-a-client/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/sd/rateio/client/
│       └── resources/web/
├── run.sh
├── run-server.sh
├── run-client.sh
├── README.md


## Notas

- O banco local fica em data/rateio.db.
- Em ambiente de desenvolvimento, sempre rode build apos atualizar codigo para evitar executar JAR desatualizado.
