package net.thiago.items.guild;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GuildCooldownManager {
    private static final Map<UUID, Long> guildRemoveCooldowns = new HashMap<>(); // UUID do jogador -> Timestamp do último uso
    private static final long COOLDOWN_DURATION = 7 * 24 * 60 * 60 * 1000; // 7 dias em milissegundos
    private static final String MODID = "arcaneitems";
    private static final File COOLDOWN_FILE = new File("config/" + MODID + "/guild_cooldowns.json");
    private static final Gson GSON = new Gson();

    // Carregar cooldowns do arquivo
    public static void loadCooldowns() {
        if (!COOLDOWN_FILE.getParentFile().exists()) {
            COOLDOWN_FILE.getParentFile().mkdirs();
        }
        if (COOLDOWN_FILE.exists()) {
            try (FileReader reader = new FileReader(COOLDOWN_FILE)) {
                Type type = new TypeToken<Map<String, Long>>(){}.getType();
                Map<String, Long> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    guildRemoveCooldowns.clear();
                    for (Map.Entry<String, Long> entry : loaded.entrySet()) {
                        guildRemoveCooldowns.put(UUID.fromString(entry.getKey()), entry.getValue());
                    }
                }
            } catch (IOException e) {
                System.err.println("Erro ao carregar cooldowns de guildas: " + e.getMessage());
            }
        }
    }

    // Salvar cooldowns no arquivo
    public static void saveCooldowns() {
        try (FileWriter writer = new FileWriter(COOLDOWN_FILE)) {
            Map<String, Long> toSave = new HashMap<>();
            for (Map.Entry<UUID, Long> entry : guildRemoveCooldowns.entrySet()) {
                toSave.put(entry.getKey().toString(), entry.getValue());
            }
            GSON.toJson(toSave, writer);
        } catch (IOException e) {
            System.err.println("Erro ao salvar cooldowns de guildas: " + e.getMessage());
        }
    }

    // Verificar se o jogador está em cooldown e retornar o tempo restante em milissegundos
    public static long getRemainingCooldown(UUID playerUUID) {
        long currentTime = System.currentTimeMillis();
        Long lastUse = guildRemoveCooldowns.get(playerUUID);
        if (lastUse == null) {
            return 0; // Sem cooldown
        }
        long elapsed = currentTime - lastUse;
        return elapsed >= COOLDOWN_DURATION ? 0 : COOLDOWN_DURATION - elapsed;
    }

    // Atualizar o cooldown de um jogador
    public static void updateCooldown(UUID playerUUID) {
        guildRemoveCooldowns.put(playerUUID, System.currentTimeMillis());
        saveCooldowns();
    }

    // Obter a duração total do cooldown (para mensagens)
    public static long getCooldownDuration() {
        return COOLDOWN_DURATION;
    }
}