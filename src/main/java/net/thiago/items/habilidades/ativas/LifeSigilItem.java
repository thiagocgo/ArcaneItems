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

public class LifeSigilItem extends Item {

    public LifeSigilItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide()) {
            // Verifica se o jogador está em um chunk protegido
            if (!ActiveSkillUtils.canUseActiveSkill(player)) {
                return InteractionResultHolder.fail(stack);
            }

            if (player.getCooldowns().isOnCooldown(this)) {
                player.sendSystemMessage(Component.literal("Você ainda está em cooldown").withStyle(ChatFormatting.RED));
                return InteractionResultHolder.fail(stack);
            }

            double radius = 10.0;
            AABB area = new AABB(player.getX() - radius, player.getY() - radius, player.getZ() - radius,
                    player.getX() + radius, player.getY() + radius, player.getZ() + radius);

            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 140, 0));
            }

            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.0, player.getZ(), 80, radius, radius, radius, 0.1);
            }

            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
            player.getCooldowns().addCooldown(this, 2400);
            player.sendSystemMessage(Component.literal("Sua habilidade ativa foi usada").withStyle(ChatFormatting.GREEN));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}