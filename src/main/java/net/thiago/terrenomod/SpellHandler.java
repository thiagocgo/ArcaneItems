package net.thiago.terrenomod;

import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.thiago.items.ClasseGeral;
import net.thiago.items.ClasseGeral.ProtectionInfo;

public class SpellHandler {

    // Método para registrar no NeoForge event bus
    public static void register() {
        NeoForge.EVENT_BUS.addListener(SpellHandler::onSpellCast);
    }

    // Método para adicionar o prefixo padrão às mensagens
    private static Component addPrefix(Component message) {
        return Component.literal("§f§l[§6§lArcane §5§lMC§f§l] ").append(message);
    }

    @SubscribeEvent
    public static void onSpellCast(SpellPreCastEvent event) {
        Player player = event.getEntity();
        Level level = player.level();
        String dimension = level.dimension().location().toString();
        BlockPos playerPos = player.blockPosition();
        ChunkPos cp = new ChunkPos(playerPos);
        DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, cp.x, cp.z);
        ProtectionInfo info = ClasseGeral.protectedChunks.get(dcp);

        // Verifica se o PvP está globalmente desativado na dimensão
        if (!ClasseGeral.isDimensionPvpAllowed(dimension)) {
            event.setCanceled(true);
            player.sendSystemMessage(addPrefix(
                    Component.literal("Feitiços são desativados nesta dimensão!")
                            .withStyle(ChatFormatting.RED)));
            return;
        }

        // Verifica se a dimensão está protegida
        if (ClasseGeral.protectedDimensions.contains(dimension)) {
            // Caso o chunk não esteja protegido
            if (info == null) {
                event.setCanceled(true);
                player.sendSystemMessage(addPrefix(
                        Component.literal("Feitiços são desativados fora de terrenos nesta dimensão!")
                                .withStyle(ChatFormatting.RED)));
                return;
            }

            // Chunk protegido: verifica se o PvP está desativado no terreno
            if (!info.isPvpAllowed()) {
                event.setCanceled(true);
                String message = info.isGuildTerrain()
                        ? "Feitiços são desativados no terreno da guilda '" + info.getTerrainName() + "'!"
                        : "Feitiços são desativados neste terreno!";
                player.sendSystemMessage(addPrefix(
                        Component.literal(message)
                                .withStyle(ChatFormatting.RED)));
            }
        }
    }
}