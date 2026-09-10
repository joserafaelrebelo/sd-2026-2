package com.sd.rateio.server;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Dados de um grupo de RATEIO em memória/cache.
 */
public class GroupData {
    private final String groupId;
    private final String groupName;
    private final Set<String> members;

    public GroupData(String groupId, String groupName, List<String> members) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.members = new CopyOnWriteArraySet<>(members);
    }

    public String getGroupId() { return groupId; }
    public String getGroupName() { return groupName; }
    public Set<String> getMembers() { return members; }

    public void addMember(String userId) {
        members.add(userId);
    }
}

