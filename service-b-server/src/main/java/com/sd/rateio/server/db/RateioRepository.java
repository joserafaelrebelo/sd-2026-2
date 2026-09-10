package com.sd.rateio.server.db;

import com.sd.rateio.server.ExpenseRecordData;
import com.sd.rateio.server.GroupData;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Repositório transacional em SQLite para persistência do RATEIO.
 */
public class RateioRepository {
    private final DatabaseManager dbManager;

    public RateioRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    // =========================================================================
    // Usuários e Grupos
    // =========================================================================

    public void ensureUser(Connection conn, String userId, String name) throws SQLException {
        String sql = "INSERT OR IGNORE INTO users (id, name) VALUES (?, ?);";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, name != null && !name.isBlank() ? name : userId);
            ps.executeUpdate();
        }
    }

    public boolean createGroup(String groupId, String groupName, List<String> memberIds) throws SQLException {
        try (Connection conn = dbManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String checkSql = "SELECT id FROM groups WHERE id = ?;";
                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setString(1, groupId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            conn.rollback();
                            return false;
                        }
                    }
                }

                String insertGroupSql = "INSERT INTO groups (id, name) VALUES (?, ?);";
                try (PreparedStatement ps = conn.prepareStatement(insertGroupSql)) {
                    ps.setString(1, groupId);
                    ps.setString(2, groupName);
                    ps.executeUpdate();
                }

                for (String member : memberIds) {
                    ensureUser(conn, member, member);
                    String insertMemberSql = "INSERT OR IGNORE INTO group_members (group_id, user_id) VALUES (?, ?);";
                    try (PreparedStatement ps = conn.prepareStatement(insertMemberSql)) {
                        ps.setString(1, groupId);
                        ps.setString(2, member);
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
            }
        }
    }

    public List<GroupData> listGroups() throws SQLException {
        Map<String, GroupData> map = new LinkedHashMap<>();
        String sql = """
            SELECT g.id, g.name, gm.user_id
            FROM groups g
            LEFT JOIN group_members gm ON g.id = gm.group_id
            ORDER BY g.created_at ASC;
        """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String gId = rs.getString("id");
                String gName = rs.getString("name");
                String userId = rs.getString("user_id");

                GroupData group = map.computeIfAbsent(gId, k -> new GroupData(gId, gName, new ArrayList<>()));
                if (userId != null) {
                    group.addMember(userId);
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    public void ensureGroupExists(Connection conn, String groupId, String paidBy, List<String> participants) throws SQLException {
        ensureUser(conn, paidBy, paidBy);
        for (String p : participants) {
            ensureUser(conn, p, p);
        }

        String insertGroupSql = "INSERT OR IGNORE INTO groups (id, name) VALUES (?, ?);";
        try (PreparedStatement ps = conn.prepareStatement(insertGroupSql)) {
            ps.setString(1, groupId);
            ps.setString(2, groupId);
            ps.executeUpdate();
        }

        List<String> all = new ArrayList<>(participants);
        if (!all.contains(paidBy)) all.add(paidBy);

        String insertMemberSql = "INSERT OR IGNORE INTO group_members (group_id, user_id) VALUES (?, ?);";
        try (PreparedStatement ps = conn.prepareStatement(insertMemberSql)) {
            for (String member : all) {
                ps.setString(1, groupId);
                ps.setString(2, member);
                ps.executeUpdate();
            }
        }
    }

    // =========================================================================
    // Despesas (ACID Transactions)
    // =========================================================================

    public void addExpense(String txId, String groupId, String paidBy, double amount,
                           List<String> participants, String description, double sharePerPerson) throws SQLException {
        try (Connection conn = dbManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                ensureGroupExists(conn, groupId, paidBy, participants);

                String insertExpenseSql = """
                    INSERT INTO expenses (id, group_id, paid_by_user_id, amount, description, created_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP);
                """;
                try (PreparedStatement ps = conn.prepareStatement(insertExpenseSql)) {
                    ps.setString(1, txId);
                    ps.setString(2, groupId);
                    ps.setString(3, paidBy);
                    ps.setDouble(4, amount);
                    ps.setString(5, description != null ? description : "");
                    ps.executeUpdate();
                }

                String insertSplitSql = """
                    INSERT INTO expense_splits (expense_id, user_id, share_amount)
                    VALUES (?, ?, ?);
                """;
                try (PreparedStatement ps = conn.prepareStatement(insertSplitSql)) {
                    for (String p : participants) {
                        ps.setString(1, txId);
                        ps.setString(2, p);
                        ps.setDouble(3, sharePerPerson);
                        ps.executeUpdate();
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<ExpenseRecordData> getGroupExpenses(String groupId) throws SQLException {
        List<ExpenseRecordData> list = new ArrayList<>();
        String sql = """
            SELECT id, group_id, paid_by_user_id, amount, description, created_at
            FROM expenses
            WHERE group_id = ?
            ORDER BY created_at ASC;
        """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String expId = rs.getString("id");
                    String paidBy = rs.getString("paid_by_user_id");
                    double amount = rs.getDouble("amount");
                    String desc = rs.getString("description");
                    Timestamp ts = rs.getTimestamp("created_at");
                    Instant instant = ts != null ? ts.toInstant() : Instant.now();

                    List<String> participants = new ArrayList<>();
                    double share = 0.0;
                    String splitSql = "SELECT user_id, share_amount FROM expense_splits WHERE expense_id = ?;";
                    try (PreparedStatement splitPs = conn.prepareStatement(splitSql)) {
                        splitPs.setString(1, expId);
                        try (ResultSet splitRs = splitPs.executeQuery()) {
                            while (splitRs.next()) {
                                participants.add(splitRs.getString("user_id"));
                                share = splitRs.getDouble("share_amount");
                            }
                        }
                    }

                    list.add(new ExpenseRecordData(expId, groupId, paidBy, amount, participants, desc, share, instant));
                }
            }
        }
        return list;
    }

    // =========================================================================
    // Liquidações (Settle Up)
    // =========================================================================

    public void settleDebt(String txId, String groupId, String fromUser, String toUser, double amount) throws SQLException {
        try (Connection conn = dbManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                ensureUser(conn, fromUser, fromUser);
                ensureUser(conn, toUser, toUser);

                String sql = """
                    INSERT INTO settlements (id, group_id, from_user_id, to_user_id, amount, created_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP);
                """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, txId);
                    ps.setString(2, groupId);
                    ps.setString(3, fromUser);
                    ps.setString(4, toUser);
                    ps.setDouble(5, amount);
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // =========================================================================
    // Reconstituição Contábil do Ledger a partir do Banco
    // =========================================================================

    public Map<String, Map<String, Double>> computeGroupLedger(String groupId) throws SQLException {
        Map<String, Map<String, Double>> groupLedger = new HashMap<>();

        try (Connection conn = dbManager.getConnection()) {
            String expSql = """
                SELECT e.paid_by_user_id, es.user_id AS participant, es.share_amount
                FROM expenses e
                JOIN expense_splits es ON e.id = es.expense_id
                WHERE e.group_id = ?
                ORDER BY e.created_at ASC;
            """;
            try (PreparedStatement ps = conn.prepareStatement(expSql)) {
                ps.setString(1, groupId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String paidBy = rs.getString("paid_by_user_id");
                        String participant = rs.getString("participant");
                        double share = rs.getDouble("share_amount");

                        if (!participant.equals(paidBy)) {
                            addDebt(groupLedger, participant, paidBy, share);
                        }
                    }
                }
            }

            String settleSql = """
                SELECT from_user_id, to_user_id, amount
                FROM settlements
                WHERE group_id = ?
                ORDER BY created_at ASC;
            """;
            try (PreparedStatement ps = conn.prepareStatement(settleSql)) {
                ps.setString(1, groupId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String fromUser = rs.getString("from_user_id");
                        String toUser = rs.getString("to_user_id");
                        double amount = rs.getDouble("amount");

                        reduceDebt(groupLedger, fromUser, toUser, amount);
                    }
                }
            }
        }

        return groupLedger;
    }

    public double computeNetBalance(String groupId, String userId) throws SQLException {
        Map<String, Map<String, Double>> ledger = computeGroupLedger(groupId);
        double balance = 0.0;

        Map<String, Double> myDebts = ledger.get(userId);
        if (myDebts != null) {
            for (double val : myDebts.values()) {
                balance -= val;
            }
        }

        for (Map.Entry<String, Map<String, Double>> entry : ledger.entrySet()) {
            if (!entry.getKey().equals(userId)) {
                Double owedToMe = entry.getValue().get(userId);
                if (owedToMe != null) {
                    balance += owedToMe;
                }
            }
        }

        return balance;
    }

    // =========================================================================
    // Helpers Contábeis
    // =========================================================================

    private void addDebt(Map<String, Map<String, Double>> groupLedger,
                         String debtor, String creditor, double amount) {
        double reverseDebt = getDebtAmount(groupLedger, creditor, debtor);

        if (reverseDebt > 0.001) {
            if (reverseDebt >= amount) {
                setDebt(groupLedger, creditor, debtor, reverseDebt - amount);
                return;
            } else {
                setDebt(groupLedger, creditor, debtor, 0.0);
                amount -= reverseDebt;
            }
        }

        groupLedger.computeIfAbsent(debtor, k -> new HashMap<>());
        groupLedger.get(debtor).merge(creditor, amount, Double::sum);
    }

    private void reduceDebt(Map<String, Map<String, Double>> groupLedger,
                            String fromUser, String toUser, double amount) {
        double currentDebt = getDebtAmount(groupLedger, fromUser, toUser);

        if (currentDebt >= amount) {
            setDebt(groupLedger, fromUser, toUser, currentDebt - amount);
        } else {
            setDebt(groupLedger, fromUser, toUser, 0.0);
            double excess = amount - currentDebt;
            if (excess > 0.001) {
                addDebt(groupLedger, toUser, fromUser, excess);
            }
        }
    }

    private double getDebtAmount(Map<String, Map<String, Double>> groupLedger, String debtor, String creditor) {
        Map<String, Double> debts = groupLedger.get(debtor);
        if (debts == null) return 0.0;
        Double val = debts.get(creditor);
        return val != null ? val : 0.0;
    }

    private void setDebt(Map<String, Map<String, Double>> groupLedger, String debtor, String creditor, double amount) {
        groupLedger.computeIfAbsent(debtor, k -> new HashMap<>());
        if (amount < 0.001) {
            groupLedger.get(debtor).remove(creditor);
        } else {
            groupLedger.get(debtor).put(creditor, amount);
        }
    }
}

