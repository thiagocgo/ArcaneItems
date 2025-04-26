package net.thiago.items.habilidades.ativas;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class SpeedRuneItem extends Item {

    public SpeedRuneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Executa somente no servidor
        if (!level.isClientSide()) {
            // Verifica se o item está em cooldown
            if (player.getCooldowns().isOnCooldown(this)) {
                player.sendSystemMessage(
                        Component.literal("Você ainda está em cooldown").withStyle(ChatFormatting.RED)
                );
                return InteractionResultHolder.fail(stack);
            }

            // Aplica o efeito Speed I por 1 minuto (1200 ticks)
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 0)); // Speed 1 = amplificador 0

            // Emite partículas do tipo CLOUD para dar um efeito "mágico"
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                        ParticleTypes.CLOUD,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        40,       // quantidade de partículas
                        1.0, 1.0, 1.0, // spread em X, Y, Z
                        0.1       // velocidade das partículas
                );
            }

            // Toca um som mágico; usamos o som de beacon para remeter a um efeito místico
            level.playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS,
                    1.0F, 1.0F
            );

            // Define o cooldown do item para 3 minutos (3600 ticks)
            player.getCooldowns().addCooldown(this, 3600);

            // Envia mensagem de confirmação para o jogador
            player.sendSystemMessage(
                    Component.literal("Sua habilidade ativa foi usada!").withStyle(ChatFormatting.GREEN)
            );
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
