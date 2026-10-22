package com.sd.rateio.usergroup;

import java.util.List;

/**
 * POJO que representa os dados de um grupo com seus membros.
 * Utilizado internamente para transferir dados entre repositório e serviço.
 */
public class GroupData {

    private final String groupId;
    private final String groupName;
    private final List<String> members;

    public GroupData(String groupId, String groupName, List<String> members) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.members = members;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public List<String> getMembers() {
        return members;
    }
}
