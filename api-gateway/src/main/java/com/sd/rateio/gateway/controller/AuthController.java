package com.sd.rateio.gateway.controller;

import com.sd.rateio.gateway.dto.ApiResponse;
import com.sd.rateio.gateway.grpc.GrpcClientManager;
import com.sd.rateio.gateway.security.JwtTokenProvider;
import com.sd.rateio.grpc.AuthenticateUserRequest;
import com.sd.rateio.grpc.AuthenticateUserResponse;
import com.sd.rateio.grpc.RegisterUserRequest;
import com.sd.rateio.grpc.RegisterUserResponse;
import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller REST para autenticação — registro e login de usuários.
 * Rotas públicas (não exigem JWT).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final GrpcClientManager grpcClientManager;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(GrpcClientManager grpcClientManager, JwtTokenProvider jwtTokenProvider) {
        this.grpcClientManager = grpcClientManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Registra um novo usuário.
     * Body esperado: { "userId": "...", "name": "...", "password": "..." }
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> body) {
        String userId = getStringField(body, "userId");
        String name = getStringField(body, "name");
        String password = getStringField(body, "password");

        // Validação manual dos campos obrigatórios
        if (isBlank(userId) || isBlank(name) || isBlank(password)) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Campos userId, name e password são obrigatórios"));
        }

        try {
            RegisterUserResponse response = grpcClientManager.getUserGroupStub()
                    .registerUser(RegisterUserRequest.newBuilder()
                            .setUserId(userId)
                            .setName(name)
                            .setPassword(password)
                            .build());

            if (response.getSuccess()) {
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(new ApiResponse(true, response.getMessage()));
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
     * Autentica um usuário e retorna um token JWT.
     * Body esperado: { "userId": "...", "password": "..." }
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> body) {
        String userId = getStringField(body, "userId");
        String password = getStringField(body, "password");

        // Validação manual dos campos obrigatórios
        if (isBlank(userId) || isBlank(password)) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Campos userId e password são obrigatórios"));
        }

        try {
            AuthenticateUserResponse response = grpcClientManager.getUserGroupStub()
                    .authenticateUser(AuthenticateUserRequest.newBuilder()
                            .setUserId(userId)
                            .setPassword(password)
                            .build());

            if (response.getSuccess()) {
                // Gera token JWT com userId e nome retornado pelo microsserviço
                String token = jwtTokenProvider.generateToken(response.getUserId(), response.getName());

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("token", token);
                result.put("userId", response.getUserId());
                result.put("name", response.getName());
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse(false, "Credenciais inválidas"));
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

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
