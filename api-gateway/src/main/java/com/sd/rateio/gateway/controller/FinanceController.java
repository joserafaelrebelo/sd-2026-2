package com.sd.rateio.gateway.controller;

import com.sd.rateio.gateway.dto.ApiResponse;
import com.sd.rateio.gateway.grpc.GrpcClientManager;
import com.sd.rateio.grpc.*;
import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Controller REST para operações financeiras — despesas, saldos e liquidações.
 * Rotas protegidas (exigem JWT válido).
 */
@RestController
@RequestMapping("/api")
public class FinanceController {

    private final GrpcClientManager grpcClientManager;

    public FinanceController(GrpcClientManager grpcClientManager) {
        this.grpcClientManager = grpcClientManager;
    }

    /**
     * Registra uma nova despesa em um grupo.
     * POST /api/expenses
     * Body: { "groupId", "paidBy", "amount", "participants": [...], "description" }
     */
    @PostMapping("/expenses")
    public ResponseEntity<?> addExpense(@RequestBody Map<String, Object> body) {
        String groupId = getStringField(body, "groupId");
        String paidBy = getStringField(body, "paidBy");
        Double amount = getDoubleField(body, "amount");
        List<String> participants = extractStringList(body, "participants");
        String description = getStringField(body, "description");

        // Validação manual
        if (isBlank(groupId) || isBlank(paidBy)) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Campos groupId e paidBy são obrigatórios"));
        }
        if (amount == null || amount <= 0) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Campo amount deve ser maior que zero"));
        }
        if (participants.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Lista de participants não pode estar vazia"));
        }

        try {
            ExpenseRequest.Builder builder = ExpenseRequest.newBuilder()
                    .setGroupId(groupId)
                    .setPaidByUserId(paidBy)
                    .setAmount(amount)
                    .addAllParticipantUserIds(participants);

            if (description != null) {
                builder.setDescription(description);
            }

            ExpenseResponse response = grpcClientManager.getFinanceStub()
                    .addExpense(builder.build());

            if (response.getSuccess()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("message", response.getMessage());
                result.put("transactionId", response.getTransactionId());
                return ResponseEntity.status(HttpStatus.CREATED).body(result);
            } else {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, response.getMessage()));
            }
        } catch (StatusRuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Erro gRPC: " + e.getStatus().getDescription()));
        }
    }

    /**
     * Retorna detalhes de um grupo: despesas e dívidas.
     * GET /api/group-details?groupId=xxx
     */
    @GetMapping("/group-details")
    public ResponseEntity<?> getGroupDetails(@RequestParam(required = false) String groupId) {
        if (isBlank(groupId)) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Parâmetro groupId é obrigatório"));
        }

        try {
            // Busca despesas do grupo
            GroupExpensesResponse expensesResponse = grpcClientManager.getFinanceStub()
                    .getGroupExpenses(GroupExpensesRequest.newBuilder()
                            .setGroupId(groupId)
                            .build());

            // Busca saldos/dívidas do grupo
            GroupBalancesResponse balancesResponse = grpcClientManager.getFinanceStub()
                    .getGroupBalances(GroupBalancesRequest.newBuilder()
                            .setGroupId(groupId)
                            .build());

            // Monta a lista de despesas
            List<Map<String, Object>> expenses = new ArrayList<>();
            double totalExpensesAmount = 0.0;

            for (ExpenseRecord record : expensesResponse.getExpensesList()) {
                Map<String, Object> exp = new LinkedHashMap<>();
                exp.put("transactionId", record.getTransactionId());
                exp.put("groupId", record.getGroupId());
                exp.put("paidBy", record.getPaidByUserId());
                exp.put("amount", record.getAmount());
                exp.put("participants", record.getParticipantUserIdsList());
                exp.put("description", record.getDescription());
                exp.put("timestamp", record.getTimestamp());
                exp.put("sharePerPerson", record.getSharePerPerson());
                expenses.add(exp);
                totalExpensesAmount += record.getAmount();
            }

            // Monta a lista de dívidas
            List<Map<String, Object>> debts = new ArrayList<>();
            for (DebtEntry debt : balancesResponse.getDebtsList()) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("fromUser", debt.getFromUserId());
                d.put("toUser", debt.getToUserId());
                d.put("amount", debt.getAmount());
                debts.add(d);
            }

            // Resultado final
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("groupId", groupId);
            result.put("totalExpensesAmount", totalExpensesAmount);
            result.put("expenses", expenses);
            result.put("debts", debts);

            return ResponseEntity.ok(result);
        } catch (StatusRuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Erro gRPC: " + e.getStatus().getDescription()));
        }
    }

    /**
     * Liquida (settle) uma dívida entre dois usuários.
     * POST /api/settle
     * Body: { "groupId", "fromUser", "toUser", "amount" }
     */
    @PostMapping("/settle")
    public ResponseEntity<?> settleDebt(@RequestBody Map<String, Object> body) {
        String groupId = getStringField(body, "groupId");
        String fromUser = getStringField(body, "fromUser");
        String toUser = getStringField(body, "toUser");
        Double amount = getDoubleField(body, "amount");

        // Validação manual
        if (isBlank(groupId) || isBlank(fromUser) || isBlank(toUser)) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Campos groupId, fromUser e toUser são obrigatórios"));
        }
        if (amount == null || amount <= 0) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Campo amount deve ser maior que zero"));
        }

        try {
            SettleDebtResponse response = grpcClientManager.getFinanceStub()
                    .settleDebt(SettleDebtRequest.newBuilder()
                            .setGroupId(groupId)
                            .setFromUserId(fromUser)
                            .setToUserId(toUser)
                            .setAmount(amount)
                            .build());

            if (response.getSuccess()) {
                return ResponseEntity.ok(new ApiResponse(true, response.getMessage()));
            } else {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, response.getMessage()));
            }
        } catch (StatusRuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Erro gRPC: " + e.getStatus().getDescription()));
        }
    }

    // ======================== Helpers ========================

    private String getStringField(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value != null ? value.toString().trim() : null;
    }

    private Double getDoubleField(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) return null;
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
