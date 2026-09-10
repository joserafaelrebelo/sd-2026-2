package com.sd.rateio.client;

import com.sd.rateio.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ClientMain {
    public static void main(String[] args) throws Exception {
        boolean runTestOnly = false;
        boolean skipSeed = "true".equalsIgnoreCase(System.getenv("RATEIO_SKIP_SEED"));

        String grpcHost = "127.0.0.1";
        int grpcPort = 50051;
        String webBindAddress = "0.0.0.0";
        int webPort = 8080;

        // Leitura de variáveis de ambiente
        if (System.getenv("GRPC_SERVER_HOST") != null && !System.getenv("GRPC_SERVER_HOST").isBlank()) {
            grpcHost = System.getenv("GRPC_SERVER_HOST").trim();
        }
        if (System.getenv("GRPC_SERVER_PORT") != null && !System.getenv("GRPC_SERVER_PORT").isBlank()) {
            try {
                grpcPort = Integer.parseInt(System.getenv("GRPC_SERVER_PORT").trim());
            } catch (NumberFormatException ignored) {}
        }
        if (System.getenv("WEB_BIND_ADDRESS") != null && !System.getenv("WEB_BIND_ADDRESS").isBlank()) {
            webBindAddress = System.getenv("WEB_BIND_ADDRESS").trim();
        }
        if (System.getenv("WEB_PORT") != null && !System.getenv("WEB_PORT").isBlank()) {
            try {
                webPort = Integer.parseInt(System.getenv("WEB_PORT").trim());
            } catch (NumberFormatException ignored) {}
        }

        // Argumentos de linha de comando
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--test".equals(arg)) {
                runTestOnly = true;
            } else if ("--no-seed".equals(arg)) {
                skipSeed = true;
            } else if (("--host".equals(arg) || "-h".equals(arg)) && i + 1 < args.length) {
                grpcHost = args[++i];
            } else if (("--port".equals(arg) || "-p".equals(arg)) && i + 1 < args.length) {
                try {
                    grpcPort = Integer.parseInt(args[++i]);
                } catch (NumberFormatException ignored) {}
            } else if ("--web-host".equals(arg) && i + 1 < args.length) {
                webBindAddress = args[++i];
            } else if ("--web-port".equals(arg) && i + 1 < args.length) {
                try {
                    webPort = Integer.parseInt(args[++i]);
                } catch (NumberFormatException ignored) {}
            }
        }

        System.out.println("[Cliente A - RATEIO] Conectando ao Servidor B gRPC em " + grpcHost + ":" + grpcPort + "...");

        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        ManagedChannel channel = NettyChannelBuilder
                .forAddress(new InetSocketAddress(grpcHost, grpcPort))
                .eventLoopGroup(eventLoopGroup)
                .channelType(NioSocketChannel.class)
                .usePlaintext()
                .build();

        BalanceServiceGrpc.BalanceServiceBlockingStub stub = BalanceServiceGrpc.newBlockingStub(channel);

        if (runTestOnly) {
            runAutomatedTest(stub);
            channel.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
            eventLoopGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS);
            return;
        }

        // Modo Padrão: Inicializar dados de exemplo e subir Web Gateway
        if (!skipSeed) {
            seedSampleData(stub);
        }

        WebGatewayServer webServer = new WebGatewayServer(webBindAddress, webPort, stub);
        webServer.start();

        System.out.println("\n=========================================================");
        System.out.println("          ✨ RATE.IO - INTERFACE WEB PRONTA ✨           ");
        System.out.println("=========================================================");
        System.out.println("  Servidor gRPC conectado: " + grpcHost + ":" + grpcPort);
        System.out.println("  Interface Web ativa em:  " + webBindAddress + ":" + webPort);
        System.out.println("  Acesse no seu navegador local:");
        System.out.println("  👉  http://localhost:" + webPort);
        System.out.println("  (Ou http://<IP_PUBLICO_DA_VM>:" + webPort + " na nuvem GCP)");
        System.out.println("=========================================================");
        System.out.println("  Pressione Ctrl+C para encerrar.\n");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Cliente A - RATEIO] Encerrando Web Gateway...");
            webServer.stop();
            try {
                channel.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
            eventLoopGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS);
        }));

        Thread.currentThread().join();
    }

    private static void seedSampleData(BalanceServiceGrpc.BalanceServiceBlockingStub stub) {
        try {
            String groupId = "viagem-rio";
            stub.createGroup(CreateGroupRequest.newBuilder()
                    .setGroupId(groupId)
                    .setGroupName("Viagem ao Rio")
                    .addAllMemberUserIds(List.of("alice", "bob", "carol"))
                    .build());

            stub.addExpense(ExpenseRequest.newBuilder()
                    .setGroupId(groupId)
                    .setPaidByUserId("alice")
                    .setAmount(300.0)
                    .addAllParticipantUserIds(List.of("alice", "bob", "carol"))
                    .setDescription("Hospedagem Copacabana")
                    .build());

            stub.addExpense(ExpenseRequest.newBuilder()
                    .setGroupId(groupId)
                    .setPaidByUserId("bob")
                    .setAmount(150.0)
                    .addAllParticipantUserIds(List.of("alice", "bob", "carol"))
                    .setDescription("Jantar Rodízio")
                    .build());

            System.out.println("[Cliente A - RATEIO] Grupo de exemplo 'Viagem ao Rio' verificado/inicializado.");
        } catch (Exception e) {
            System.out.println("[Cliente A - RATEIO] Informação ao inicializar dados de exemplo: " + e.getMessage());
        }
    }

    private static void runAutomatedTest(BalanceServiceGrpc.BalanceServiceBlockingStub stub) {
        System.out.println("=========================================================");
        System.out.println("          RATEIO gRPC - TESTE AUTOMATIZADO               ");
        System.out.println("=========================================================\n");

        String groupId = "teste-cli";

        System.out.println("[1] Criando grupo '" + groupId + "'...");
        CreateGroupResponse groupResp = stub.createGroup(CreateGroupRequest.newBuilder()
                .setGroupId(groupId)
                .setGroupName("Grupo de Teste CLI")
                .addAllMemberUserIds(List.of("alice", "bob", "carol"))
                .build());
        System.out.println("    Resultado: success=" + groupResp.getSuccess() + " | " + groupResp.getMessage());

        System.out.println("\n[2] Registrando Despesa 1 (Hospedagem - R$ 300,00 paga por Alice)...");
        stub.addExpense(ExpenseRequest.newBuilder()
                .setGroupId(groupId)
                .setPaidByUserId("alice")
                .setAmount(300.0)
                .addAllParticipantUserIds(List.of("alice", "bob", "carol"))
                .setDescription("Hospedagem")
                .build());

        System.out.println("\n[3] Registrando Despesa 2 (Jantar - R$ 150,00 paga por Bob)...");
        stub.addExpense(ExpenseRequest.newBuilder()
                .setGroupId(groupId)
                .setPaidByUserId("bob")
                .setAmount(150.0)
                .addAllParticipantUserIds(List.of("alice", "bob", "carol"))
                .setDescription("Jantar")
                .build());

        System.out.println("\n[4] Consultando balanços simplificados...");
        GroupBalancesResponse balances = stub.getGroupBalances(GroupBalancesRequest.newBuilder().setGroupId(groupId).build());
        for (DebtEntry d : balances.getDebtsList()) {
            System.out.println("    -> " + d.getFromUserId() + " deve pagar R$ " + String.format("%.2f", d.getAmount()) + " para " + d.getToUserId());
        }

        System.out.println("\n[5] Carol liquidando dívida com Alice...");
        stub.settleDebt(SettleDebtRequest.newBuilder()
                .setGroupId(groupId)
                .setFromUserId("carol")
                .setToUserId("alice")
                .setAmount(150.0)
                .build());

        GroupBalancesResponse balancesAfter = stub.getGroupBalances(GroupBalancesRequest.newBuilder().setGroupId(groupId).build());
        System.out.println("    Dívidas restantes: " + balancesAfter.getDebtsList().size());
        System.out.println("\n=========================================================");
        System.out.println("             TESTE CONCLUÍDO COM SUCESSO!                ");
        System.out.println("=========================================================");
    }
}

