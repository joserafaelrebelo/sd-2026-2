package com.sd.rateio.server;

import java.util.*;

/**
 * Implementa o algoritmo guloso clássico de simplificação de dívidas para o RATEIO.
 */
public class DebtSimplifier {

    public static class SimplifiedDebt {
        public final String fromUser; // devedor
        public final String toUser;   // credor
        public final double amount;

        public SimplifiedDebt(String fromUser, String toUser, double amount) {
            this.fromUser = fromUser;
            this.toUser = toUser;
            this.amount = amount;
        }

        @Override
        public String toString() {
            return fromUser + " -> " + toUser + " : R$ " + String.format("%.2f", amount);
        }
    }

    public static List<SimplifiedDebt> simplify(Map<String, Map<String, Double>> ledger) {
        Map<String, Double> netBalances = new HashMap<>();

        for (Map.Entry<String, Map<String, Double>> debtorEntry : ledger.entrySet()) {
            String debtor = debtorEntry.getKey();
            for (Map.Entry<String, Double> creditorEntry : debtorEntry.getValue().entrySet()) {
                String creditor = creditorEntry.getKey();
                double amount = creditorEntry.getValue();

                netBalances.merge(debtor, -amount, Double::sum);
                netBalances.merge(creditor, amount, Double::sum);
            }
        }

        PriorityQueue<double[]> debtors = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        PriorityQueue<double[]> creditors = new PriorityQueue<>((a, b) -> Double.compare(b[0], a[0]));

        List<String> userList = new ArrayList<>(netBalances.keySet());
        Collections.sort(userList);

        Map<Integer, String> indexToUser = new HashMap<>();
        for (int i = 0; i < userList.size(); i++) {
            indexToUser.put(i, userList.get(i));
            double bal = netBalances.get(userList.get(i));
            if (bal < -0.001) {
                debtors.add(new double[]{bal, i});
            } else if (bal > 0.001) {
                creditors.add(new double[]{bal, i});
            }
        }

        List<SimplifiedDebt> result = new ArrayList<>();

        while (!debtors.isEmpty() && !creditors.isEmpty()) {
            double[] debtor = debtors.poll();
            double[] creditor = creditors.poll();

            double debtAmt = -debtor[0];
            double creditAmt = creditor[0];

            double settle = Math.min(debtAmt, creditAmt);

            if (settle > 0.001) {
                result.add(new SimplifiedDebt(
                        indexToUser.get((int) debtor[1]),
                        indexToUser.get((int) creditor[1]),
                        Math.round(settle * 100.0) / 100.0
                ));
            }

            double remainingDebt = debtAmt - settle;
            double remainingCredit = creditAmt - settle;

            if (remainingDebt > 0.001) {
                debtors.add(new double[]{-remainingDebt, debtor[1]});
            }
            if (remainingCredit > 0.001) {
                creditors.add(new double[]{remainingCredit, creditor[1]});
            }
        }

        return result;
    }
}

