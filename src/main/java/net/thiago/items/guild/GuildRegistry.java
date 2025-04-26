package net.thiago.items.guild;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class GuildRegistry {
    private static final String MODID = "arcaneitems";
    private static final File GUILD_FILE = new File("config/" + MODID + "/guilds.json");
    private static final Gson GSON = new Gson();

    // Estrutura para serialização
    private static class GuildData {
        String name;
        UUID leader;
        Set<UUID> members;
    }

    private static final Map<String, UUID> registeredGuilds = new HashMap<>();
    private static final Map<String, GuildInfo> guildMembers = new HashMap<>();

    public static void loadGuilds() {
        if (!GUILD_FILE.getParentFile().exists()) {
            GUILD_FILE.getParentFile().mkdirs();
        }
        if (GUILD_FILE.exists()) {
            try (FileReader reader = new FileReader(GUILD_FILE)) {
                List<GuildData> guildDataList = GSON.fromJson(reader, new TypeToken<List<GuildData>>(){}.getType());
                if (guildDataList == null) guildDataList = new ArrayList<>();

                registeredGuilds.clear();
                guildMembers.clear();
                for (GuildData data : guildDataList) {
                    registeredGuilds.put(data.name, data.leader);
                    GuildInfo info = new GuildInfo(data.leader);
                    if (data.members != null) {
                        info.getMembers().addAll(data.members);
                    }
                    guildMembers.put(data.name, info);
                }
            } catch (IOException e) {
                System.err.println("Erro ao carregar guildas: " + e.getMessage());
            }
        }
    }

    public static void saveGuilds() {
        try (FileWriter writer = new FileWriter(GUILD_FILE)) {
            List<GuildData> guildDataList = new ArrayList<>();
            for (Map.Entry<String, GuildInfo> entry : guildMembers.entrySet()) {
                GuildData data = new GuildData();
                data.name = entry.getKey();
                data.leader = entry.getValue().getLeader();
                data.members = entry.getValue().getMembers();
                guildDataList.add(data);
            }
            GSON.toJson(guildDataList, writer);
        } catch (IOException e) {
            System.err.println("Erro ao salvar guildas: " + e.getMessage());
        }
    }

    public static boolean isGuildNameTaken(String guildName) {
        return registeredGuilds.keySet().stream().anyMatch(existing -> existing.equalsIgnoreCase(guildName));
    }

    public static void registerGuild(String guildName, UUID founderUUID) {
        registeredGuilds.put(guildName, founderUUID);
        guildMembers.put(guildName, new GuildInfo(founderUUID));
    }

    public static void removeGuild(String guildName) {
        registeredGuilds.remove(guildName);
        guildMembers.remove(guildName);
        saveGuilds();
    }

    public static boolean tryRegisterGuild(String guildName, ServerPlayer player) {
        if (isGuildNameTaken(guildName)) {
            player.sendSystemMessage(Component.literal("§cJá existe uma guilda com o nome '" + guildName + "'! Escolha outro nome."));
            return false;
        }
        registerGuild(guildName, player.getUUID());
        return true;
    }

    public static String getGuildNameByOwner(UUID ownerUUID) {
        return registeredGuilds.entrySet().stream()
                .filter(entry -> entry.getValue().equals(ownerUUID))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    public static boolean addMember(String guildName, UUID memberUUID) {
        GuildInfo info = guildMembers.get(guildName);
        if (info == null || info.getMembers().contains(memberUUID)) return false;
        info.addMember(memberUUID);
        return true; // saveGuilds() movido para o chamador
    }

    public static boolean removeMember(String guildName, UUID memberUUID) {
        GuildInfo info = guildMembers.get(guildName);
        if (info == null || !info.getMembers().contains(memberUUID)) return false;
        info.removeMember(memberUUID);
        return true; // saveGuilds() movido para o chamador
    }

    public static GuildInfo getGuildInfo(String guildName) {
        return guildMembers.get(guildName);
    }

    public static Map<String, GuildInfo> getAllGuilds() {
        return guildMembers;
    }
}