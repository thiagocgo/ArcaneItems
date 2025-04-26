package net.thiago.terrenomod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.world.level.ChunkPos;
import net.thiago.items.ClasseGeral;
import net.thiago.items.ClasseGeral.ProtectionInfo;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.Collectors;

public class TerrainDataHandler {
    private static final File DATA_FILE = new File("config/arcaneitems/terrain_data.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static class TerrainData {
        Set<String> protectedDimensions = new HashSet<>();
        Map<String, ProtectionInfoData> protectedChunks = new HashMap<>();
        Map<String, Integer> playerTerrainLimits = new HashMap<>();
        Map<String, Integer> playerStoreTerrainLimits = new HashMap<>();
        Map<String, Boolean> dimensionPvpStatus = new HashMap<>(); // Novo campo
    }

    private static class ProtectionInfoData {
        String owner;
        Set<String> allies;
        String terrainName;
        boolean pvpAllowed;
        int sizeX;
        int sizeZ;
        int allyLimit;
    }

    public static void loadData() {
        if (!DATA_FILE.exists()) return;
        try (FileReader reader = new FileReader(DATA_FILE)) {
            TerrainData data = GSON.fromJson(reader, TerrainData.class);
            if (data != null) {
                ClasseGeral.protectedDimensions = data.protectedDimensions;
                ClasseGeral.protectedChunks.clear();
                for (Map.Entry<String, ProtectionInfoData> entry : data.protectedChunks.entrySet()) {
                    String key = entry.getKey();
                    String[] parts = key.split(",");
                    if (parts.length < 3) continue;
                    String dimension = parts[0];
                    int x = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, x, z);

                    ProtectionInfoData infoData = entry.getValue();
                    UUID ownerUUID = UUID.fromString(infoData.owner);
                    ProtectionInfo info = new ProtectionInfo(ownerUUID, infoData.terrainName, infoData.sizeX, infoData.sizeZ, infoData.allyLimit);
                    info.setPvpAllowed(infoData.pvpAllowed);
                    if (infoData.allies != null) {
                        for (String allyStr : infoData.allies) {
                            info.getAllies().add(UUID.fromString(allyStr));
                        }
                    }
                    ClasseGeral.protectedChunks.put(dcp, info);
                }
                // Carregar dimensionPvpStatus
                ClasseGeral.dimensionPvpStatus.clear();
                if (data.dimensionPvpStatus != null) {
                    ClasseGeral.dimensionPvpStatus.putAll(data.dimensionPvpStatus);
                }
                // Carregar limites de terrenos
                ClasseGeral.playerTerrainLimits.clear();
                if (data.playerTerrainLimits != null) {
                    for (Map.Entry<String, Integer> entry : data.playerTerrainLimits.entrySet()) {
                        ClasseGeral.playerTerrainLimits.put(UUID.fromString(entry.getKey()), entry.getValue());
                    }
                }
                // Carregar limites de terrenos de loja
                ClasseGeral.playerStoreTerrainLimits.clear();
                if (data.playerStoreTerrainLimits != null) {
                    for (Map.Entry<String, Integer> entry : data.playerStoreTerrainLimits.entrySet()) {
                        ClasseGeral.playerStoreTerrainLimits.put(UUID.fromString(entry.getKey()), entry.getValue());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveData() {
        try {
            if (!DATA_FILE.getParentFile().exists()) DATA_FILE.getParentFile().mkdirs();
            TerrainData data = new TerrainData();
            data.protectedDimensions = ClasseGeral.protectedDimensions;
            data.dimensionPvpStatus = ClasseGeral.dimensionPvpStatus; // Salvar dimensionPvpStatus
            for (Map.Entry<DimensionalChunkPos, ProtectionInfo> entry : ClasseGeral.protectedChunks.entrySet()) {
                DimensionalChunkPos dcp = entry.getKey();
                ProtectionInfo info = entry.getValue();
                String key = dcp.toString();

                ProtectionInfoData infoData = new ProtectionInfoData();
                infoData.owner = info.getOwner().toString();
                infoData.allies = info.getAllies().stream().map(UUID::toString).collect(Collectors.toSet());
                infoData.terrainName = info.getTerrainName();
                infoData.pvpAllowed = info.isPvpAllowed();
                infoData.sizeX = info.getSizeX();
                infoData.sizeZ = info.getSizeZ();
                infoData.allyLimit = info.getAllyLimit();

                data.protectedChunks.put(key, infoData);
            }
            // Salvar limites de terrenos
            for (Map.Entry<UUID, Integer> entry : ClasseGeral.playerTerrainLimits.entrySet()) {
                data.playerTerrainLimits.put(entry.getKey().toString(), entry.getValue());
            }
            // Salvar limites de terrenos de loja
            for (Map.Entry<UUID, Integer> entry : ClasseGeral.playerStoreTerrainLimits.entrySet()) {
                data.playerStoreTerrainLimits.put(entry.getKey().toString(), entry.getValue());
            }
            try (FileWriter writer = new FileWriter(DATA_FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
