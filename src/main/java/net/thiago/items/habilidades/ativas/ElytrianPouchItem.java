package net.thiago.items.habilidades.ativas;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

/**
 * ElytrianPouchItem:
 * Quando usado (botão direito), reabastece o jogador com 32 foguetes e inicia um cooldown de 3 minutos.
 */
public class ElytrianPouchItem extends Item {

    public ElytrianPouchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Executa a lógica apenas no servidor
        if (!level.isClientSide()) {
            // Se o item ainda estiver em cooldown, envia uma mensagem e aborta
            if (player.getCooldowns().isOnCooldown(this)) {
                player.sendSystemMessage(
                        Component.literal("Você ainda está em cooldown").withStyle(ChatFormatting.RED)
                );
                return InteractionResultHolder.fail(stack);
            }

            // Cria um ItemStack de 32 foguetes
            ItemStack rockets = new ItemStack(Items.FIREWORK_ROCKET, 32);
            // Tenta adicionar ao inventário do jogador; se o inventário estiver cheio, o item será descartado
            if (!player.getInventory().add(rockets)) {
                player.drop(rockets, false);
            }

            // Adiciona cooldown de 3 minutos (180s * 20 ticks = 3600 ticks)
            player.getCooldowns().addCooldown(this, 3600);

            // Envia mensagem de confirmação ao jogador
            player.sendSystemMessage(
                    Component.literal("Você recebeu 32 foguetes para voar!").withStyle(ChatFormatting.GREEN)
            );
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public Component getName(ItemStack stack) {
        // Nome fixo para o item
        return Component.literal("Elytrian Pouch").withStyle(ChatFormatting.WHITE);
    }
}
