package com.sd.rateio.gateway.controller;

import com.sd.rateio.gateway.dto.ApiResponse;
import com.sd.rateio.gateway.grpc.GrpcClientManager;
import com.sd.rateio.grpc.CreateGroupRequest;
import com.sd.rateio.grpc.CreateGroupResponse;
import com.sd.rateio.grpc.GroupInfo;
import com.sd.rateio.grpc.ListGroupsRequest;
import com.sd.rateio.grpc.ListGroupsResponse;
import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Controller REST para gerenciamento de grupos.
 * Rotas protegidas (exigem JWT válido).
 */
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GrpcClientManager grpcClientManager;

    public GroupController(GrpcClientManager grpcClientManager) {
        this.grpcClientManager = grpcClientManager;
    }

    /**
     * Lista todos os grupos existentes.
     * GET /api/groups
     */
    @GetMapping
    public ResponseEntity<?> listGroups() {
        try {
            ListGroupsResponse response = grpcClientManager.getUserGroupStub()
                    .listGroups(ListGroupsRequest.newBuilder().build());

            List<Map<String, Object>> groups = new ArrayList<>();
            for (GroupInfo group : response.getGroupsList()) {
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("groupId", group.getGroupId());
                g.put("groupName", group.getGroupName());
                g.put("members", group.getMemberUserIdsList());
                groups.add(g);
            }

            return ResponseEntity.ok(groups);
        } catch (StatusRuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Erro gRPC: " + e.getStatus().getDescription()));
        }
    }

    /**
     * Cria um novo grupo.
     * POST /api/groups
     * Body esperado: { "groupId": "...", "groupName": "...", "members": ["user1", "user2"] }
     */
    @PostMapping
    public ResponseEntity<?> createGroup(@RequestBody Map<String, Object> body) {
        String groupId = getStringField(body, "groupId");
        String groupName = getStringField(body, "groupName");

        // Validação manual dos campos obrigatórios
        if (isBlank(groupId) || isBlank(groupName)) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Campos groupId e groupName são obrigatórios"));
        }

        // Extrai lista de membros (pode ser nula/vazia)
        List<String> members = extractStringList(body, "members");

        try {
            CreateGroupResponse response = grpcClientManager.getUserGroupStub()
                    .createGroup(CreateGroupRequest.newBuilder()
                            .setGroupId(groupId)
                            .setGroupName(groupName)
                            .addAllMemberUserIds(members)
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

    // ======================== Helpers ========================

    private String getStringField(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value != null ? value.toString().trim() : null;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
