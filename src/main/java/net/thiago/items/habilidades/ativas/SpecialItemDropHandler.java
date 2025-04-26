package net.thiago.items.habilidades.ativas;

import net.minecraft.ChatFormatting;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.thiago.items.guild.GuildContractItem;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SpecialItemDropHandler {

    // Set que mantém a lista de itens especiais que não podem ser interagidos de certas formas
    private static final Set<Class<? extends Item>> BLOCKED_ITEMS = new HashSet<>();
    private static final Set<UUID> playersWithSpecialItemsOpen = new HashSet<>();

    static {
        BLOCKED_ITEMS.add(ResistanceShieldItem.class);
        BLOCKED_ITEMS.add(ArcaneGuiItem.class);
        BLOCKED_ITEMS.add(ArcaneQuiverItem.class);
        BLOCKED_ITEMS.add(DashWandItem.class);
        BLOCKED_ITEMS.add(DragonOrbItem.class);
        BLOCKED_ITEMS.add(ElytrianPouchItem.class);
        BLOCKED_ITEMS.add(LifeSigilItem.class);
        BLOCKED_ITEMS.add(NecromancerOrbItem.class);
        BLOCKED_ITEMS.add(RageOrbItem.class);
        BLOCKED_ITEMS.add(SpeedRuneItem.class);
        BLOCKED_ITEMS.add(ThunderHammerItem.class);
        BLOCKED_ITEMS.add(TrueInvisibilityItem.class);
        BLOCKED_ITEMS.add(GuildContractItem.class);
    }

    // Evento que impede o jogador de dropar itens especiais
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        ItemEntity itemEntity = event.getEntity();
        if (itemEntity == null || itemEntity.level().isClientSide()) return;

        ItemStack stack = itemEntity.getItem();
        if (isBlockedItem(stack)) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer serverPlayer) {
                serverPlayer.getInventory().add(stack.copy());
                itemEntity.discard();
                serverPlayer.sendSystemMessage(Component.literal("Você não pode dropar esse item especial!").withStyle(ChatFormatting.RED));
            }
        }
    }

    // Evento que impede o uso de itens especiais em blocos, como em baús
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        ItemStack stack = event.getItemStack();
        if (!stack.isEmpty() && isBlockedItem(stack)) {
            BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getPos());
            if (blockEntity instanceof ChestBlockEntity) {
                event.setCanceled(true);
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(Component.literal("Você não pode colocar esse item especial em um baú!").withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    // Evento que impede o uso de itens especiais em entidades, como quadros ou estandes de armadura
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        ItemStack stack = player.getMainHandItem();
        if (!stack.isEmpty() && isBlockedItem(stack)) {
            Entity target = event.getTarget();
            if (target instanceof ItemFrame || target instanceof ArmorStand) {
                event.setCanceled(true);
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(Component.literal("Você não pode colocar esse item especial em uma entidade!").withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    // Evento que gerencia a abertura de containers, como baús, para evitar que itens especiais sejam manipulados
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide()) return;

        for (ItemStack stack : serverPlayer.getInventory().items) {
            if (!stack.isEmpty() && isBlockedItem(stack)) {
                playersWithSpecialItemsOpen.add(serverPlayer.getUUID());
                break;
            }
        }
    }

    // Evento que gerencia o fechamento de containers, devolvendo itens especiais ao inventário
    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide()) return;

        playersWithSpecialItemsOpen.remove(serverPlayer.getUUID());

        AbstractContainerMenu menu = event.getContainer();
        boolean foundBlocked = false;

        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (!stack.isEmpty() && isBlockedItem(stack)) {
                menu.slots.get(i).set(ItemStack.EMPTY);
                serverPlayer.getInventory().add(stack.copy());
                foundBlocked = true;
            }
        }

        // Removida a mensagem: serverPlayer.sendSystemMessage(Component.literal("Itens especiais foram devolvidos ao seu inventário!").withStyle(ChatFormatting.RED));
    }

    // Evento que gerencia o tick do servidor, verificando a manipulação de itens especiais nos containers
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!playersWithSpecialItemsOpen.contains(player.getUUID())) continue;

            AbstractContainerMenu container = player.containerMenu;
            if (container instanceof ChestMenu) {
                for (int i = 0; i < container.slots.size(); i++) {
                    ItemStack stack = container.slots.get(i).getItem();
                    if (!stack.isEmpty() && isBlockedItem(stack)) {
                        container.slots.get(i).set(ItemStack.EMPTY);
                        player.getInventory().add(stack.copy());
                    }
                }
            }
        }
    }

    // Verifica se o item está na lista de itens bloqueados
    private static boolean isBlockedItem(ItemStack stack) {
        Item item = stack.getItem();
        return BLOCKED_ITEMS.stream().anyMatch(clazz -> clazz.isInstance(item));
    }
}
