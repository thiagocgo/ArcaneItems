package net.thiago.items.habilidades.ativas;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class DashWandItem extends Item {

    public DashWandItem(Properties properties) {
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

            // Verifica se o item está em cooldown
            if (player.getCooldowns().isOnCooldown(this)) {
                player.sendSystemMessage(
                        Component.literal("O Dash ainda está em cooldown. Aguarde 20 segundos!")
                                .withStyle(ChatFormatting.RED)
                );
                return InteractionResultHolder.fail(stack);
            }

            Vec3 look = player.getLookAngle();
            double dashSpeed = 12.0;
            Vec3 dashVelocity = look.scale(dashSpeed);
            Vec3 currentVelocity = player.getDeltaMovement();
            if (currentVelocity.lengthSqr() < 0.01) {
                dashVelocity = look.scale(10.0);
            }
            if (player.getXRot() < -10) {
                dashVelocity = new Vec3(dashVelocity.x, dashVelocity.y + 1.0, dashVelocity.z);
            }
            Vec3 currentPos = player.position();
            Vec3 targetPos = currentPos.add(dashVelocity);
            int groundY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int)targetPos.x, (int)targetPos.z);
            double finalY = groundY + 0.5;
            player.teleportTo(targetPos.x, finalY, targetPos.z);

            if (level instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
                serverLevel.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + player.getEyeHeight(), player.getZ(), 30, 0.5, 0.5, 0.5, 0.1);
            }

            player.sendSystemMessage(Component.literal("Dash ativado!").withStyle(ChatFormatting.GREEN));
            player.getCooldowns().addCooldown(this, 400);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}