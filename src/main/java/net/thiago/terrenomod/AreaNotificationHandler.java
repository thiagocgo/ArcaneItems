package net.thiago.terrenomod;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;
import net.thiago.items.ClasseGeral;

import java.util.HashMap;
import java.util.Map;

public class AreaNotificationHandler {
    // Agora armazenamos DimensionalChunkPos para cada jogador
    private static final Map<ServerPlayer, DimensionalChunkPos> lastPlayerChunk = new HashMap<>();
    // Armazena o último nome de terreno notificado para cada jogador
    private static final Map<ServerPlayer, String> lastTerrainName = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Executa a cada 20 ticks (aproximadamente 1 segundo)
        if (player.tickCount % 20 != 0) {
            return;
        }

        // Obtém a dimensão do jogador
        String dimension = player.level().dimension().location().toString();
        // Calcula o chunk do jogador
        ChunkPos cp = new ChunkPos(player.blockPosition());
        DimensionalChunkPos currentChunk = new DimensionalChunkPos(dimension, cp.x, cp.z);

        String currentTerrain = "";
        var info = ClasseGeral.protectedChunks.get(currentChunk);
        if (info != null) {
            currentTerrain = (info.getTerrainName() == null) ? "" : info.getTerrainName();
        }

        // Recupera o último chunk e o último nome de terreno do jogador
        DimensionalChunkPos lastChunk = lastPlayerChunk.get(player);
        String lastTerrain = lastTerrainName.getOrDefault(player, "");

        // Se o jogador mudou de chunk ou se o nome do terreno mudou, atualiza e notifica
        if (lastChunk == null || !lastChunk.equals(currentChunk)) {
            lastPlayerChunk.put(player, currentChunk);
            lastTerrainName.put(player, currentTerrain);

            if (!lastTerrain.isEmpty() && (currentTerrain.isEmpty() || !currentTerrain.equalsIgnoreCase(lastTerrain))) {
                player.sendSystemMessage(
                        Component.literal("Você saiu da área protegida! [" + lastTerrain + "]")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                );
            }
            if (!currentTerrain.isEmpty() && (lastTerrain.isEmpty() || !currentTerrain.equalsIgnoreCase(lastTerrain))) {
                player.sendSystemMessage(
                        Component.literal("Você entrou em um local protegido! [" + currentTerrain + "]")
                                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                );
            }
        }
    }
}
