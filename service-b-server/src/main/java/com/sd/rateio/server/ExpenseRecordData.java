package com.sd.rateio.server;

import java.time.Instant;
import java.util.List;

/**
 * Registro imutável de uma despesa para armazenamento em histórico do RATEIO.
 */
public class ExpenseRecordData {
    private final String transactionId;
    private final String groupId;
    private final String paidByUserId;
    private final double amount;
    private final List<String> participantUserIds;
    private final String description;
    private final Instant timestamp;
    private final double sharePerPerson;

    public ExpenseRecordData(String transactionId, String groupId, String paidByUserId,
                             double amount, List<String> participantUserIds,
                             String description, double sharePerPerson) {
        this(transactionId, groupId, paidByUserId, amount, participantUserIds, description, sharePerPerson, Instant.now());
    }

    public ExpenseRecordData(String transactionId, String groupId, String paidByUserId,
                             double amount, List<String> participantUserIds,
                             String description, double sharePerPerson, Instant timestamp) {
        this.transactionId = transactionId;
        this.groupId = groupId;
        this.paidByUserId = paidByUserId;
        this.amount = amount;
        this.participantUserIds = List.copyOf(participantUserIds);
        this.description = description;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.sharePerPerson = sharePerPerson;
    }

    public String getTransactionId() { return transactionId; }
    public String getGroupId() { return groupId; }
    public String getPaidByUserId() { return paidByUserId; }
    public double getAmount() { return amount; }
    public List<String> getParticipantUserIds() { return participantUserIds; }
    public String getDescription() { return description; }
    public Instant getTimestamp() { return timestamp; }
    public double getSharePerPerson() { return sharePerPerson; }
}

