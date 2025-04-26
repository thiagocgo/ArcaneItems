package net.thiago.terrenomod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.thiago.items.ClasseGeral;
import net.thiago.items.ClasseGeral.ProtectionInfo;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;

public class ChunkHandler {

    private Component addPrefix(Component message) {
        return Component.literal("§f§l[§6§lArcane §5§lMC§f§l] ").append(message);
    }

    @SubscribeEvent
    public void onBlockBreak(BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.getPlayer();
        Level level = player.level();
        String dimension = level.dimension().location().toString();

        if (ClasseGeral.protectedDimensions.isEmpty() || !ClasseGeral.protectedDimensions.contains(dimension)) {
            return;
        }

        BlockPos pos = event.getPos();
        ChunkPos cp = new ChunkPos(pos);
        DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, cp.x, cp.z);
        ProtectionInfo info = ClasseGeral.protectedChunks.get(dcp);

        if (info == null) {
            event.setCanceled(true);
            player.sendSystemMessage(addPrefix(
                    Component.literal("Você não pode quebrar blocos! Compre um terreno para isso.")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            return;
        }

        String terrainType = info.getTerrainName();
        boolean authorized = player.getUUID().equals(info.getOwner()) || info.getAllies().contains(player.getUUID());

        if (terrainType.equalsIgnoreCase("comprado")) {
            if (!authorized) {
                event.setCanceled(true);
                player.sendSystemMessage(addPrefix(
                        Component.literal("Você não pode quebrar blocos neste terreno!")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
        } else if (terrainType.equalsIgnoreCase("loja")) {
            if (!authorized) {
                event.setCanceled(true);
                player.sendSystemMessage(addPrefix(
                        Component.literal("Você não pode quebrar blocos neste terreno [LOJA]!")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
        } else if (terrainType.equalsIgnoreCase("admin")) {
            if (!authorized) { // Em vez de !player.getUUID().equals(info.getOwner())
                event.setCanceled(true);
                player.sendSystemMessage(addPrefix(
                        Component.literal("Você não pode quebrar blocos neste terreno ADMIN!")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
                } else if (info.isGuildTerrain()) { // Terreno de guilda
            if (!authorized) {
                event.setCanceled(true);
                player.sendSystemMessage(addPrefix(
                        Component.literal("Você não pode quebrar blocos no terreno da guilda '" + terrainType + "'!")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
        } else {
            if (!authorized) {
                event.setCanceled(true);
                player.sendSystemMessage(addPrefix(
                        Component.literal("Você não pode quebrar blocos neste terreno!")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
        }
    }


    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemStack();

        // Verifica se o jogador está tentando lançar um tridente
        if (stack.getItem() == Items.TRIDENT) {
            // Armazena uma cópia do ItemStack do tridente no mapa temporário
            ClasseGeral.pendingTridentStacks.put(player.getUUID(), stack.copy());
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        Level level = player.level();
        String dimension = level.dimension().location().toString();

        if (ClasseGeral.protectedDimensions.isEmpty() || !ClasseGeral.protectedDimensions.contains(dimension)) {
            return;
        }

        BlockPos pos = event.getPos();
        ChunkPos cp = new ChunkPos(pos);
        DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, cp.x, cp.z);
        ProtectionInfo info = ClasseGeral.protectedChunks.get(dcp);
        ItemStack stack = event.getItemStack();

        // Verifica métodos de iniciar fogo: Flint and Steel e Fire Charge
        if (stack.getItem() == Items.FLINT_AND_STEEL || stack.getItem() == Items.FIRE_CHARGE) {
            if (info != null) { // Chunk protegido
                boolean authorized = player.getUUID().equals(info.getOwner()) || info.getAllies().contains(player.getUUID());
                if (!authorized) {
                    event.setCanceled(true);
                    String message = info.isGuildTerrain() ?
                            "Você não pode iniciar fogo no terreno da guilda '" + info.getTerrainName() + "'!" :
                            "Você não pode iniciar fogo neste terreno!";
                    player.sendSystemMessage(addPrefix(
                            Component.literal(message)
                                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                    return;
                }
            } else { // Chunk não protegido, mas dimensão protegida
                event.setCanceled(true);
                player.sendSystemMessage(addPrefix(
                        Component.literal("Você não pode iniciar fogo fora de um terreno seu!")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                return;
            }
        }

        // Lógica existente para outras interações
        boolean isBlockItem = stack.getItem() instanceof BlockItem;
        BlockEntity tile = level.getBlockEntity(pos);
        boolean isInteractive = (tile instanceof MenuProvider);

        if (info == null) {
            if (isBlockItem) {
                event.setCanceled(true);
                player.sendSystemMessage(addPrefix(
                        Component.literal("Você não pode colocar blocos! Compre um terreno para isso.")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
            return;
        }

        String terrainType = info.getTerrainName();
        boolean authorized = player.getUUID().equals(info.getOwner()) || info.getAllies().contains(player.getUUID());

        if (terrainType.equalsIgnoreCase("comprado")) {
            if (!authorized) {
                event.setCanceled(true);
                player.sendSystemMessage(addPrefix(
                        Component.literal("Você não pode interagir neste terreno!")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
        } else if (terrainType.equalsIgnoreCase("loja")) {
            if (isBlockItem && !authorized) {
                event.setCanceled(true);
                player.sendSystemMessage(addPrefix(
                        Component.literal("Você não pode colocar blocos neste terreno [LOJA]!")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
        } else if (terrainType.equalsIgnoreCase("admin")) {
            if (isBlockItem && !authorized) {
                event.setCanceled(true);
                player.sendSystemMessage(addPrefix(
                        Component.literal("Você não pode colocar blocos neste terreno ADMIN!")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
        } else if (info.isGuildTerrain()) { // Terreno de guilda
            if (!authorized) {
                event.setCanceled(true);
                player.sendSystemMessage(addPrefix(
                        Component.literal("Você não pode interagir no terreno da guilda '" + terrainType + "'!")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
        } else {
            if (!authorized) {
                event.setCanceled(true);
                player.sendSystemMessage(addPrefix(
                        Component.literal("Você não pode interagir neste terreno!")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        ServerPlayer attacker = (ServerPlayer) event.getEntity();

        ChunkPos cp = new ChunkPos(attacker.blockPosition());
        String dimension = attacker.level().dimension().location().toString();
        DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, cp.x, cp.z);
        ProtectionInfo info = ClasseGeral.protectedChunks.get(dcp);

        // Verifica se o PvP está globalmente desativado na dimensão
        if (!ClasseGeral.isDimensionPvpAllowed(dimension)) {
            attacker.sendSystemMessage(addPrefix(
                    Component.literal("PvP está completamente desativado nesta dimensão!")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
            event.setCanceled(true);
            return;
        }

        // Verifica se a dimensão está protegida
        if (ClasseGeral.protectedDimensions.contains(dimension)) {
            // Caso o chunk não esteja protegido
            if (info == null) {
                attacker.sendSystemMessage(addPrefix(
                        Component.literal("PvP está desativado fora de terrenos nesta dimensão!")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                event.setCanceled(true);
                return;
            }
            // Chunk protegido: segue a lógica de PvP/PvE do terreno
            if (!info.isPvpAllowed() || !info.isPveAllowed()) {
                String message = info.isGuildTerrain() ?
                        "Ataques são desativados no terreno da guilda '" + info.getTerrainName() + "'!" :
                        "Ataques são desativados neste terreno!";
                attacker.sendSystemMessage(addPrefix(
                        Component.literal(message)
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();

        // 🔥 Proteção contra fireballs
        if (entity instanceof Fireball || entity instanceof SmallFireball) {
            ServerPlayer player = null;
            if (entity instanceof Fireball fireball && fireball.getOwner() instanceof ServerPlayer sp) {
                player = sp;
            } else if (entity instanceof SmallFireball smallFireball && smallFireball.getOwner() instanceof ServerPlayer sp) {
                player = sp;
            }

            if (player != null) {
                Level level = player.level();
                String dimension = level.dimension().location().toString();
                BlockPos pos = entity.blockPosition();
                ChunkPos cp = new ChunkPos(pos);
                DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, cp.x, cp.z);
                ProtectionInfo info = ClasseGeral.protectedChunks.get(dcp);

                if (!ClasseGeral.isDimensionPvpAllowed(dimension)) {
                    event.setCanceled(true);
                    player.sendSystemMessage(addPrefix(
                            Component.literal("Projéteis são desativados nesta dimensão!")
                                    .withStyle(ChatFormatting.RED)));
                    return;
                }

                if (ClasseGeral.protectedDimensions.contains(dimension) && info == null) {
                    event.setCanceled(true);
                    player.sendSystemMessage(addPrefix(
                            Component.literal("Projéteis são desativados fora de terrenos nesta dimensão!")
                                    .withStyle(ChatFormatting.RED)));
                    return;
                }

                if (info != null) {
                    boolean authorized = player.getUUID().equals(info.getOwner()) || info.getAllies().contains(player.getUUID());
                    if (!authorized && (!info.isPvpAllowed() || !info.isPveAllowed())) {
                        event.setCanceled(true);
                        String msg = info.isGuildTerrain()
                                ? "Você não pode usar fireballs no terreno da guilda '" + info.getTerrainName() + "'!"
                                : "Você não pode usar fireballs neste terreno!";
                        player.sendSystemMessage(addPrefix(Component.literal(msg).withStyle(ChatFormatting.RED)));
                    }
                }
            }
        }

        // 🎯 Proteção contra projéteis em terrenos com PvP/PvE desativados
        if (entity instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) {
            Level level = player.level();
            String dimension = level.dimension().location().toString();
            BlockPos pos = entity.blockPosition();
            ChunkPos cp = new ChunkPos(pos);
            DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, cp.x, cp.z);
            ProtectionInfo info = ClasseGeral.protectedChunks.get(dcp);

            // Verifica se o PvP está globalmente desativado na dimensão
            if (!ClasseGeral.isDimensionPvpAllowed(dimension)) {
                event.setCanceled(true);
                player.sendSystemMessage(addPrefix(
                        Component.literal("Projéteis são desativados nesta dimensão!")
                                .withStyle(ChatFormatting.RED)));
                // Restaurar tridente, se aplicável
                if (projectile instanceof ThrownTrident) {
                    ItemStack tridentStack = ClasseGeral.pendingTridentStacks.remove(player.getUUID());
                    if (tridentStack != null && !player.getInventory().add(tridentStack)) {
                        player.getInventory().add(0, tridentStack);
                    }
                }
                return;
            }

            // Verifica se a dimensão está protegida e o chunk não está protegido
            if (ClasseGeral.protectedDimensions.contains(dimension) && info == null) {
                event.setCanceled(true);
                player.sendSystemMessage(addPrefix(
                        Component.literal("Projéteis são desativados fora de terrenos nesta dimensão!")
                                .withStyle(ChatFormatting.RED)));
                // Restaurar tridente, se aplicável
                if (projectile instanceof ThrownTrident) {
                    ItemStack tridentStack = ClasseGeral.pendingTridentStacks.remove(player.getUUID());
                    if (tridentStack != null && !player.getInventory().add(tridentStack)) {
                        player.getInventory().add(0, tridentStack);
                    }
                }
                return;
            }

            // Chunk protegido com PvP/PvE desativado
            if (info != null && (!info.isPvpAllowed() || !info.isPveAllowed())) {
                event.setCanceled(true);

                ItemStack projectileStack = ItemStack.EMPTY;

                if (projectile instanceof Arrow) {
                    projectileStack = new ItemStack(Items.ARROW);
                } else if (projectile instanceof ThrownTrident) {
                    // Restaurar o tridente original com NBT
                    projectileStack = ClasseGeral.pendingTridentStacks.remove(player.getUUID());
                } else if (projectile instanceof SpectralArrow) {
                    projectileStack = new ItemStack(Items.SPECTRAL_ARROW);
                } else if (projectile instanceof Fireball) {
                    projectileStack = new ItemStack(Items.FIRE_CHARGE);
                } else if (projectile instanceof ThrownPotion) {
                    projectileStack = new ItemStack(Items.POTION);
                } else if (projectile instanceof Snowball) {
                    projectileStack = new ItemStack(Items.SNOWBALL);
                }

                // Adicionar o item ao inventário
                if (!projectileStack.isEmpty() && !player.getInventory().add(projectileStack)) {
                    player.getInventory().add(0, projectileStack);
                }

                String msg = info.isGuildTerrain()
                        ? "Você não pode lançar projéteis no terreno da guilda '" + info.getTerrainName() + "'!"
                        : "Você não pode lançar projéteis neste terreno!";
                player.sendSystemMessage(addPrefix(Component.literal(msg).withStyle(ChatFormatting.RED)));
            }
        }

        // 💣 Proteção global contra TNT
        if (entity instanceof PrimedTnt tnt && tnt.getOwner() instanceof ServerPlayer player) {
            String dimension = player.level().dimension().toString();
            if (ClasseGeral.protectedDimensions.contains(dimension)) {
                event.setCanceled(true);
                player.sendSystemMessage(addPrefix(Component.literal("Explosões são desativadas nesta dimensão!").withStyle(ChatFormatting.RED)));
            }
        }

        // 💀 Proteção global contra WitherSkull
        if (entity instanceof WitherSkull skull && skull.getOwner() instanceof ServerPlayer player) {
            String dimension = player.level().dimension().toString();
            if (ClasseGeral.protectedDimensions.contains(dimension)) {
                event.setCanceled(true);
                player.sendSystemMessage(addPrefix(Component.literal("Comando do Wither é desativado nesta dimensão!").withStyle(ChatFormatting.RED)));
            }
        }
    }
}