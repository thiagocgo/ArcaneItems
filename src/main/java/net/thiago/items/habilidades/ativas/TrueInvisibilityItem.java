package net.thiago.items.habilidades.ativas;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.Holder;

public class TrueInvisibilityItem extends Item {

    public TrueInvisibilityItem(Properties properties) {
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

            // Obtém o Holder do efeito true_invisibility do Iron's Spellbooks
            ResourceLocation effectId = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "true_invisibility");
            Holder<MobEffect> trueInvisibilityHolder = BuiltInRegistries.MOB_EFFECT.getHolder(effectId).orElse(null);

            if (trueInvisibilityHolder == null) {
                player.sendSystemMessage(Component.literal("Erro: Efeito true_invisibility não encontrado!").withStyle(ChatFormatting.RED));
                return InteractionResultHolder.fail(stack);
            }

            // Aplica o efeito true_invisibility com a mesma configuração (140 ticks, nível 0, sem partículas visíveis)
            player.addEffect(new MobEffectInstance(trueInvisibilityHolder, 140, 0, false, false));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
            for (int i = 0; i < 20; i++) {
                double offsetX = (player.getRandom().nextDouble() - 0.5) * player.getBbWidth();
                double offsetY = player.getRandom().nextDouble() * player.getBbHeight();
                double offsetZ = (player.getRandom().nextDouble() - 0.5) * player.getBbWidth();
                level.addParticle(ParticleTypes.SMOKE, player.getX() + offsetX, player.getY() + offsetY, player.getZ() + offsetZ, 0, 0.1, 0);
            }
            player.getCooldowns().addCooldown(this, 3600);
            player.sendSystemMessage(Component.literal("Sua habilidade ativa foi usada").withStyle(ChatFormatting.GREEN));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}