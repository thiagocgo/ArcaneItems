package net.thiago.items.guild;

import java.util.*;

public class GuildInfo {
    private final UUID leader;
    private final Set<UUID> members;

    public GuildInfo(UUID leader) {
        this.leader = leader;
        this.members = new HashSet<>();
    }

    public UUID getLeader() {
        return leader;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public void addMember(UUID memberUUID) {
        members.add(memberUUID);
    }

    public void removeMember(UUID memberUUID) {
        members.remove(memberUUID);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }
}
