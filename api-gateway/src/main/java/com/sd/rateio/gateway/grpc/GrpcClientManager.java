package com.sd.rateio.gateway.grpc;

import com.sd.rateio.grpc.FinanceServiceGrpc;
import com.sd.rateio.grpc.UserGroupServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Gerencia os canais gRPC para os microsserviços de Usuários/Grupos e Finanças.
 *
 * Usa NioEventLoopGroup + NioSocketChannel explicitamente para evitar
 * falhas com epoll/IPv4 em Linux.
 */
@Component
public class GrpcClientManager {

    @Value("${grpc.usergroup.host:localhost}")
    private String userGroupHost;

    @Value("${grpc.usergroup.port:50051}")
    private int userGroupPort;

    @Value("${grpc.finance.host:localhost}")
    private String financeHost;

    @Value("${grpc.finance.port:50052}")
    private int financePort;

    private ManagedChannel userGroupChannel;
    private ManagedChannel financeChannel;

    private NioEventLoopGroup userGroupEventLoopGroup;
    private NioEventLoopGroup financeEventLoopGroup;

    private UserGroupServiceGrpc.UserGroupServiceBlockingStub userGroupStub;
    private FinanceServiceGrpc.FinanceServiceBlockingStub financeStub;

    /** Inicializa os canais gRPC e stubs na inicialização do Spring. */
    @PostConstruct
    public void init() {
        // Canal para o microsserviço de Usuários & Grupos
        userGroupEventLoopGroup = new NioEventLoopGroup();
        userGroupChannel = NettyChannelBuilder
                .forAddress(new InetSocketAddress(userGroupHost, userGroupPort))
                .eventLoopGroup(userGroupEventLoopGroup)
                .channelType(NioSocketChannel.class)
                .usePlaintext()
                .build();
        userGroupStub = UserGroupServiceGrpc.newBlockingStub(userGroupChannel);

        // Canal para o microsserviço de Finanças
        financeEventLoopGroup = new NioEventLoopGroup();
        financeChannel = NettyChannelBuilder
                .forAddress(new InetSocketAddress(financeHost, financePort))
                .eventLoopGroup(financeEventLoopGroup)
                .channelType(NioSocketChannel.class)
                .usePlaintext()
                .build();
        financeStub = FinanceServiceGrpc.newBlockingStub(financeChannel);
    }

    /** Encerra os canais gRPC e event loops ao destruir o bean. */
    @PreDestroy
    public void shutdown() {
        if (userGroupChannel != null && !userGroupChannel.isShutdown()) {
            userGroupChannel.shutdown();
            try {
                userGroupChannel.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                userGroupChannel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (financeChannel != null && !financeChannel.isShutdown()) {
            financeChannel.shutdown();
            try {
                financeChannel.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                financeChannel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (userGroupEventLoopGroup != null) {
            userGroupEventLoopGroup.shutdownGracefully();
        }
        if (financeEventLoopGroup != null) {
            financeEventLoopGroup.shutdownGracefully();
        }
    }

    /** Retorna o stub bloqueante do serviço de Usuários & Grupos. */
    public UserGroupServiceGrpc.UserGroupServiceBlockingStub getUserGroupStub() {
        return userGroupStub;
    }

    /** Retorna o stub bloqueante do serviço de Finanças. */
    public FinanceServiceGrpc.FinanceServiceBlockingStub getFinanceStub() {
        return financeStub;
    }
}
