package net.thiago.items.habilidades.ativas;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;


public class RageOrbItem extends Item {

    public RageOrbItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide()) {
            // Verifica se o item está em cooldown
            if (player.getCooldowns().isOnCooldown(this)) {
                player.sendSystemMessage(Component.literal("Você ainda está em cooldown").withStyle(ChatFormatting.RED));
                return InteractionResultHolder.fail(stack);
            }

            // Aplica o efeito de Força 2 por 15 segundos (300 ticks)
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 300, 1)); // Força 2 = amplificador 1

            // Toca um som mágico (som do uso de um item)
            level.playSound(null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS,
                    1.0F, 1.0F
            );


            // Define o cooldown do item para 3 minutos (3600 ticks)
            player.getCooldowns().addCooldown(this, 3600);

            // Envia mensagem de confirmação para o jogador
            player.sendSystemMessage(Component.literal("Sua habilidade ativa foi usada!").withStyle(ChatFormatting.GREEN));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
