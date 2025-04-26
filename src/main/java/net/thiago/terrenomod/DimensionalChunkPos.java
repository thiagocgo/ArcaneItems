package net.thiago.terrenomod;

import java.util.Objects;
import net.minecraft.world.level.ChunkPos;

public class DimensionalChunkPos {
    private final String dimension;
    private final int x;
    private final int z;

    public DimensionalChunkPos(String dimension, int x, int z) {
        this.dimension = dimension;
        this.x = x;
        this.z = z;
    }

    // Construtor auxiliar a partir de um ChunkPos
    public DimensionalChunkPos(String dimension, ChunkPos chunkPos) {
        this(dimension, chunkPos.x, chunkPos.z);
    }

    public String getDimension() {
        return dimension;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DimensionalChunkPos that = (DimensionalChunkPos) o;
        return x == that.x && z == that.z && dimension.equals(that.dimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, x, z);
    }

    @Override
    public String toString() {
        // Usado para persistência (formato: dimension,x,z)
        return dimension + "," + x + "," + z;
    }
}
