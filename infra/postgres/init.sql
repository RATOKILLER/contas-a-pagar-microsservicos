-- Executado uma única vez, na primeira subida do container PostgreSQL.
-- Cada microsserviço tem o SEU banco e o SEU usuário: um serviço não consegue
-- ler nem alterar as tabelas de outro (autonomia de dados).

CREATE USER pessoas_user WITH PASSWORD 'pessoas_pass';
CREATE DATABASE pessoas_db OWNER pessoas_user;

CREATE USER categorias_user WITH PASSWORD 'categorias_pass';
CREATE DATABASE categorias_db OWNER categorias_user;

CREATE USER contas_pagar_user WITH PASSWORD 'contas_pagar_pass';
CREATE DATABASE contas_pagar_db OWNER contas_pagar_user;

CREATE USER contas_receber_user WITH PASSWORD 'contas_receber_pass';
CREATE DATABASE contas_receber_db OWNER contas_receber_user;

CREATE USER relatorios_user WITH PASSWORD 'relatorios_pass';
CREATE DATABASE relatorios_db OWNER relatorios_user;

-- Ninguém além do dono conecta em cada banco.
REVOKE CONNECT ON DATABASE pessoas_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE categorias_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE contas_pagar_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE contas_receber_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE relatorios_db FROM PUBLIC;
