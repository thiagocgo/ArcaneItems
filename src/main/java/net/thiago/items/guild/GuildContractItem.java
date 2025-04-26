package net.thiago.items.guild;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.thiago.items.ClasseGeral;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GuildContractItem extends Item {
    public final int sizeX;
    public final int sizeZ;
    public final int allyLimit;
    public final int brickCount;

    public GuildContractItem(Properties properties, int sizeX, int sizeZ, int allyLimit, int brickCount) {
        super(properties.stacksTo(1));
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.allyLimit = allyLimit;
        this.brickCount = brickCount;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(itemstack);
        }

        // Iniciar o "cast" (3 segundos)
        serverPlayer.getCooldowns().addCooldown(this, 60); // 60 ticks = 3 segundos
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        serverLevel.sendParticles(ParticleTypes.ENCHANT, serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(), 20, 0.5, 0.5, 0.5, 0.1);

        // Animação durante o "cast"
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        for (int i = 0; i < 60; i += 5) {
            executor.schedule(() -> {
                serverLevel.sendParticles(ParticleTypes.SMOKE, serverPlayer.getX(), serverPlayer.getY() + 0.5, serverPlayer.getZ(),
                        5, 0.3, 0.3, 0.3, 0.01);
            }, i * 50, TimeUnit.MILLISECONDS);
        }

        // Após o "cast", pedir ao jogador para usar o comando
        executor.schedule(() -> {
            serverPlayer.sendSystemMessage(Component.literal("§2§lUse /setguildname <nome> em até 30 segundos para definir o nome da guilda!"));
            ClasseGeral.pendingGuilds.put(serverPlayer.getUUID(), new ClasseGeral.GuildCreationData(
                    serverLevel, serverPlayer, itemstack, sizeX, sizeZ, allyLimit, brickCount, null));
            // Remover automaticamente após 30 segundos se não for usado
            executor.schedule(() -> ClasseGeral.pendingGuilds.remove(serverPlayer.getUUID()), 30, TimeUnit.SECONDS);
            executor.shutdown();
        }, 3000, TimeUnit.MILLISECONDS);

        return InteractionResultHolder.consume(itemstack);
    }
}