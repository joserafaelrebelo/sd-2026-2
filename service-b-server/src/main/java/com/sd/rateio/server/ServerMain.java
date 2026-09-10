package com.sd.rateio.server;

import com.sd.rateio.server.db.DatabaseManager;
import com.sd.rateio.server.db.RateioRepository;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

public class ServerMain {
    public static void main(String[] args) throws IOException, InterruptedException {
        String bindAddress = "0.0.0.0";
        int port = 50051;
        String dbPath = "data/rateio.db";

        // Variáveis de ambiente
        if (System.getenv("GRPC_SERVER_BIND_ADDRESS") != null && !System.getenv("GRPC_SERVER_BIND_ADDRESS").isBlank()) {
            bindAddress = System.getenv("GRPC_SERVER_BIND_ADDRESS").trim();
        }
        if (System.getenv("GRPC_SERVER_PORT") != null && !System.getenv("GRPC_SERVER_PORT").isBlank()) {
            try {
                port = Integer.parseInt(System.getenv("GRPC_SERVER_PORT").trim());
            } catch (NumberFormatException ignored) {}
        }
        if (System.getenv("RATEIO_DB_PATH") != null && !System.getenv("RATEIO_DB_PATH").isBlank()) {
            dbPath = System.getenv("RATEIO_DB_PATH").trim();
        }

        // Argumentos de linha de comando (sobrescrevem variáveis de ambiente se fornecidos)
        for (int i = 0; i < args.length; i++) {
            if ("--host".equals(args[i]) && i + 1 < args.length) {
                bindAddress = args[++i];
            } else if ("--port".equals(args[i]) && i + 1 < args.length) {
                try {
                    port = Integer.parseInt(args[++i]);
                } catch (NumberFormatException ignored) {}
            } else if ("--db".equals(args[i]) && i + 1 < args.length) {
                dbPath = args[++i];
            }
        }

        // Garantir diretório do banco de dados
        File dbFile = new File(dbPath);
        if (dbFile.getParentFile() != null) {
            dbFile.getParentFile().mkdirs();
        }

        // Inicializar Banco de Dados Relacional SQLite do RATEIO
        DatabaseManager dbManager = new DatabaseManager(dbPath);
        try {
            dbManager.initSchema();
        } catch (SQLException e) {
            System.err.println("[Servidor B - RATEIO] Erro crítico ao iniciar banco SQLite: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
        RateioRepository repository = new RateioRepository(dbManager);

        NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();

        Server server = NettyServerBuilder
                .forAddress(new InetSocketAddress(bindAddress, port))
                .bossEventLoopGroup(bossGroup)
                .workerEventLoopGroup(workerGroup)
                .channelType(NioServerSocketChannel.class)
                .addService(new BalanceServiceImpl(repository))
                .build()
                .start();

        System.out.println("[Servidor B - RATEIO] Iniciado em " + bindAddress + ":" + port + " com SQLite (" + dbPath + ")");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Servidor B - RATEIO] Shutdown recebido. Encerrando...");
            try {
                server.shutdownNow().awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
            bossGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS);
            workerGroup.shutdownGracefully(0, 2, TimeUnit.SECONDS);
        }));

        server.awaitTermination();
    }
}

