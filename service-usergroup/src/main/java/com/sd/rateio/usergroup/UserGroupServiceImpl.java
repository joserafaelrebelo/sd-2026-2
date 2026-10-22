package com.sd.rateio.usergroup;

import com.sd.rateio.grpc.*;
import com.sd.rateio.usergroup.db.UserGroupRepository;

import io.grpc.stub.StreamObserver;

import java.sql.SQLException;
import java.util.List;

/**
 * Implementação do serviço gRPC UserGroupService.
 * Delega operações de persistência ao UserGroupRepository.
 */
public class UserGroupServiceImpl extends UserGroupServiceGrpc.UserGroupServiceImplBase {

    private static final String TAG = "[Microsserviço Usuários & Grupos]";
    private final UserGroupRepository repository;

    public UserGroupServiceImpl(UserGroupRepository repository) {
        this.repository = repository;
    }

    // ── Registra um novo usuário ──────────────────────────────────────────
    @Override
    public void registerUser(RegisterUserRequest request,
                             StreamObserver<RegisterUserResponse> responseObserver) {
        try {
            String userId = request.getUserId();
            String name = request.getName();
            String password = request.getPassword();

            // Validação de campos obrigatórios
            if (userId.isBlank() || name.isBlank() || password.isBlank()) {
                responseObserver.onNext(RegisterUserResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Campos user_id, name e password são obrigatórios.")
                        .build());
                responseObserver.onCompleted();
                return;
            }

            boolean created = repository.registerUser(userId, name, password);

            if (created) {
                System.out.println(TAG + " Usuário registrado: " + userId);
                responseObserver.onNext(RegisterUserResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("Usuário criado com sucesso (201).")
                        .build());
            } else {
                System.out.println(TAG + " Usuário já existe: " + userId);
                responseObserver.onNext(RegisterUserResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Usuário já existe.")
                        .build());
            }
        } catch (SQLException e) {
            System.err.println(TAG + " Erro ao registrar usuário: " + e.getMessage());
            responseObserver.onNext(RegisterUserResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Erro interno: " + e.getMessage())
                    .build());
        }
        responseObserver.onCompleted();
    }

    // ── Autentica um usuário ──────────────────────────────────────────────
    @Override
    public void authenticateUser(AuthenticateUserRequest request,
                                 StreamObserver<AuthenticateUserResponse> responseObserver) {
        try {
            String userId = request.getUserId();
            String password = request.getPassword();

            String[] result = repository.authenticateUser(userId, password);

            if (result != null) {
                System.out.println(TAG + " Autenticação bem-sucedida: " + userId);
                responseObserver.onNext(AuthenticateUserResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("Autenticação bem-sucedida.")
                        .setUserId(result[0])
                        .setName(result[1])
                        .build());
            } else {
                System.out.println(TAG + " Falha na autenticação: " + userId);
                responseObserver.onNext(AuthenticateUserResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Credenciais inválidas.")
                        .build());
            }
        } catch (SQLException e) {
            System.err.println(TAG + " Erro ao autenticar usuário: " + e.getMessage());
            responseObserver.onNext(AuthenticateUserResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Erro interno: " + e.getMessage())
                    .build());
        }
        responseObserver.onCompleted();
    }

    // ── Cria um novo grupo ────────────────────────────────────────────────
    @Override
    public void createGroup(CreateGroupRequest request,
                            StreamObserver<CreateGroupResponse> responseObserver) {
        try {
            String groupId = request.getGroupId();
            String groupName = request.getGroupName();
            List<String> members = request.getMemberUserIdsList();

            // Validação de campos obrigatórios
            if (groupId.isBlank() || groupName.isBlank()) {
                responseObserver.onNext(CreateGroupResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Campos group_id e group_name são obrigatórios.")
                        .build());
                responseObserver.onCompleted();
                return;
            }

            boolean created = repository.createGroup(groupId, groupName, members);

            if (created) {
                System.out.println(TAG + " Grupo criado: " + groupId);
                responseObserver.onNext(CreateGroupResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("Grupo criado com sucesso.")
                        .build());
            } else {
                System.out.println(TAG + " Grupo já existe: " + groupId);
                responseObserver.onNext(CreateGroupResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Grupo já existe.")
                        .build());
            }
        } catch (SQLException e) {
            System.err.println(TAG + " Erro ao criar grupo: " + e.getMessage());
            responseObserver.onNext(CreateGroupResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Erro interno: " + e.getMessage())
                    .build());
        }
        responseObserver.onCompleted();
    }

    // ── Lista todos os grupos ─────────────────────────────────────────────
    @Override
    public void listGroups(ListGroupsRequest request,
                           StreamObserver<ListGroupsResponse> responseObserver) {
        try {
            List<GroupData> groups = repository.listGroups();

            ListGroupsResponse.Builder responseBuilder = ListGroupsResponse.newBuilder();
            for (GroupData g : groups) {
                responseBuilder.addGroups(GroupInfo.newBuilder()
                        .setGroupId(g.getGroupId())
                        .setGroupName(g.getGroupName())
                        .addAllMemberUserIds(g.getMembers())
                        .build());
            }

            System.out.println(TAG + " Listando " + groups.size() + " grupo(s).");
            responseObserver.onNext(responseBuilder.build());
        } catch (SQLException e) {
            System.err.println(TAG + " Erro ao listar grupos: " + e.getMessage());
            responseObserver.onNext(ListGroupsResponse.newBuilder().build());
        }
        responseObserver.onCompleted();
    }
}
