package com.sd.rateio.gateway.dto;

/**
 * DTO genérico para respostas padronizadas da API REST.
 */
public record ApiResponse(boolean success, String message) {}
