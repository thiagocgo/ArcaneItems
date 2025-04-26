package net.thiago.items.habilidades.ativas;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.HashMap;
import java.util.Map;

public class DragonOrbItem extends Item {

    // Mapa para armazenar o tempo do último uso do item por jogador
    private static final Map<Player, Long> lastUseTime = new HashMap<>();
    private static final long COOLDOWN_TIME = 3600L; // 1200 ticks = 2 minutos

    public DragonOrbItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        long currentTime = level.getGameTime(); // Pega o tempo atual do jogo
        Long lastUsed = lastUseTime.get(player);

        // Verifica se o cooldown foi atingido
        if (lastUsed != null && currentTime - lastUsed < COOLDOWN_TIME) {
            long remainingTime = COOLDOWN_TIME - (currentTime - lastUsed);
            player.sendSystemMessage(Component.literal("Você precisa esperar " + remainingTime / 20 + " segundos para usar o item novamente.")
                    .withStyle(ChatFormatting.RED));
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide()) {
            // Aplica resistência ao fogo (nível 2 por 1 minuto)
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200, 1)); // 1200 = 1 minuto

            // Toca um som de fogo (som de fogo ambiente)
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);

            // Envia mensagem para o jogador
            player.sendSystemMessage(Component.literal("Você agora tem resistência ao fogo!").withStyle(ChatFormatting.GREEN));

            // Registra o tempo do último uso do item
            lastUseTime.put(player, currentTime);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
