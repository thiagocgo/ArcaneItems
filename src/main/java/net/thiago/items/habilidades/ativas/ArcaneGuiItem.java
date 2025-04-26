package net.thiago.items.habilidades.ativas;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;


public class ArcaneGuiItem extends Item {
    public ArcaneGuiItem(Properties properties) {
        super(new Properties().stacksTo(1)); // O item não pode ser empilhado
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            MinecraftServer server = player.getServer();
            if (server != null) {
                try {
                    server.getCommands().getDispatcher().execute("zmenu open arcanemc", player.createCommandSourceStack());
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                    e.printStackTrace();
                }
            }
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

}

