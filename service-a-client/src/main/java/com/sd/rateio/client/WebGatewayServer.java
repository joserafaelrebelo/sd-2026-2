package com.sd.rateio.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sd.rateio.grpc.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Servidor Web / API Gateway do RATE.IO embutido no client.
 * Converte requisições HTTP REST da interface web em chamadas gRPC para o service-b-server.
 */
public class WebGatewayServer {
    private final String bindAddress;
    private final int port;
    private final BalanceServiceGrpc.BalanceServiceBlockingStub stub;
    private final Gson gson = new Gson();
    private HttpServer server;

    public WebGatewayServer(int port, BalanceServiceGrpc.BalanceServiceBlockingStub stub) {
        this("0.0.0.0", port, stub);
    }

    public WebGatewayServer(String bindAddress, int port, BalanceServiceGrpc.BalanceServiceBlockingStub stub) {
        this.bindAddress = (bindAddress != null && !bindAddress.isBlank()) ? bindAddress : "0.0.0.0";
        this.port = port;
        this.stub = stub;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        server.setExecutor(Executors.newFixedThreadPool(10));

        // Roteamento estático e API
        server.createContext("/", new StaticHandler());
        server.createContext("/api/groups", new GroupsHandler());
        server.createContext("/api/expenses", new ExpensesHandler());
        server.createContext("/api/settle", new SettleHandler());
        server.createContext("/api/group-details", new GroupDetailsHandler());

        server.start();
        System.out.println("[Web Gateway - RATE.IO] Interface Web rodando em http://" + (bindAddress.equals("0.0.0.0") ? "0.0.0.0" : bindAddress) + ":" + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
        }
    }

    // =========================================================================
    // Handlers HTTP
    // =========================================================================

    private class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCors(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            String resourcePath = "web" + path;
            InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);

            if (is == null) {
                resourcePath = "web/index.html";
                is = getClass().getClassLoader().getResourceAsStream(resourcePath);
            }

            if (is == null) {
                sendResponse(exchange, 404, "text/plain", "404 Not Found".getBytes(StandardCharsets.UTF_8));
                return;
            }

            String mime = "text/plain";
            if (path.endsWith(".html")) mime = "text/html; charset=UTF-8";
            else if (path.endsWith(".css")) mime = "text/css; charset=UTF-8";
            else if (path.endsWith(".js")) mime = "application/javascript; charset=UTF-8";
            else if (path.endsWith(".json")) mime = "application/json; charset=UTF-8";
            else if (path.endsWith(".svg")) mime = "image/svg+xml";

            byte[] bytes = is.readAllBytes();
            is.close();
            sendResponse(exchange, 200, mime, bytes);
        }
    }

    private class GroupsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendCors(exchange);
            String method = exchange.getRequestMethod();

            try {
                if ("OPTIONS".equalsIgnoreCase(method)) {
                    exchange.sendResponseHeaders(204, -1);
                } else if ("GET".equalsIgnoreCase(method)) {
                    ListGroupsResponse resp = stub.listGroups(ListGroupsRequest.newBuilder().build());
                    JsonArray arr = new JsonArray();
                    for (GroupInfo g : resp.getGroupsList()) {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("groupId", g.getGroupId());
                        obj.addProperty("groupName", g.getGroupName());
                        JsonArray members = new JsonArray();
                        for (String m : g.getMemberUserIdsList()) {
                            members.add(m);
                        }
                        obj.add("members", members);
                        arr.add(obj);
                    }
                    sendJson(exchange, 200, arr);
                } else if ("POST".equalsIgnoreCase(method)) {
                    JsonObject body = parseJsonBody(exchange);
                    String groupId = body.has("groupId") ? body.get("groupId").getAsString() : "";
                    String groupName = body.has("groupName") ? body.get("groupName").getAsString() : "";
                    List<String> members = new ArrayList<>();
                    if (body.has("members") && body.get("members").isJsonArray()) {
                        for (JsonElement el : body.getAsJsonArray("members")) {
                            members.add(el.getAsString().trim());
                        }
                    }

                    CreateGroupResponse resp = stub.createGroup(CreateGroupRequest.newBuilder()
                            .setGroupId(groupId)
                            .setGroupName(groupName)
                            .addAllMemberUserIds(members)
                            .build());

                    JsonObject res = new JsonObject();
                    res.addProperty("success", resp.getSuccess());
                    res.addProperty("message", resp.getMessage());
                    sendJson(exchange, resp.getSuccess() ? 200 : 400, res);
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
            } catch (Exception e) {
                sendError(exchange, e);
            }
        }
    }

    private class GroupDetailsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            try {
                String query = exchange.getRequestURI().getQuery();
                String groupId = "";
                if (query != null && query.contains("groupId=")) {
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=");
                        if (pair.length == 2 && pair[0].equals("groupId")) {
                            groupId = pair[1];
                        }
                    }
                }

                if (groupId.isEmpty()) {
                    JsonObject err = new JsonObject();
                    err.addProperty("error", "groupId param required");
                    sendJson(exchange, 400, err);
                    return;
                }

                GroupExpensesResponse expResp = stub.getGroupExpenses(GroupExpensesRequest.newBuilder()
                        .setGroupId(groupId).build());

                JsonArray expensesArr = new JsonArray();
                double totalExpensesAmount = 0.0;
                for (ExpenseRecord e : expResp.getExpensesList()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("transactionId", e.getTransactionId());
                    o.addProperty("paidBy", e.getPaidByUserId());
                    o.addProperty("amount", e.getAmount());
                    o.addProperty("sharePerPerson", e.getSharePerPerson());
                    o.addProperty("description", e.getDescription());
                    o.addProperty("timestamp", e.getTimestamp());
                    JsonArray parts = new JsonArray();
                    for (String p : e.getParticipantUserIdsList()) {
                        parts.add(p);
                    }
                    o.add("participants", parts);
                    expensesArr.add(o);
                    totalExpensesAmount += e.getAmount();
                }

                GroupBalancesResponse balResp = stub.getGroupBalances(GroupBalancesRequest.newBuilder()
                        .setGroupId(groupId).build());

                JsonArray debtsArr = new JsonArray();
                for (DebtEntry d : balResp.getDebtsList()) {
                    JsonObject dObj = new JsonObject();
                    dObj.addProperty("fromUser", d.getFromUserId());
                    dObj.addProperty("toUser", d.getToUserId());
                    dObj.addProperty("amount", d.getAmount());
                    debtsArr.add(dObj);
                }

                JsonObject result = new JsonObject();
                result.addProperty("groupId", groupId);
                result.addProperty("totalExpensesAmount", totalExpensesAmount);
                result.add("expenses", expensesArr);
                result.add("debts", debtsArr);

                sendJson(exchange, 200, result);
            } catch (Exception e) {
                sendError(exchange, e);
            }
        }
    }

    private class ExpensesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendCors(exchange);
            String method = exchange.getRequestMethod();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                JsonObject body = parseJsonBody(exchange);
                String groupId = body.has("groupId") ? body.get("groupId").getAsString() : "";
                String paidBy = body.has("paidBy") ? body.get("paidBy").getAsString() : "";
                double amount = body.has("amount") ? body.get("amount").getAsDouble() : 0.0;
                String description = body.has("description") ? body.get("description").getAsString() : "";

                List<String> participants = new ArrayList<>();
                if (body.has("participants") && body.get("participants").isJsonArray()) {
                    for (JsonElement el : body.getAsJsonArray("participants")) {
                        participants.add(el.getAsString().trim());
                    }
                }

                ExpenseResponse resp = stub.addExpense(ExpenseRequest.newBuilder()
                        .setGroupId(groupId)
                        .setPaidByUserId(paidBy)
                        .setAmount(amount)
                        .addAllParticipantUserIds(participants)
                        .setDescription(description)
                        .build());

                JsonObject res = new JsonObject();
                res.addProperty("success", resp.getSuccess());
                res.addProperty("message", resp.getMessage());
                res.addProperty("transactionId", resp.getTransactionId());

                sendJson(exchange, resp.getSuccess() ? 200 : 400, res);
            } catch (Exception e) {
                sendError(exchange, e);
            }
        }
    }

    private class SettleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendCors(exchange);
            String method = exchange.getRequestMethod();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                JsonObject body = parseJsonBody(exchange);
                String groupId = body.has("groupId") ? body.get("groupId").getAsString() : "";
                String fromUser = body.has("fromUser") ? body.get("fromUser").getAsString() : "";
                String toUser = body.has("toUser") ? body.get("toUser").getAsString() : "";
                double amount = body.has("amount") ? body.get("amount").getAsDouble() : 0.0;

                SettleDebtResponse resp = stub.settleDebt(SettleDebtRequest.newBuilder()
                        .setGroupId(groupId)
                        .setFromUserId(fromUser)
                        .setToUserId(toUser)
                        .setAmount(amount)
                        .build());

                JsonObject res = new JsonObject();
                res.addProperty("success", resp.getSuccess());
                res.addProperty("message", resp.getMessage());

                sendJson(exchange, resp.getSuccess() ? 200 : 400, res);
            } catch (Exception e) {
                sendError(exchange, e);
            }
        }
    }

    private JsonObject parseJsonBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        if (json.isBlank()) return new JsonObject();
        return gson.fromJson(json, JsonObject.class);
    }

    private void sendJson(HttpExchange exchange, int status, JsonElement json) throws IOException {
        byte[] bytes = gson.toJson(json).getBytes(StandardCharsets.UTF_8);
        sendResponse(exchange, status, "application/json; charset=UTF-8", bytes);
    }

    private void sendError(HttpExchange exchange, Exception e) throws IOException {
        JsonObject err = new JsonObject();
        err.addProperty("success", false);
        err.addProperty("error", e.getMessage());
        sendJson(exchange, 500, err);
    }

    private void sendResponse(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        OutputStream os = exchange.getResponseBody();
        os.write(body);
        os.close();
    }

    private void sendCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
}

