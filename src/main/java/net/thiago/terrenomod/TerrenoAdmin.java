package net.thiago.terrenomod;

import java.util.Set;
import java.util.UUID;

public class TerrenoAdmin {
    private final String name;
    private final UUID owner;
    private final Set<DimensionalChunkPos> chunks;

    public TerrenoAdmin(String name, UUID owner, Set<DimensionalChunkPos> chunks) {
        this.name = name;
        this.owner = owner;
        this.chunks = chunks;
    }

    public String getName() {
        return name;
    }

    public UUID getOwner() {
        return owner;
    }

    public Set<DimensionalChunkPos> getChunks() {
        return chunks;
    }
}
