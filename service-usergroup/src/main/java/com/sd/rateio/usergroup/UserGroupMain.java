package com.sd.rateio.usergroup;

import com.sd.rateio.usergroup.db.PostgresManager;
import com.sd.rateio.usergroup.db.UserGroupRepository;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Ponto de entrada do microsserviço de Usuários & Grupos.
 * Configura o servidor gRPC com Netty (NIO explícito) e conecta ao PostgreSQL.
 */
public class UserGroupMain {

    private static final String TAG = "[Microsserviço Usuários & Grupos]";

    public static void main(String[] args) throws Exception {
        // ── Configuração via variáveis de ambiente (com valores padrão) ──
        String bind = env("GRPC_BIND_ADDRESS", "0.0.0.0");
        int port = Integer.parseInt(env("GRPC_PORT", "50051"));
        String dbUrl = env("DB_URL", "jdbc:postgresql://localhost:5432/rateio_users");
        String dbUser = env("DB_USER", "rateio");
        String dbPass = env("DB_PASS", "rateio123");

        // ── Sobrescreve com argumentos CLI, se fornecidos ──
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host":
                    if (i + 1 < args.length) bind = args[++i];
                    break;
                case "--port":
                    if (i + 1 < args.length) port = Integer.parseInt(args[++i]);
                    break;
            }
        }

        // ── Inicializa camada de persistência ──
        PostgresManager pgManager = new PostgresManager(dbUrl, dbUser, dbPass);
        pgManager.initSchema();

        UserGroupRepository repository = new UserGroupRepository(pgManager);

        // ── Configura e inicia servidor gRPC com NIO explícito ──
        NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();

        Server server = NettyServerBuilder
                .forAddress(new InetSocketAddress(bind, port))
                .channelType(NioServerSocketChannel.class)
                .bossEventLoopGroup(bossGroup)
                .workerEventLoopGroup(workerGroup)
                .addService(new UserGroupServiceImpl(repository))
                .build()
                .start();

        System.out.println(TAG + " Iniciado em " + bind + ":" + port);

        // ── Shutdown hook para encerramento gracioso ──
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println(TAG + " Encerrando servidor gRPC...");
            server.shutdown();
            try {
                if (!server.awaitTermination(10, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                server.shutdownNow();
            }
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            System.out.println(TAG + " Servidor encerrado.");
        }));

        // Bloqueia a thread principal até o servidor ser encerrado
        server.awaitTermination();
    }

    /**
     * Retorna o valor da variável de ambiente ou o valor padrão.
     */
    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
