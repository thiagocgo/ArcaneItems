package net.thiago.items.habilidades.ativas;

import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ThunderHammerParticleEffect extends MobEffect {

    // Registro do efeito
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, "arcaneitems");
    public static final DeferredHolder<MobEffect, ThunderHammerParticleEffect> EFFECT =
            EFFECTS.register("thunder_hammer_particles", () -> new ThunderHammerParticleEffect());

    public ThunderHammerParticleEffect() {
        super(MobEffectCategory.NEUTRAL, 0xFFFFFF); // Cor branca, mas não visível (sem ícone)
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            // Adiciona 2 partículas por tick, similar ao AscensionEffect
            for (int i = 0; i < 2; i++) {
                double offsetX = (entity.getRandom().nextDouble() - 0.5) * 0.8; // Espalhamento de ±0.4 blocos
                double offsetY = entity.getRandom().nextDouble() * entity.getBbHeight(); // Altura aleatória
                double offsetZ = (entity.getRandom().nextDouble() - 0.5) * 0.8;
                serverLevel.sendParticles(ParticleHelper.ELECTRICITY,
                        entity.getX() + offsetX,
                        entity.getY() + offsetY,
                        entity.getZ() + offsetZ,
                        1, // Uma partícula por chamada
                        0, 0, 0, // Sem velocidade inicial
                        0); // Sem spread adicional
            }
        }
        return true; // Continua aplicando o efeito até a duração acabar
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true; // Aplica em todos os ticks
    }

    // Método para registrar o efeito
    public static void register(IEventBus eventBus) {
        EFFECTS.register(eventBus);
    }
}