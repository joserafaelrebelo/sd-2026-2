package com.sd.rateio.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada do API Gateway (Spring Boot).
 * Recebe requisições REST do frontend e as encaminha via gRPC
 * para os microsserviços de Usuários/Grupos e Finanças.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
