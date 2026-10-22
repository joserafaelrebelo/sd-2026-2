-- Script executado automaticamente pelo container PostgreSQL na primeira inicialização.
-- Cria os dois bancos de dados usados pelos microsserviços do RATEIO.

-- Banco do Microsserviço de Usuários & Grupos (já criado via POSTGRES_DB)
-- CREATE DATABASE rateio_users;  -- criado automaticamente

-- Banco do Microsserviço de Finanças
CREATE DATABASE rateio_finance;

-- Conceder permissões ao usuário rateio nos dois bancos
GRANT ALL PRIVILEGES ON DATABASE rateio_users TO rateio;
GRANT ALL PRIVILEGES ON DATABASE rateio_finance TO rateio;

