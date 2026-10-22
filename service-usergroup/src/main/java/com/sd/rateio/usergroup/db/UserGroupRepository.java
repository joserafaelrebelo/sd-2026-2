package com.sd.rateio.usergroup.db;

import com.sd.rateio.usergroup.GroupData;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositório responsável pelas operações de CRUD de usuários e grupos
 * no banco de dados PostgreSQL.
 */
public class UserGroupRepository {

    private static final String TAG = "[Microsserviço Usuários & Grupos]";
    private final PostgresManager pgManager;

    public UserGroupRepository(PostgresManager pgManager) {
        this.pgManager = pgManager;
    }

    // ── Registro de usuário ───────────────────────────────────────────────

    /**
     * Registra um novo usuário com senha hash (BCrypt).
     *
     * @return true se o usuário foi criado, false se já existia (chave duplicada).
     */
    public boolean registerUser(String userId, String name, String password) throws SQLException {
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        String sql = "INSERT INTO users (id, name, password_hash) VALUES (?, ?, ?)";

        try (Connection conn = pgManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, name);
            ps.setString(3, hash);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            // Código 23505 = unique_violation (chave duplicada)
            if ("23505".equals(e.getSQLState())) {
                return false;
            }
            throw e;
        }
    }

    // ── Autenticação de usuário ───────────────────────────────────────────

    /**
     * Autentica um usuário verificando a senha com BCrypt.
     *
     * @return {userId, name} se as credenciais forem válidas, null caso contrário.
     */
    public String[] authenticateUser(String userId, String password) throws SQLException {
        String sql = "SELECT id, name, password_hash FROM users WHERE id = ?";

        try (Connection conn = pgManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    if (BCrypt.checkpw(password, storedHash)) {
                        return new String[]{rs.getString("id"), rs.getString("name")};
                    }
                }
            }
        }
        return null;
    }

    // ── Criação de grupo ──────────────────────────────────────────────────

    /**
     * Cria um grupo e associa os membros indicados.
     * Utiliza transação para garantir atomicidade.
     * Para cada membro, cria um registro stub de usuário caso ainda não exista.
     *
     * @return true se o grupo foi criado, false se já existia.
     */
    public boolean createGroup(String groupId, String groupName, List<String> memberUserIds)
            throws SQLException {

        Connection conn = pgManager.getConnection();
        try {
            conn.setAutoCommit(false);

            // Insere o grupo
            String insertGroup = "INSERT INTO groups (id, name) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertGroup)) {
                ps.setString(1, groupId);
                ps.setString(2, groupName);
                ps.executeUpdate();
            } catch (SQLException e) {
                conn.rollback();
                if ("23505".equals(e.getSQLState())) {
                    return false;
                }
                throw e;
            }

            // Insere membros no grupo
            String upsertUser = "INSERT INTO users (id, name, password_hash) VALUES (?, ?, '') "
                    + "ON CONFLICT (id) DO NOTHING";
            String insertMember = "INSERT INTO group_members (group_id, user_id) VALUES (?, ?) "
                    + "ON CONFLICT DO NOTHING";

            for (String memberId : memberUserIds) {
                // Garante que o usuário existe (stub se necessário)
                try (PreparedStatement ps = conn.prepareStatement(upsertUser)) {
                    ps.setString(1, memberId);
                    ps.setString(2, memberId);
                    ps.executeUpdate();
                }
                // Associa o usuário ao grupo
                try (PreparedStatement ps = conn.prepareStatement(insertMember)) {
                    ps.setString(1, groupId);
                    ps.setString(2, memberId);
                    ps.executeUpdate();
                }
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
            conn.close();
        }
    }

    // ── Listagem de grupos ────────────────────────────────────────────────

    /**
     * Retorna todos os grupos com seus respectivos membros.
     */
    public List<GroupData> listGroups() throws SQLException {
        List<GroupData> groups = new ArrayList<>();

        String sqlGroups = "SELECT id, name FROM groups ORDER BY created_at";
        String sqlMembers = "SELECT user_id FROM group_members WHERE group_id = ? ORDER BY joined_at";

        try (Connection conn = pgManager.getConnection();
             PreparedStatement psGroups = conn.prepareStatement(sqlGroups);
             ResultSet rsGroups = psGroups.executeQuery()) {

            while (rsGroups.next()) {
                String gId = rsGroups.getString("id");
                String gName = rsGroups.getString("name");

                List<String> members = new ArrayList<>();
                try (PreparedStatement psMembers = conn.prepareStatement(sqlMembers)) {
                    psMembers.setString(1, gId);
                    try (ResultSet rsMembers = psMembers.executeQuery()) {
                        while (rsMembers.next()) {
                            members.add(rsMembers.getString("user_id"));
                        }
                    }
                }

                groups.add(new GroupData(gId, gName, members));
            }
        }

        return groups;
    }
}
