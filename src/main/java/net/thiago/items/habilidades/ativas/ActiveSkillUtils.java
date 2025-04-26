package net.thiago.items.habilidades.ativas;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.thiago.items.ClasseGeral;
import net.thiago.terrenomod.DimensionalChunkPos;

public class ActiveSkillUtils {

    /**
     * Verifica se o jogador pode usar uma habilidade ativa no chunk atual.
     * Se o chunk for protegido (ProtectionInfo presente), a habilidade é bloqueada.
     *
     * @param player O jogador tentando usar a habilidade
     * @return true se a habilidade pode ser usada, false se for bloqueada
     */
    public static boolean canUseActiveSkill(Player player) {
        // Obtém a posição do jogador e o chunk atual
        BlockPos playerPos = player.blockPosition();
        ChunkPos chunkPos = new ChunkPos(playerPos);
        String dimension = player.level().dimension().location().toString();
        DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, chunkPos.x, chunkPos.z);

        // Verifica se o chunk é protegido
        ClasseGeral.ProtectionInfo info = ClasseGeral.protectedChunks.get(dcp);
        if (info != null) {
            player.sendSystemMessage(Component.literal("§cVocê não pode usar essa habilidade ativa em terrenos protegidos"));
            return false;
        }

        return true;
    }
}