package com.sd.rateio.server;

import com.sd.rateio.grpc.*;
import com.sd.rateio.server.db.DatabaseManager;
import com.sd.rateio.server.db.RateioRepository;
import io.grpc.stub.StreamObserver;

import java.sql.SQLException;
import java.util.*;

public class BalanceServiceImpl extends BalanceServiceGrpc.BalanceServiceImplBase {

    private final RateioRepository repository;

    public BalanceServiceImpl() {
        DatabaseManager dbManager = new DatabaseManager("data/rateio.db");
        try {
            dbManager.initSchema();
        } catch (SQLException e) {
            System.err.println("[Servidor B - RATEIO] Erro ao inicializar esquema SQLite: " + e.getMessage());
        }
        this.repository = new RateioRepository(dbManager);
    }

    public BalanceServiceImpl(RateioRepository repository) {
        this.repository = repository;
    }

    // ========================= CreateGroup =========================

    @Override
    public void createGroup(CreateGroupRequest req, StreamObserver<CreateGroupResponse> responseObserver) {
        String groupId = req.getGroupId();
        String groupName = req.getGroupName();
        List<String> members = req.getMemberUserIdsList();

        if (groupId.isEmpty() || groupName.isEmpty()) {
            responseObserver.onNext(CreateGroupResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("group_id and group_name are required")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        try {
            boolean created = repository.createGroup(groupId, groupName, members);
            if (!created) {
                responseObserver.onNext(CreateGroupResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Group '" + groupId + "' already exists")
                        .build());
                responseObserver.onCompleted();
                return;
            }

            System.out.println("[Servidor B - RATEIO] Grupo persistido no SQLite: " + groupName + " (" + groupId + ") com membros: " + members);

            responseObserver.onNext(CreateGroupResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Group '" + groupName + "' created successfully")
                    .build());
            responseObserver.onCompleted();
        } catch (SQLException e) {
            System.err.println("[Servidor B - RATEIO] Erro SQL ao criar grupo: " + e.getMessage());
            responseObserver.onNext(CreateGroupResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Database error: " + e.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    // ========================= ListGroups =========================

    @Override
    public void listGroups(ListGroupsRequest req, StreamObserver<ListGroupsResponse> responseObserver) {
        ListGroupsResponse.Builder builder = ListGroupsResponse.newBuilder();

        try {
            List<GroupData> groups = repository.listGroups();
            for (GroupData g : groups) {
                builder.addGroups(GroupInfo.newBuilder()
                        .setGroupId(g.getGroupId())
                        .setGroupName(g.getGroupName())
                        .addAllMemberUserIds(g.getMembers())
                        .build());
            }
        } catch (SQLException e) {
            System.err.println("[Servidor B - RATEIO] Erro SQL ao listar grupos: " + e.getMessage());
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    // ========================= AddExpense =========================

    @Override
    public void addExpense(ExpenseRequest req, StreamObserver<ExpenseResponse> responseObserver) {
        String groupId = req.getGroupId();
        String paidBy = req.getPaidByUserId();
        double amount = req.getAmount();
        List<String> participants = req.getParticipantUserIdsList();
        int n = participants.size();

        if (amount <= 0 || n == 0) {
            responseObserver.onNext(ExpenseResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Invalid amount or empty participants")
                    .setTransactionId("")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (paidBy.isEmpty()) {
            responseObserver.onNext(ExpenseResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("paid_by_user_id is required")
                    .setTransactionId("")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        double share = amount / n;
        String txId = UUID.randomUUID().toString();

        try {
            repository.addExpense(txId, groupId, paidBy, amount, participants, req.getDescription(), share);

            System.out.println("[Servidor B - RATEIO] Despesa persistida no SQLite: R$ " + String.format("%.2f", amount)
                    + " por " + paidBy + " no grupo " + groupId
                    + " (dividida entre " + n + " pessoas, R$ " + String.format("%.2f", share) + " cada)"
                    + " (tx: " + txId + ")");

            responseObserver.onNext(ExpenseResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Expense recorded")
                    .setTransactionId(txId)
                    .build());
            responseObserver.onCompleted();
        } catch (SQLException e) {
            System.err.println("[Servidor B - RATEIO] Erro SQL ao registrar despesa: " + e.getMessage());
            responseObserver.onNext(ExpenseResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Database error: " + e.getMessage())
                    .setTransactionId("")
                    .build());
            responseObserver.onCompleted();
        }
    }

    // ========================= GetUserBalance =========================

    @Override
    public void getUserBalance(BalanceRequest req, StreamObserver<BalanceResponse> responseObserver) {
        String groupId = req.getGroupId();
        String userId = req.getUserId();

        double netBalance = 0.0;
        try {
            netBalance = repository.computeNetBalance(groupId, userId);
        } catch (SQLException e) {
            System.err.println("[Servidor B - RATEIO] Erro SQL ao consultar saldo do usuário: " + e.getMessage());
        }

        responseObserver.onNext(BalanceResponse.newBuilder()
                .setUserId(userId)
                .setTotalBalance(netBalance)
                .setCurrency("BRL")
                .build());
        responseObserver.onCompleted();
    }

    // ========================= SettleDebt =========================

    @Override
    public void settleDebt(SettleDebtRequest req, StreamObserver<SettleDebtResponse> responseObserver) {
        String groupId = req.getGroupId();
        String fromUser = req.getFromUserId();
        String toUser = req.getToUserId();
        double amount = req.getAmount();

        if (amount <= 0) {
            responseObserver.onNext(SettleDebtResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Amount must be positive")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (fromUser.isEmpty() || toUser.isEmpty()) {
            responseObserver.onNext(SettleDebtResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("from_user_id and to_user_id are required")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (fromUser.equals(toUser)) {
            responseObserver.onNext(SettleDebtResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("from_user_id and to_user_id cannot be the same")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        String txId = UUID.randomUUID().toString();

        try {
            repository.settleDebt(txId, groupId, fromUser, toUser, amount);

            System.out.println("[Servidor B - RATEIO] Liquidação persistida no SQLite: " + fromUser + " pagou R$ "
                    + String.format("%.2f", amount) + " para " + toUser + " no grupo " + groupId);

            responseObserver.onNext(SettleDebtResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage(fromUser + " settled R$ " + String.format("%.2f", amount) + " with " + toUser)
                    .build());
            responseObserver.onCompleted();
        } catch (SQLException e) {
            System.err.println("[Servidor B - RATEIO] Erro SQL ao liquidar dívida: " + e.getMessage());
            responseObserver.onNext(SettleDebtResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Database error: " + e.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    // ========================= GetGroupExpenses =========================

    @Override
    public void getGroupExpenses(GroupExpensesRequest req, StreamObserver<GroupExpensesResponse> responseObserver) {
        String groupId = req.getGroupId();
        GroupExpensesResponse.Builder builder = GroupExpensesResponse.newBuilder();

        try {
            List<ExpenseRecordData> expenses = repository.getGroupExpenses(groupId);
            for (ExpenseRecordData e : expenses) {
                builder.addExpenses(ExpenseRecord.newBuilder()
                        .setTransactionId(e.getTransactionId())
                        .setGroupId(e.getGroupId())
                        .setPaidByUserId(e.getPaidByUserId())
                        .setAmount(e.getAmount())
                        .addAllParticipantUserIds(e.getParticipantUserIds())
                        .setDescription(e.getDescription())
                        .setTimestamp(e.getTimestamp().toString())
                        .setSharePerPerson(e.getSharePerPerson())
                        .build());
            }
        } catch (SQLException e) {
            System.err.println("[Servidor B - RATEIO] Erro SQL ao buscar despesas do grupo: " + e.getMessage());
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    // ========================= GetGroupBalances =========================

    @Override
    public void getGroupBalances(GroupBalancesRequest req, StreamObserver<GroupBalancesResponse> responseObserver) {
        String groupId = req.getGroupId();
        GroupBalancesResponse.Builder builder = GroupBalancesResponse.newBuilder().setGroupId(groupId);

        try {
            Map<String, Map<String, Double>> ledgerSnapshot = repository.computeGroupLedger(groupId);

            List<DebtSimplifier.SimplifiedDebt> simplified = DebtSimplifier.simplify(ledgerSnapshot);

            for (DebtSimplifier.SimplifiedDebt debt : simplified) {
                builder.addDebts(DebtEntry.newBuilder()
                        .setFromUserId(debt.fromUser)
                        .setToUserId(debt.toUser)
                        .setAmount(debt.amount)
                        .build());
            }
        } catch (SQLException e) {
            System.err.println("[Servidor B - RATEIO] Erro SQL ao computar balanços simplificados: " + e.getMessage());
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }
}

