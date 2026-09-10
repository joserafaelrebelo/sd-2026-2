package com.sd.rateio.server.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gerenciador de conexão e inicialização do esquema do banco de dados SQLite para o RATEIO.
 */
public class DatabaseManager {
    private final String dbUrl;

    public DatabaseManager(String dbFilePath) {
        File file = new File(dbFilePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        this.dbUrl = "jdbc:sqlite:" + file.getAbsolutePath();
    }

    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(dbUrl);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
        }
        return conn;
    }

    public void initSchema() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Usuários
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id VARCHAR(50) PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // 2. Grupos
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS groups (
                    id VARCHAR(50) PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
            """);

            // 3. Membros do Grupo (N:N)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS group_members (
                    group_id VARCHAR(50) NOT NULL,
                    user_id VARCHAR(50) NOT NULL,
                    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (group_id, user_id),
                    FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                );
            """);

            // 4. Despesas
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS expenses (
                    id VARCHAR(64) PRIMARY KEY,
                    group_id VARCHAR(50) NOT NULL,
                    paid_by_user_id VARCHAR(50) NOT NULL,
                    amount DECIMAL(12, 2) NOT NULL CHECK (amount > 0),
                    description VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
                    FOREIGN KEY (paid_by_user_id) REFERENCES users(id)
                );
            """);

            // 5. Divisão de Despesas (Splits individuais)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS expense_splits (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    expense_id VARCHAR(64) NOT NULL,
                    user_id VARCHAR(50) NOT NULL,
                    share_amount DECIMAL(12, 2) NOT NULL CHECK (share_amount >= 0),
                    UNIQUE(expense_id, user_id),
                    FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE CASCADE,
                    FOREIGN KEY (user_id) REFERENCES users(id)
                );
            """);

            // 6. Liquidações / Pagamentos de Dívidas (Settle Up)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS settlements (
                    id VARCHAR(64) PRIMARY KEY,
                    group_id VARCHAR(50) NOT NULL,
                    from_user_id VARCHAR(50) NOT NULL,
                    to_user_id VARCHAR(50) NOT NULL,
                    amount DECIMAL(12, 2) NOT NULL CHECK (amount > 0),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CHECK (from_user_id <> to_user_id),
                    FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
                    FOREIGN KEY (from_user_id) REFERENCES users(id),
                    FOREIGN KEY (to_user_id) REFERENCES users(id)
                );
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_expenses_group ON expenses(group_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_splits_expense ON expense_splits(expense_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_settlements_group ON settlements(group_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_members_group ON group_members(group_id);");
        }

        System.out.println("[DatabaseManager] Esquema SQLite RATEIO inicializado com sucesso em " + dbUrl);
    }
}

