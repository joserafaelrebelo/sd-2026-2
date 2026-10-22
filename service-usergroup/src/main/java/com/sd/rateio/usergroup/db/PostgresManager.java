package com.sd.rateio.usergroup.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gerencia a conexão com o banco PostgreSQL e a inicialização do esquema.
 */
public class PostgresManager {

    private static final String TAG = "[PostgresManager]";

    private final String url;
    private final String user;
    private final String password;

    public PostgresManager(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /**
     * Obtém uma nova conexão JDBC com o banco de dados.
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Cria as tabelas necessárias caso ainda não existam.
     */
    public void initSchema() throws SQLException {
        String ddl = """
                CREATE TABLE IF NOT EXISTS users (
                    id VARCHAR(50) PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    password_hash VARCHAR(200) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );

                CREATE TABLE IF NOT EXISTS groups (
                    id VARCHAR(50) PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );

                CREATE TABLE IF NOT EXISTS group_members (
                    group_id VARCHAR(50) NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
                    user_id VARCHAR(50) NOT NULL,
                    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (group_id, user_id)
                );
                """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        }

        System.out.println(TAG + " Esquema 'rateio_users' inicializado com sucesso.");
    }
}
