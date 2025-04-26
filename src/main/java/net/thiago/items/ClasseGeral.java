package net.thiago.items;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.thiago.homes.HomeCommandHandler;
import net.thiago.items.guild.GuildContractItem;
import net.thiago.items.guild.GuildCooldownManager;
import net.thiago.items.guild.GuildInfo;
import net.thiago.items.guild.GuildRegistry;
import net.thiago.items.habilidades.ativas.SpecialItemDropHandler;
import net.thiago.items.habilidades.ativas.ThunderHammerParticleEffect;
import net.thiago.terrenomod.*;
import net.thiago.items.habilidades.ativas.NovosItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.*;



@Mod(ClasseGeral.MOD_ID)
public class ClasseGeral {
    public static final String MOD_ID = "arcaneitems";

    public static final Map<DimensionalChunkPos, ProtectionInfo> protectedChunks = new HashMap<>();

    public static final Map<UUID, Integer> playerTerrainLimits = new HashMap<>();
    public static final Map<UUID, GuildCreationData> pendingGuilds = new HashMap<>();
    private static final Map<UUID, Long> guildRemoveCooldowns = new HashMap<>();
    public static final Map<UUID, ItemStack> pendingTridentStacks = new WeakHashMap<>();
    public static final Map<String, Boolean> dimensionPvpStatus = new HashMap<>();
    private static final long COOLDOWN_DURATION = 7 * 24 * 60 * 60 * 1000;



    public static void setDefaultTerrainLimit() {

        for (UUID playerUUID : playerTerrainLimits.keySet()) {
            if (!playerTerrainLimits.containsKey(playerUUID)) {
                playerTerrainLimits.put(playerUUID, 2);
            }
        }
    }

    // Novo mapa para limites de terrenos de loja
    public static final Map<UUID, Integer> playerStoreTerrainLimits = new HashMap<>();
    public static final Map<UUID, Long> pvpCommandCooldowns = new HashMap<>();
    private static final long TIME_LIMIT = 30_000; // 30 segundos em milissegundos

    // Configurar limite de terrenos de loja com valor padrão 0
    public static void setDefaultStoreTerrainLimit() {
        for (UUID playerUUID : playerStoreTerrainLimits.keySet()) {
            if (!playerStoreTerrainLimits.containsKey(playerUUID)) {
                playerStoreTerrainLimits.put(playerUUID, 0); // Limite de loja 0 por padrão
            }
        }
    }

    private void clientSetup(final FMLClientSetupEvent event) {
    }

    public static final Map<UUID, GuildInviteData> pendingGuildInvites = new HashMap<>();

    public static class GuildInviteData {
        public final String guildName;
        public final UUID inviterUUID;
        public final long inviteTime;

        public GuildInviteData(String guildName, UUID inviterUUID) {
            this.guildName = guildName;
            this.inviterUUID = inviterUUID;
            this.inviteTime = System.currentTimeMillis();
        }
    }



    private String getCooldownTimeRemaining(long lastUseTimestamp) {
        long currentTime = System.currentTimeMillis();
        long cooldownDuration = 10 * 60 * 1000L; // 10 minutos em milissegundos
        long timeElapsed = currentTime - lastUseTimestamp;
        long timeRemaining = cooldownDuration - timeElapsed;
        if (timeRemaining <= 0) return "0 segundos";
        long minutes = timeRemaining / (60 * 1000);
        long seconds = (timeRemaining % (60 * 1000)) / 1000;
        return minutes + " minutos e " + seconds + " segundos";
    }

    public static Map<String, TerrenoAdmin> adminTerrains = new HashMap<>();


    public ClasseGeral(IEventBus modEventBus, ModContainer modContainer) {

        NeoForge.EVENT_BUS.register(new ChunkHandler());
        NeoForge.EVENT_BUS.register(new AreaNotificationHandler());
        NeoForge.EVENT_BUS.register(SpecialItemDropHandler.class);
        ThunderHammerParticleEffect.register(modEventBus);


        NeoForge.EVENT_BUS.register(this);
        SpellHandler.register();

        net.thiago.items.habilidades.ativas.NovosItems.register(modEventBus);


        modEventBus.addListener(this::addCreative);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        // 🔄 Registra o renderizador customizado do morcego no cliente
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {

        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Registra os comandos durante a configuração comum
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        GuildRegistry.loadGuilds();
        GuildCooldownManager.loadCooldowns();
    }

    // Adiciona os itens do mod à Creative Tab de combate
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            try {
                var dash = NovosItems.DASHITEM.get();
                if (dash != null) {
                    event.accept(dash);
                }
            } catch (Exception e) {
                // Dash item não está bound, ou algo similar – opcionalmente logue a exceção.
            }

            try {
                var shield = NovosItems.SHIELDITEM.get();
                if (shield != null) {
                    event.accept(shield);
                }
            } catch (Exception e) {
            }

            try {
                var invis = NovosItems.TRUEINVISIBILITY.get();
                if (invis != null) {
                    event.accept(invis);
                }
            } catch (Exception e) {
            }

            try {
                var lifeSigil = NovosItems.LIFESIGIL.get();
                if (lifeSigil != null) {
                    event.accept(lifeSigil);
                }
            } catch (Exception e) {
            }

            try {
                var speedRune = NovosItems.SPEEDRUNE.get();
                if (speedRune != null) {
                    event.accept(speedRune);
                }
            } catch (Exception e) {
            }

            try {
                var arcaneQuiver = NovosItems.ARCANE_QUIVER.get();
                if (arcaneQuiver != null) {
                    event.accept(arcaneQuiver);
                }
            } catch (Exception e) {
            }

            try {
                var rageOrb = NovosItems.RAGE_ORB.get();
                if (rageOrb != null) {
                    event.accept(rageOrb);
                }
            } catch (Exception e) {
            }

            try {
                var arcaneGui = NovosItems.ARCANE_GUI.get();
                if (arcaneGui != null) {
                    event.accept(arcaneGui);
                }
            } catch (Exception e) {
            }

            try {
                var dragonOrb = NovosItems.DRAGON_ORB.get();
                if (dragonOrb != null) {
                    event.accept(dragonOrb);
                }
            } catch (Exception e) {
            }

            try {
                var elytrianPouch = NovosItems.ELYTRIAN_POUCH.get();
                if (elytrianPouch != null) {
                    event.accept(elytrianPouch);
                }
            } catch (Exception e) {
            }

            try {
                var batEssence = NovosItems.BAT_ESSENCE.get();
                if (batEssence != null) {
                    event.accept(batEssence);
                }
            } catch (Exception e) {
            }

            try {
                var necromancerOrb = NovosItems.NECROMANCER_ORB.get();
                if (necromancerOrb != null) {
                    event.accept(necromancerOrb);
                }
            } catch (Exception e) {
            }

            // Adicionando os novos itens de guilda
            try {
                var guildIron = NovosItems.GUILD_CONTRACT_IRON.get();
                if (guildIron != null) {
                    event.accept(guildIron);
                }
            } catch (Exception e) {
            }

            try {
                var guildGold = NovosItems.GUILD_CONTRACT_GOLD.get();
                if (guildGold != null) {
                    event.accept(guildGold);
                }
            } catch (Exception e) {
            }

            try {
                var guildDiamond = NovosItems.GUILD_CONTRACT_DIAMOND.get();
                if (guildDiamond != null) {
                    event.accept(guildDiamond);
                }
            } catch (Exception e) {
            }
            try {
                var guildDiamond = NovosItems.VIP_INGOT.get();
                if (guildDiamond != null) {
                    event.accept(guildDiamond);
                }
            } catch (Exception e) {
            }
            try {
                var guildDiamond = NovosItems.VIP_HELMET.get();
                if (guildDiamond != null) {
                    event.accept(guildDiamond);
                }
            } catch (Exception e) {
            }
            try {
                var guildDiamond = NovosItems.VIP_CHESTPLATE.get();
                if (guildDiamond != null) {
                    event.accept(guildDiamond);
                }
            } catch (Exception e) {
            }
            try {
                var guildDiamond = NovosItems.VIP_LEGGINGS.get();
                if (guildDiamond != null) {
                    event.accept(guildDiamond);
                }
            } catch (Exception e) {
            }
            try {
                var guildDiamond = NovosItems.VIP_BOOTS.get();
                if (guildDiamond != null) {
                    event.accept(guildDiamond);
                }
            } catch (Exception e) {
            }
        }
    }

    private void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Registro do comando /terreno
        dispatcher.register(
                Commands.literal("terreno")
                        // Comando básico: /terreno comprar
                        .then(Commands.literal("comprar")
                                .executes(this::executeComprar))

                        // Comando: /terreno vender
                        .then(Commands.literal("vender")
                                .executes(this::executeVender))

                        // Comando: /terreno listar terrenos
                        .then(Commands.literal("listar")
                                .then(Commands.literal("terrenos")
                                        .executes(this::executeListarTerrenos)))

                        // Comando: /terreno pvp on e /terreno pvp off
                        .then(Commands.literal("pvp")
                                .then(Commands.literal("on")
                                        .executes(this::executePvpOn))
                                .then(Commands.literal("off")
                                        .executes(this::executePvpOff)))

                        // Comando: /terreno loja comprar (sem limit aqui)
                        .then(Commands.literal("loja")
                                .then(Commands.literal("comprar")
                                        .executes(this::executeLojaComprar)))

                        .then(Commands.literal("admin")
                                .requires(source -> source.hasPermission(2)) // Apenas ops
                                .then(Commands.literal("limit")
                                        .then(Commands.literal("add")
                                                .then(Commands.argument("player", StringArgumentType.word())
                                                        .then(Commands.argument("valor", IntegerArgumentType.integer())
                                                                .executes(this::executeAdminLimitAdd))))
                                        .then(Commands.literal("remove")
                                                .then(Commands.argument("player", StringArgumentType.word())
                                                        .then(Commands.argument("valor", IntegerArgumentType.integer())
                                                                .executes(this::executeAdminLimitRemove)))))
                                .then(Commands.literal("loja")
                                        .then(Commands.literal("limit")
                                                .then(Commands.literal("add")
                                                        .then(Commands.argument("player", StringArgumentType.word())
                                                                .then(Commands.argument("valor", IntegerArgumentType.integer())
                                                                        .executes(this::executeAdminStoreLimitAdd))))
                                                .then(Commands.literal("remove")
                                                        .then(Commands.argument("player", StringArgumentType.word())
                                                                .then(Commands.argument("valor", IntegerArgumentType.integer())
                                                                        .executes(this::executeAdminStoreLimitRemove))))))
                                .then(Commands.literal("proteger")
                                        .executes(this::executeAdminProteger))
                                .then(Commands.literal("desproteger")
                                        .executes(this::executeAdminDesproteger))
                                .then(Commands.literal("comprar")
                                        .then(Commands.argument("valor", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("nome", StringArgumentType.greedyString())
                                                        .executes(this::executeAdminComprarArea))))
                                .then(Commands.literal("vender")
                                        .then(Commands.argument("nome", StringArgumentType.greedyString())
                                                .executes(this::executeAdminVenderArea)))
                                .then(Commands.literal("pvp")
                                        .then(Commands.literal("on")
                                                .then(Commands.argument("nome", StringArgumentType.greedyString())
                                                        .executes(this::executeAdminPvpOn)))
                                        .then(Commands.literal("off")
                                                .then(Commands.argument("nome", StringArgumentType.greedyString())
                                                        .executes(this::executeAdminPvpOff))))
                                // Adicionando o comando dimensionpvp aqui
                                .then(Commands.literal("dimensionpvp")
                                        .then(Commands.literal("on")
                                                .executes(this::executeAdminDimensionPvpOn))
                                        .then(Commands.literal("off")
                                                .executes(this::executeAdminDimensionPvpOff))
                                        .then(Commands.literal("status")
                                                .executes(this::executeAdminDimensionPvpStatus)))));

        // Comandos separados para /sethome, /home, /delhome e /listhomes
        dispatcher.register(
                Commands.literal("sethome")
                        .executes(HomeCommandHandler::executeSetHomeDefault) // Sem argumento
                        .then(Commands.argument("nome", StringArgumentType.string())
                                .executes(HomeCommandHandler::executeSetHomeWithName)) // Com argumento
        );

        dispatcher.register(
                Commands.literal("home")
                        .executes(HomeCommandHandler::executeHomeDefault) // Sem argumento
                        .then(Commands.argument("nome", StringArgumentType.string())
                                .executes(HomeCommandHandler::executeHomeWithName)) // Com argumento
        );

        dispatcher.register(
                Commands.literal("delhome")
                        .executes(HomeCommandHandler::executeDelHomeDefault) // Sem argumento
                        .then(Commands.argument("nome", StringArgumentType.string())
                                .executes(HomeCommandHandler::executeDelHomeWithName)) // Com argumento
        );

        dispatcher.register(
                Commands.literal("listhomes")
                        .executes(HomeCommandHandler::executeListHomes)
        );

        dispatcher.register(
                Commands.literal("setguildname")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();  // Agora temos acesso ao source
                                    ServerPlayer player = source.getPlayerOrException();
                                    String guildName = StringArgumentType.getString(context, "name");
                                    ClasseGeral.GuildCreationData data = ClasseGeral.pendingGuilds.get(player.getUUID());

                                    if (data == null) {
                                        source.sendFailure(Component.literal("§cVocê precisa usar o contrato de guilda primeiro!"));
                                        return 0;
                                    }

                                    long currentTime = System.currentTimeMillis();
                                    if (currentTime - data.creationTime > TIME_LIMIT) {
                                        source.sendFailure(Component.literal("§cO tempo para definir o nome da guilda expirou! Use o contrato novamente."));
                                        ClasseGeral.pendingGuilds.remove(player.getUUID());
                                        return 0;
                                    }

                                    if (guildName.length() > 20) {
                                        source.sendFailure(Component.literal("§cO nome da guilda deve ter no máximo 20 caracteres!"));
                                        return 0;
                                    }

                                    if (!guildName.matches("[a-zA-Z0-9]+")) {
                                        source.sendFailure(Component.literal("§cO nome da guilda só pode conter letras e números!"));
                                        return 0;
                                    }

                                    if (!GuildRegistry.tryRegisterGuild(guildName, player)) {
                                        return 0; // Mensagem já enviada pelo GuildRegistry
                                    }

                                    data.guildName = guildName;

                                    // Passar source para finalizeGuildCreation
                                    finalizeGuildCreation(data.level, data.player, data.itemstack, guildName, source);

                                    ClasseGeral.pendingGuilds.remove(player.getUUID());
                                    return 1;
                                }))
        );

        dispatcher.register(
                Commands.literal("guild")
                        .then(Commands.literal("remove")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player;
                                    try {
                                        player = source.getPlayerOrException();
                                    } catch (Exception e) {
                                        source.sendFailure(addPrefix(Component.literal("Este comando só pode ser executado por jogadores.")));
                                        return 0;
                                    }

                                    UUID playerUUID = player.getUUID();

                                    // Verificar cooldown usando GuildCooldownManager
                                    long remainingCooldown = GuildCooldownManager.getRemainingCooldown(playerUUID);
                                    if (remainingCooldown > 0) {
                                        long daysLeft = remainingCooldown / (24 * 60 * 60 * 1000);
                                        long hoursLeft = (remainingCooldown % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
                                        source.sendFailure(addPrefix(Component.literal("§cVocê deve esperar " + daysLeft + " dias e " + hoursLeft + " horas antes de usar este comando novamente!")));
                                        return 0;
                                    }

                                    BlockPos playerPos = player.blockPosition();
                                    ChunkPos cp = new ChunkPos(playerPos);
                                    String dimension = player.level().dimension().location().toString();
                                    DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, cp.x, cp.z);
                                    ProtectionInfo info = ClasseGeral.protectedChunks.get(dcp);

                                    if (info == null) {
                                        source.sendFailure(addPrefix(Component.literal("Este chunk não está protegido.")));
                                        return 0;
                                    }
                                    if (!playerUUID.equals(info.getOwner())) {
                                        source.sendFailure(addPrefix(Component.literal("Você não é o dono desta guilda.")));
                                        return 0;
                                    }
                                    if (!info.isGuildTerrain()) {
                                        source.sendFailure(addPrefix(Component.literal("Este terreno não é uma guilda.")));
                                        return 0;
                                    }

                                    // Determinar o item original com base em sizeX, sizeZ e allyLimit
                                    ItemStack refundItem = null;
                                    if (info.getSizeX() == 1 && info.getSizeZ() == 1 && info.getAllyLimit() == 5) {
                                        refundItem = new ItemStack(NovosItems.GUILD_CONTRACT_IRON.get());
                                    } else if (info.getSizeX() == 3 && info.getSizeZ() == 3 && info.getAllyLimit() == 10) {
                                        refundItem = new ItemStack(NovosItems.GUILD_CONTRACT_GOLD.get());
                                    } else if (info.getSizeX() == 5 && info.getSizeZ() == 5 && info.getAllyLimit() == 20) {
                                        refundItem = new ItemStack(NovosItems.GUILD_CONTRACT_DIAMOND.get());
                                    } else {
                                        source.sendFailure(addPrefix(Component.literal("Não foi possível determinar o tipo de contrato da guilda.")));
                                        return 0;
                                    }

                                    // Remover todos os chunks associados à guilda
                                    String guildName = info.getTerrainName();
                                    List<DimensionalChunkPos> chunksToRemove = new ArrayList<>();
                                    for (Map.Entry<DimensionalChunkPos, ProtectionInfo> entry : ClasseGeral.protectedChunks.entrySet()) {
                                        if (entry.getValue().getTerrainName().equals(guildName) && entry.getValue().getOwner().equals(playerUUID)) {
                                            chunksToRemove.add(entry.getKey());
                                        }
                                    }

                                    // Remover status de todos os membros antes de apagar a guilda
                                    GuildInfo guildInfo = GuildRegistry.getGuildInfo(guildName);
                                    if (guildInfo != null) {
                                        for (UUID memberUUID : guildInfo.getMembers()) {
                                            ServerPlayer member = player.getServer().getPlayerList().getPlayer(memberUUID);
                                            String memberName = member != null ? member.getName().getString() : null;
                                            if (memberName == null) {
                                                // Tentar obter o nome offline (opcional, depende do servidor)
                                                memberName = player.getServer().getProfileCache().get(memberUUID)
                                                        .map(GameProfile::getName)
                                                        .orElse(null);
                                            }
                                            if (memberName != null) {
                                                try {
                                                    String command = "status remover " + memberName;
                                                    source.sendSystemMessage(addPrefix(Component.literal("Removendo status de " + memberName + "...")));
                                                    CommandSourceStack consoleSource = player.getServer().createCommandSourceStack()
                                                            .withPermission(4)
                                                            .withSuppressedOutput();
                                                    player.getServer().getCommands().performPrefixedCommand(consoleSource, command);
                                                } catch (Exception e) {
                                                    source.sendFailure(addPrefix(Component.literal("Erro ao remover status de " + memberName + ": " + e.getMessage())
                                                            .withStyle(ChatFormatting.RED)));
                                                }
                                            }
                                        }
                                    }

                                    // Remover status do dono da guilda
                                    try {
                                        String playerName = player.getName().getString();
                                        String command = "status remover " + playerName;
                                        source.sendSystemMessage(addPrefix(Component.literal("Removendo status de " + playerName + "...")));
                                        CommandSourceStack consoleSource = player.getServer().createCommandSourceStack()
                                                .withPermission(4)
                                                .withSuppressedOutput();
                                        player.getServer().getCommands().performPrefixedCommand(consoleSource, command);
                                    } catch (Exception e) {
                                        source.sendFailure(addPrefix(Component.literal("Erro ao remover seu status: " + e.getMessage())
                                                .withStyle(ChatFormatting.RED)));
                                    }

                                    // Remover chunks
                                    for (DimensionalChunkPos chunk : chunksToRemove) {
                                        ClasseGeral.protectedChunks.remove(chunk);
                                    }

                                    // Remover a guilda do GuildRegistry
                                    GuildRegistry.removeGuild(guildName);

                                    // Devolver o item ao inventário ou dropar se não houver espaço
                                    boolean added = player.getInventory().add(refundItem);
                                    if (!added) {
                                        player.drop(refundItem, false);
                                        source.sendFailure(addPrefix(Component.literal("§eO contrato foi dropado no chão por falta de espaço no inventário!")));
                                    } else {
                                        source.sendSuccess(() -> addPrefix(Component.literal("§aContrato devolvido ao inventário com sucesso!")), true);
                                    }

                                    // Atualizar o cooldown usando GuildCooldownManager
                                    GuildCooldownManager.updateCooldown(playerUUID);

                                    // Mensagem de sucesso
                                    source.sendSuccess(() ->
                                            addPrefix(Component.literal("Guilda '" + guildName + "' removida com sucesso! O contrato foi devolvido.")), true);

                                    // Salvar os dados de terrenos
                                    TerrainDataHandler.saveData();
                                    return 1;
                                }))

                        .then(Commands.literal("aliado")
                                .then(Commands.literal("adicionar")
                                    .then(Commands.argument("nome", StringArgumentType.word())
                                        .executes(this::executeAliado)))


                                .then(Commands.literal("retirar")
                                        .then(Commands.argument("nome", StringArgumentType.word())
                                                .executes(this::executeRetirarAliado))))

                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    CommandSourceStack source = ctx.getSource();

                                    // Não restringir a jogadores, permitindo execução pelo console
                                    Map<String, GuildInfo> allGuilds = GuildRegistry.getAllGuilds();

                                    if (allGuilds.isEmpty()) {
                                        source.sendSuccess(() -> addPrefix(Component.literal("Nenhuma guilda registrada.").withStyle(ChatFormatting.RED)), false);
                                        return 1;
                                    }

                                    // Cabeçalho
                                    source.sendSuccess(() -> addPrefix(Component.literal("══ ⟡ GUILDAS REGISTRADAS ⟡ ══").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)), false);

                                    for (Map.Entry<String, GuildInfo> entry : allGuilds.entrySet()) {
                                        String guildName = entry.getKey();
                                        GuildInfo info = entry.getValue();

                                        // Resolver nome do líder
                                        String leaderName = getPlayerNameFromUUID(source.getServer(), info.getLeader());
                                        if (leaderName == null) {
                                            leaderName = info.getLeader().toString() + " [Offline]";
                                        }

                                        // Construir lista de membros
                                        StringBuilder memberList = new StringBuilder();
                                        for (UUID memberUUID : info.getMembers()) {
                                            String name = getPlayerNameFromUUID(source.getServer(), memberUUID);
                                            if (name == null) {
                                                name = memberUUID.toString() + " [Offline]";
                                            }
                                            memberList.append(name).append(", ");
                                        }

                                        String membersText = memberList.length() > 0 ? memberList.substring(0, memberList.length() - 2) : "Nenhum membro";

                                        // Enviar informações da guilda
                                        final String finalGuildName = guildName;
                                        final String finalLeaderName = leaderName;
                                        final String finalMembersText = membersText;
                                        source.sendSuccess(() -> addPrefix(Component.literal(finalGuildName + " ────────────").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)), false);
                                        source.sendSuccess(() -> addPrefix(Component.literal("Fundador: " + finalLeaderName).withStyle(ChatFormatting.RED, ChatFormatting.GOLD)), false);
                                        source.sendSuccess(() -> addPrefix(Component.literal("Membros: " + finalMembersText).withStyle(ChatFormatting.YELLOW, ChatFormatting.WHITE)), false);
                                    }

                                    // Linha final indicando fim da lista
                                    source.sendSuccess(() -> addPrefix(Component.literal("══ ⟡ FIM DA LISTA ⟡ ══").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)), false);

                                    // Enviar mensagem de confirmação ao jogador, se aplicável
                                    if (source.getEntity() instanceof ServerPlayer player) {
                                        player.sendSystemMessage(addPrefix(Component.literal("Lista de guildas exibida.").withStyle(ChatFormatting.GREEN)));
                                    }

                                    return 1;
                                }))

                        .then(Commands.literal("accept")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player;
                                    try {
                                        player = source.getPlayerOrException();
                                    } catch (Exception e) {
                                        source.sendFailure(addPrefix(Component.literal("Este comando só pode ser executado por jogadores.")));
                                        return 0;
                                    }

                                    UUID playerUUID = player.getUUID();
                                    GuildInviteData invite = pendingGuildInvites.get(playerUUID);
                                    if (invite == null) {
                                        source.sendFailure(addPrefix(Component.literal("Você não tem nenhum convite pendente.")));
                                        return 0;
                                    }

                                    // Verificar se o convite expirou (5 minutos)
                                    long currentTime = System.currentTimeMillis();
                                    if (currentTime - invite.inviteTime > 5 * 60 * 1000) { // 5 minutos
                                        pendingGuildInvites.remove(playerUUID);
                                        source.sendFailure(addPrefix(Component.literal("O convite para a guilda '" + invite.guildName + "' expirou.")));
                                        return 0;
                                    }

                                    // Adicionar jogador à guilda
                                    if (!GuildRegistry.addMember(invite.guildName, playerUUID)) {
                                        source.sendFailure(addPrefix(Component.literal("Erro ao aceitar o convite: você já é membro da guilda.")));
                                        pendingGuildInvites.remove(playerUUID);
                                        return 0;
                                    }

                                    // Adicionar aliado a todos os chunks da guilda
                                    for (Map.Entry<DimensionalChunkPos, ProtectionInfo> entry : ClasseGeral.protectedChunks.entrySet()) {
                                        ProtectionInfo info = entry.getValue();
                                        if (info.getTerrainName().equals(invite.guildName) && info.isGuildTerrain()) {
                                            info.getAllies().add(playerUUID);
                                        }
                                    }

                                    // Executar comando /status como console
                                    boolean statusApplied = false;
                                    String playerName = player.getName().getString();
                                    try {
                                        String command = "status Guildmember " + playerName;
                                        source.sendSystemMessage(addPrefix(Component.literal("Aplicando status Guildmember para " + playerName + "...")));
                                        CommandSourceStack consoleSource = player.getServer().createCommandSourceStack()
                                                .withPermission(4)
                                                .withSuppressedOutput();
                                        player.getServer().getCommands().performPrefixedCommand(consoleSource, command);
                                        statusApplied = true; // Assumir sucesso se não houver exceção
                                    } catch (Exception e) {
                                        source.sendSystemMessage(addPrefix(Component.literal("Erro ao aplicar status Guildmember: " + e.getMessage())
                                                .withStyle(ChatFormatting.RED)));
                                    }

                                    // Enviar mensagem de sucesso ao jogador
                                    final Component playerMessage = addPrefix(Component.literal("Você aceitou o convite e agora é membro da guilda '" + invite.guildName + "'!"
                                                    + (statusApplied ? "" : " (Status Guildmember não aplicado)"))
                                            .withStyle(statusApplied ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
                                    source.sendSuccess(() -> playerMessage, false);

                                    // Notificar o guildmaster (se online)
                                    ServerPlayer inviter = player.getServer().getPlayerList().getPlayer(invite.inviterUUID);
                                    if (inviter != null) {
                                        final Component inviterMessage = addPrefix(Component.literal(playerName + " aceitou seu convite e agora é membro da guilda '" + invite.guildName + "'.")
                                                .withStyle(ChatFormatting.GREEN));
                                        inviter.sendSystemMessage(inviterMessage);
                                    }

                                    // Remover convite pendente
                                    pendingGuildInvites.remove(playerUUID);

                                    // Salvar dados
                                    GuildRegistry.saveGuilds();
                                    TerrainDataHandler.saveData();
                                    return 1;
                                }))
                        .then(Commands.literal("decline")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player;
                                    try {
                                        player = source.getPlayerOrException();
                                    } catch (Exception e) {
                                        source.sendFailure(addPrefix(Component.literal("Este comando só pode ser executado por jogadores.")));
                                        return 0;
                                    }

                                    UUID playerUUID = player.getUUID();
                                    GuildInviteData invite = pendingGuildInvites.get(playerUUID);
                                    if (invite == null) {
                                        source.sendFailure(addPrefix(Component.literal("Você não tem nenhum convite pendente.")));
                                        return 0;
                                    }

                                    // Enviar mensagem de recusa ao jogador
                                    final Component playerMessage = addPrefix(Component.literal("Você recusou o convite para a guilda '" + invite.guildName + "'.")
                                            .withStyle(ChatFormatting.YELLOW));
                                    source.sendSuccess(() -> playerMessage, false);

                                    // Notificar o guildmaster (se online)
                                    ServerPlayer inviter = player.getServer().getPlayerList().getPlayer(invite.inviterUUID);
                                    if (inviter != null) {
                                        final Component inviterMessage = addPrefix(Component.literal(player.getName().getString() + " recusou seu convite para a guilda '" + invite.guildName + "'.")
                                                .withStyle(ChatFormatting.RED));
                                        inviter.sendSystemMessage(inviterMessage);
                                    }

                                    // Remover convite pendente
                                    pendingGuildInvites.remove(playerUUID);

                                    // Salvar dados
                                    GuildRegistry.saveGuilds();
                                    return 1;
                                }))
        );
    }

    private String getPlayerNameFromUUID(MinecraftServer server, UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            return player.getName().getString();
        }
        Optional<GameProfile> profile = server.getProfileCache().get(uuid);
        return profile.map(GameProfile::getName).orElse(null);
    }


    private int executeAdminStoreLimitRemove(CommandContext<CommandSourceStack> context) {
        String targetName = StringArgumentType.getString(context, "player");
        int value = IntegerArgumentType.getInteger(context, "valor");
        ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayerByName(targetName);

        if (target == null) {
            context.getSource().sendFailure(addPrefix(Component.literal("Jogador não encontrado.")));
            return 0;
        }

        UUID targetUUID = target.getUUID();
        int currentLimit = playerStoreTerrainLimits.getOrDefault(targetUUID, 0);
        int newLimit = currentLimit - value;

        // Garante que o limite não fique negativo
        if (newLimit < 0) {
            newLimit = 0;
        }

        // Cria uma variável final para usar na lambda
        final int finalNewLimit = newLimit;

        // Define o novo limite de terreno de loja para o jogador
        playerStoreTerrainLimits.put(targetUUID, finalNewLimit);

        context.getSource().sendSuccess(() -> addPrefix(Component.literal("Limite de terreno de loja para " + targetName + " agora é " + finalNewLimit)), true);
        TerrainDataHandler.saveData(); // Salva as alterações

        return 1;
    }


    private int executeAdminDimensionPvpOn(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer admin;
        try {
            admin = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(addPrefix(Component.literal("Este comando só pode ser executado por jogadores.")));
            return 0;
        }

        String dimension = admin.level().dimension().location().toString();
        dimensionPvpStatus.put(dimension, true);
        TerrainDataHandler.saveData();

        source.sendSuccess(() ->
                addPrefix(Component.literal("PvP ativado globalmente na dimensão: " + dimension)
                        .withStyle(ChatFormatting.YELLOW)), true);
        admin.getServer().getPlayerList().broadcastSystemMessage(
                addPrefix(Component.literal("PvP foi ativado globalmente na dimensão: " + dimension)
                        .withStyle(ChatFormatting.YELLOW)),
                false
        );
        return 1;
    }

    private int executeAdminDimensionPvpOff(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer admin;
        try {
            admin = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(addPrefix(Component.literal("Este comando só pode ser executado por jogadores.")));
            return 0;
        }

        String dimension = admin.level().dimension().location().toString();
        dimensionPvpStatus.put(dimension, false);
        TerrainDataHandler.saveData();

        source.sendSuccess(() ->
                addPrefix(Component.literal("PvP desativado globalmente na dimensão: " + dimension)
                        .withStyle(ChatFormatting.YELLOW)), true);
        admin.getServer().getPlayerList().broadcastSystemMessage(
                addPrefix(Component.literal("PvP foi desativado globalmente na dimensão: " + dimension)
                        .withStyle(ChatFormatting.YELLOW)),
                false
        );
        return 1;
    }

    private int executeAdminDimensionPvpStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(addPrefix(Component.literal("Este comando só pode ser executado por jogadores.")));
            return 0;
        }

        String dimension = player.level().dimension().location().toString();
        boolean pvpAllowed = isDimensionPvpAllowed(dimension);
        source.sendSuccess(() ->
                addPrefix(Component.literal("PvP está " + (pvpAllowed ? "ativado" : "desativado") + " na dimensão: " + dimension)
                        .withStyle(ChatFormatting.YELLOW)), true);
        return 1;
    }

    public static boolean isDimensionPvpAllowed(String dimension) {
        return dimensionPvpStatus.getOrDefault(dimension, true); // PvP permitido por padrão
    }

    // Comando /terreno pvp on
    private int executePvpOn(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            UUID playerUUID = player.getUUID();

            // Verifica o cooldown
            long currentTime = System.currentTimeMillis();
            Long lastUseTimestamp = pvpCommandCooldowns.get(playerUUID);
            long cooldownDuration = 10 * 60 * 1000L; // 10 minutos em milissegundos
            if (lastUseTimestamp != null && (currentTime - lastUseTimestamp) < cooldownDuration) {
                String timeRemaining = getCooldownTimeRemaining(lastUseTimestamp);
                context.getSource().sendFailure(Component.literal("Você deve esperar " + timeRemaining + " antes de alterar o PvP novamente!"));
                return 0;
            }

            BlockPos playerPos = player.blockPosition();
            ChunkPos chunkPos = new ChunkPos(playerPos);
            String dimension = player.level().dimension().location().toString();
            DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, chunkPos.x, chunkPos.z);

            ProtectionInfo info = ClasseGeral.protectedChunks.get(dcp);
            if (info == null || !info.getOwner().equals(playerUUID)) {
                context.getSource().sendFailure(Component.literal("Você não é o dono deste terreno!"));
                return 0;
            }

            info.setPvpAllowed(true); // Habilita o PvP
            context.getSource().sendSuccess(() -> Component.literal("PvP ativado neste terreno!"), true); // Mensagem de sucesso, sem broadcast

            // Atualiza o cooldown após a execução bem-sucedida
            pvpCommandCooldowns.put(playerUUID, System.currentTimeMillis());
            TerrainDataHandler.saveData(); // Salva as alterações
            return 1;
        } catch (CommandSyntaxException e) {
            context.getSource().sendFailure(Component.literal("Erro ao executar o comando: " + e.getMessage()));
            return 0;
        }
    }

    // Comando /terreno pvp off
    private int executePvpOff(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            UUID playerUUID = player.getUUID();

            // Verifica o cooldown
            long currentTime = System.currentTimeMillis();
            Long lastUseTimestamp = pvpCommandCooldowns.get(playerUUID);
            long cooldownDuration = 10 * 60 * 1000L; // 10 minutos em milissegundos
            if (lastUseTimestamp != null && (currentTime - lastUseTimestamp) < cooldownDuration) {
                String timeRemaining = getCooldownTimeRemaining(lastUseTimestamp);
                context.getSource().sendFailure(Component.literal("Você deve esperar " + timeRemaining + " antes de alterar o PvP novamente!"));
                return 0;
            }

            BlockPos playerPos = player.blockPosition();
            ChunkPos chunkPos = new ChunkPos(playerPos);
            String dimension = player.level().dimension().location().toString();
            DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, chunkPos.x, chunkPos.z);

            ProtectionInfo info = ClasseGeral.protectedChunks.get(dcp);
            if (info == null || !info.getOwner().equals(playerUUID)) {
                context.getSource().sendFailure(Component.literal("Você não é o dono deste terreno!"));
                return 0;
            }

            info.setPvpAllowed(false); // Desabilita o PvP
            context.getSource().sendSuccess(() -> Component.literal("PvP desativado neste terreno!"), true); // Mensagem de sucesso, sem broadcast

            // Atualiza o cooldown após a execução bem-sucedida
            pvpCommandCooldowns.put(playerUUID, System.currentTimeMillis());
            TerrainDataHandler.saveData(); // Salva as alterações
            return 1;
        } catch (CommandSyntaxException e) {
            context.getSource().sendFailure(Component.literal("Erro ao executar o comando: " + e.getMessage()));
            return 0;
        }
    }

    private int executeAdminStoreLimitAdd(CommandContext<CommandSourceStack> context) {
        String targetName = StringArgumentType.getString(context, "player");
        int value = IntegerArgumentType.getInteger(context, "valor");
        ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayerByName(targetName);

        if (target == null) {
            context.getSource().sendFailure(addPrefix(Component.literal("Jogador não encontrado.")));
            return 0;
        }

        UUID targetUUID = target.getUUID();
        int currentLimit = playerStoreTerrainLimits.getOrDefault(targetUUID, 0);
        int newLimit = currentLimit + value;

        // Define o novo limite de terreno de loja para o jogador
        playerStoreTerrainLimits.put(targetUUID, newLimit);

        context.getSource().sendSuccess(() -> addPrefix(Component.literal("Limite de terreno de loja para " + targetName + " agora é " + newLimit)), true);

        return 1;
    }


    private int executeLojaComprar(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(addPrefix(Component.literal("Este comando só pode ser executado por jogadores.")));
            return 0;
        }

        if (!player.level().dimension().equals(Level.OVERWORLD)) {
            source.sendFailure(addPrefix(Component.literal("Este comando só pode ser executado no Overworld.")));
            return 0;
        }

        // Verificar o limite de terrenos de loja do jogador
        UUID playerUUID = player.getUUID();
        int storeLimit = ClasseGeral.playerStoreTerrainLimits.getOrDefault(playerUUID, 0); // Limite de terrenos de loja
        if (storeLimit <= 0) {
            source.sendFailure(addPrefix(Component.literal("Você não tem permissão para comprar terrenos de loja.")));
            return 0;
        }

        // Diminuir o limite de terrenos de loja após a compra
        ClasseGeral.playerStoreTerrainLimits.put(playerUUID, storeLimit - 1);

        Item imbuedCoin = BuiltInRegistries.ITEM.get(ResourceLocation.parse("arcanemoney:imbued_coin"));
        int coinCount = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == imbuedCoin) {
                coinCount += stack.getCount();
            }
        }
        if (coinCount < 20) {  // Alterado para 20 Imbued Coins
            source.sendFailure(addPrefix(Component.literal("Você precisa de 20 Imbued Coins para comprar a loja!")));
            return 0;
        }

        int toRemove = 20;  // Alterado para 20 Imbued Coins
        for (int i = 0; i < player.getInventory().items.size() && toRemove > 0; i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (stack.getItem() == imbuedCoin) {
                int stackCount = stack.getCount();
                if (stackCount <= toRemove) {
                    toRemove -= stackCount;
                    player.getInventory().items.set(i, ItemStack.EMPTY);
                } else {
                    stack.setCount(stackCount - toRemove);
                    toRemove = 0;
                }
            }
        }

        BlockPos playerPos = player.blockPosition();
        ChunkPos cp = new ChunkPos(playerPos);
        String dimension = player.level().dimension().location().toString();
        DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, cp.x, cp.z);

        if (protectedChunks.containsKey(dcp)) {
            source.sendFailure(addPrefix(Component.literal("Este chunk já está protegido.")));
            return 0;
        }

        ServerLevel world = source.getLevel();
        int chunkStartX = cp.x << 4;
        int chunkStartZ = cp.z << 4;
        int chunkEndX = chunkStartX + 15;
        int chunkEndZ = chunkStartZ + 15;
        for (int x = chunkStartX; x <= chunkEndX; x++) {
            for (int z = chunkStartZ; z <= chunkEndZ; z++) {
                int y = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos pos = new BlockPos(x, y, z);
                world.setBlockAndUpdate(pos, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            }
        }

        protectedChunks.put(dcp, new ProtectionInfo(player.getUUID(), "LOJA"));

        Component mensagem = Component.literal("")
                .append(Component.literal("LOJA REGISTRADA!")
                        .withStyle(style -> style.withBold(true).withColor(0xFF0000)))
                .append(" ")
                .append(Component.literal("Interações permitidas.")
                        .withStyle(style -> style.withColor(0xFFD700)))
                .append(" ")
                .append(Component.literal("Para proteção completa, use /terreno comprar.")
                        .withStyle(style -> style.withColor(0x00FFFF).withBold(true)));
        source.sendSuccess(() -> addPrefix(mensagem), true);
        TerrainDataHandler.saveData();
        return 1;
    }

    private int executeAdminPvpOn(CommandContext<CommandSourceStack> context) {
        final String terrainName = StringArgumentType.getString(context, "nome");
        CommandSourceStack source = context.getSource();
        ServerPlayer admin;
        try {
            admin = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(addPrefix(Component.literal("Este comando só pode ser executado por jogadores.")));
            return 0;
        }
        String adminDimension = admin.level().dimension().location().toString();

        int count = 0;
        for (Map.Entry<DimensionalChunkPos, ProtectionInfo> entry : ClasseGeral.protectedChunks.entrySet()) {
            DimensionalChunkPos key = entry.getKey();
            ProtectionInfo info = entry.getValue();
            if (key.getDimension().equalsIgnoreCase(adminDimension) &&
                    info.getTerrainName() != null && !info.getTerrainName().isEmpty() &&
                    info.getTerrainName().equalsIgnoreCase(terrainName)) {
                info.setPvpAllowed(true);
                count++;
            }
        }
        final int finalCount = count;
        source.sendSuccess(() ->
                addPrefix(Component.literal("PvP ativado no terreno [" + terrainName + "] em " + finalCount + " chunks.")), true);
        TerrainDataHandler.saveData();
        return 1;
    }



    private int executeAdminPvpOff(CommandContext<CommandSourceStack> context) {
        final String terrainName = StringArgumentType.getString(context, "nome");
        CommandSourceStack source = context.getSource();
        ServerPlayer admin;
        try {
            admin = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(addPrefix(Component.literal("Este comando só pode ser executado por jogadores.")));
            return 0;
        }
        String adminDimension = admin.level().dimension().location().toString();

        int count = 0;
        for (Map.Entry<DimensionalChunkPos, ProtectionInfo> entry : ClasseGeral.protectedChunks.entrySet()) {
            DimensionalChunkPos key = entry.getKey();
            ProtectionInfo info = entry.getValue();
            if (key.getDimension().equalsIgnoreCase(adminDimension) &&
                    info.getTerrainName() != null && !info.getTerrainName().isEmpty() &&
                    info.getTerrainName().equalsIgnoreCase(terrainName)) {
                info.setPvpAllowed(false);
                count++;
            }
        }
        final int finalCount = count;
        source.sendSuccess(() ->
                addPrefix(Component.literal("PvP desativado no terreno [" + terrainName + "] em " + finalCount + " chunks.")), true);
        TerrainDataHandler.saveData();
        return 1;
    }

    private int executeAdminComprarArea(CommandContext<CommandSourceStack> context) {
        final int diameter = IntegerArgumentType.getInteger(context, "valor");
        final String terrainName = StringArgumentType.getString(context, "nome");
        final CommandSourceStack source = context.getSource();
        final ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(addPrefix(Component.literal("Este comando só pode ser executado por jogadores.")));
            return 0;
        }

        // Pega a dimensão atual do jogador (onde o admin está executando o comando)
        final String dimension = player.level().dimension().location().toString();

        // Pega a posição central do jogador e define o raio da área
        BlockPos centerPos = player.blockPosition();
        int centerX = centerPos.getX();
        int centerZ = centerPos.getZ();
        double radius = diameter / 2.0;

        int minX = (int) Math.floor(centerX - radius);
        int maxX = (int) Math.floor(centerX + radius);
        int minZ = (int) Math.floor(centerZ - radius);
        int maxZ = (int) Math.floor(centerZ + radius);

        int minChunkX = Math.floorDiv(minX, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);

        // Verifica se há conflito em cada chunk da área
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, cx, cz);
                if (ClasseGeral.protectedChunks.containsKey(dcp)) {
                    source.sendFailure(addPrefix(Component.literal("Não é possível comprar a área. O chunk em ("
                            + cx + "," + cz + ") já está protegido.")));
                    return 0;
                }
                // Verifica se o chunk já pertence a algum terreno admin existente
                for (TerrenoAdmin adminTerrain : ClasseGeral.adminTerrains.values()) {
                    if (adminTerrain.getChunks().contains(dcp)) {
                        source.sendFailure(addPrefix(Component.literal("Não é possível comprar a área. O chunk em ("
                                + cx + "," + cz + ") já pertence a um terreno admin.")));
                        return 0;
                    }
                }
            }
        }

        // Coleta todos os chunks da área em um conjunto
        Set<DimensionalChunkPos> chunksSet = new HashSet<>();
        int countChunks = 0;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, cx, cz);
                chunksSet.add(dcp);
                countChunks++;
            }
        }

        // Cria o objeto TerrenoAdmin usando o UUID do jogador (dono real) e registra-o
        TerrenoAdmin adminTerrain = new TerrenoAdmin(terrainName, player.getUUID(), chunksSet);
        ClasseGeral.adminTerrains.put(terrainName, adminTerrain);

        // Registra cada chunk no mapa de proteções usando o ProtectionInfo com o nome do terreno
        for (DimensionalChunkPos dcp : chunksSet) {
            ClasseGeral.protectedChunks.put(dcp, new ProtectionInfo(player.getUUID(), terrainName));
        }

        // Define os limites regionais para a área
        int regionMinX = minChunkX * 16;
        int regionMaxX = maxChunkX * 16 + 15;
        int regionMinZ = minChunkZ * 16;
        int regionMaxZ = maxChunkZ * 16 + 15;

        ServerLevel world = source.getLevel();

        // Coloca uma cerca em cada um dos 4 cantos externos da região
        BlockPos nw = new BlockPos(regionMinX, world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, regionMinX, regionMinZ), regionMinZ);
        BlockPos ne = new BlockPos(regionMaxX, world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, regionMaxX, regionMinZ), regionMinZ);
        BlockPos sw = new BlockPos(regionMinX, world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, regionMinX, regionMaxZ), regionMaxZ);
        BlockPos se = new BlockPos(regionMaxX, world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, regionMaxX, regionMaxZ), regionMaxZ);

        world.setBlockAndUpdate(nw, net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState());
        world.setBlockAndUpdate(ne, net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState());
        world.setBlockAndUpdate(sw, net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState());
        world.setBlockAndUpdate(se, net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState());

        final int finalCount = countChunks;
        source.sendSuccess(() ->
                addPrefix(Component.literal("Área admin '" + terrainName + "' comprada com sucesso na dimensão "
                        + dimension + "! " + finalCount + " chunks reivindicados.")), true);

        TerrainDataHandler.saveData();
        return 1;
    }



    private int executeAdminVenderArea(CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(addPrefix(Component.literal("Este comando só pode ser executado por jogadores.")));
            return 0;
        }

        final String terrainName = StringArgumentType.getString(context, "nome");
        final TerrenoAdmin adminTerrain = ClasseGeral.adminTerrains.get(terrainName);
        if (adminTerrain == null) {
            source.sendFailure(addPrefix(Component.literal("Nenhum terreno admin encontrado com o nome '" + terrainName + "'.")));
            return 0;
        }
        if (!player.getUUID().equals(adminTerrain.getOwner())) {
            source.sendFailure(addPrefix(Component.literal("Você não é o dono deste terreno admin.")));
            return 0;
        }

        // Remove o terreno admin do mapa
        ClasseGeral.adminTerrains.remove(terrainName);

        // Remove a proteção de todos os chunks associados
        for (DimensionalChunkPos dcp : adminTerrain.getChunks()) {
            ClasseGeral.protectedChunks.remove(dcp);
        }
        source.sendSuccess(() ->
                addPrefix(Component.literal("Você vendeu o terreno admin '" + terrainName + "', liberando "
                        + adminTerrain.getChunks().size() + " chunks.")), true);

        TerrainDataHandler.saveData();
        return 1;
    }


    private int executeAdminDesproteger(CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(addPrefix(Component.literal("Este comando só pode ser executado por jogadores.")));
            return 0;
        }

        // Detecta automaticamente a dimensão atual do jogador
        final String dimension = player.level().dimension().location().toString();

        // Se a dimensão estiver na lista de protegidas, remove-a
        if (ClasseGeral.protectedDimensions.contains(dimension)) {
            ClasseGeral.protectedDimensions.remove(dimension);
            // Remove todas as entradas de chunks associados a essa dimensão
            Iterator<Map.Entry<DimensionalChunkPos, ProtectionInfo>> iterator = ClasseGeral.protectedChunks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<DimensionalChunkPos, ProtectionInfo> entry = iterator.next();
                if (entry.getKey().getDimension().equals(dimension)) {
                    iterator.remove();  // Remove o chunk protegido
                }
            }
            TerrainDataHandler.saveData();
            source.sendSuccess(() ->
                    addPrefix(Component.literal("Proteção global removida para a dimensão: " + dimension)), true);
        } else {
            source.sendFailure(addPrefix(Component.literal("A dimensão atual (" + dimension + ") não está protegida.")));
        }

        return 1;
    }




    private int executeAdminProteger(CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        final ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(addPrefix(Component.literal("Este comando só pode ser executado por jogadores.")));
            return 0;
        }

        // Detecta automaticamente a dimensão atual do jogador
        final String dimension = player.level().dimension().location().toString();

        // Adiciona a dimensão à lista de dimensões protegidas
        ClasseGeral.protectedDimensions.add(dimension);


        TerrainDataHandler.saveData();
        source.sendSuccess(() ->
                addPrefix(Component.literal("Proteção global aplicada para a dimensão: " + dimension)), true);
        return 1;
    }


    private int executeComprar(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(addPrefix(Component.literal("Este comando só pode ser executado por jogadores.")));
            return 0;
        }

        if (!player.level().dimension().equals(Level.OVERWORLD)) {
            source.sendFailure(addPrefix(Component.literal("Este comando só pode ser usado no Overworld.")));
            return 0;
        }

        // Verificar a quantidade de Arcane Coins no inventário
        Item arcaneCoin = BuiltInRegistries.ITEM.get(ResourceLocation.parse("arcanemoney:arcane_coin"));
        int coinCount = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == arcaneCoin) {
                coinCount += stack.getCount();
            }
        }

        // Alterado para 1000 Arcane Coins
        if (coinCount < 1000) {
            source.sendFailure(addPrefix(Component.literal("Você precisa de 1000 Arcane Coins para comprar o terreno!")));
            source.sendFailure(addPrefix(Component.literal("Verifique seu saldo e saque o dinheiro necessário para completar a compra.")));
            return 0;
        }

        // Verificar o limite de terrenos do jogador
        UUID playerUUID = player.getUUID();
        int currentTerrainCount = playerTerrainLimits.getOrDefault(playerUUID, 2);
        if (currentTerrainCount <= 0) {
            source.sendFailure(addPrefix(Component.literal("Você atingiu o limite de terrenos permitidos!")));
            return 0;
        }
        playerTerrainLimits.put(playerUUID, currentTerrainCount - 1);

        BlockPos playerPos = player.blockPosition();
        ChunkPos cp = new ChunkPos(playerPos);
        String dimension = player.level().dimension().location().toString();
        DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, cp.x, cp.z);

        // Verifica se o chunk já está protegido
        if (protectedChunks.containsKey(dcp)) {
            source.sendFailure(addPrefix(Component.literal("Este chunk já está protegido.")));
            return 0;
        }

        // Remover as 1000 Arcane Coins do inventário do jogador
        int toRemove = 1000;
        for (int i = 0; i < player.getInventory().items.size() && toRemove > 0; i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (stack.getItem() == arcaneCoin) {
                int stackCount = stack.getCount();
                if (stackCount <= toRemove) {
                    toRemove -= stackCount;
                    player.getInventory().items.set(i, ItemStack.EMPTY);
                } else {
                    stack.setCount(stackCount - toRemove);
                    toRemove = 0;
                }
            }
        }

        // Criar o terreno no mundo
        ServerLevel world = source.getLevel();
        int chunkStartX = cp.x << 4;
        int chunkStartZ = cp.z << 4;
        int chunkEndX = chunkStartX + 15;
        int chunkEndZ = chunkStartZ + 15;
        for (int x = chunkStartX; x <= chunkEndX; x++) {
            for (int z = chunkStartZ; z <= chunkEndZ; z++) {
                int y = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos pos = new BlockPos(x, y, z);
                world.setBlockAndUpdate(pos, Blocks.COBBLESTONE.defaultBlockState());
            }
        }

// Registrar a proteção do terreno
        protectedChunks.put(dcp, new ProtectionInfo(player.getUUID(), ""));
        source.sendSuccess(() ->
                addPrefix(Component.literal("Terreno comprado com sucesso! Chunk protegido e piso de cobblestone colocado.")), true);

// Enviar a mensagem com os comandos disponíveis
        Component commandsMessage = Component.literal("Comandos disponíveis para você:\n")
                .append(Component.literal("▶ /terreno listar terrenos").withStyle(style -> style.withColor(0x00FFFF).withBold(true)))
                .append(Component.literal(" - Lista seus terrenos protegidos.\n"))
                .append(Component.literal("▶ /terreno vender").withStyle(style -> style.withColor(0x00FFFF).withBold(true)))
                .append(Component.literal(" - Vende o terreno e remove a proteção.\n"))
                .append(Component.literal("▶ /terreno pvp on | off").withStyle(style -> style.withColor(0x00FFFF).withBold(true)))
                .append(Component.literal(" - Ativa ou desativa PvP no seu terreno.\n"))
                .append(Component.literal("▶ /sethome <nome>").withStyle(style -> style.withColor(0x00FF00).withBold(true)))
                .append(Component.literal(" - Define uma nova home neste local.\n"))
                .append(Component.literal("▶ /home <nome>").withStyle(style -> style.withColor(0x00FF00).withBold(true)))
                .append(Component.literal(" - Teleporta para a home escolhida.\n"))
                .append(Component.literal("▶ /delhome <nome>").withStyle(style -> style.withColor(0x00FF00).withBold(true)))
                .append(Component.literal(" - Remove a home informada.\n"))
                .append(Component.literal("▶ /listhomes").withStyle(style -> style.withColor(0x00FF00).withBold(true)))
                .append(Component.literal(" - Lista todas as suas homes.\n"));

// Enviar a mensagem ao jogador
        source.sendSuccess(() -> addPrefix(commandsMessage), true);

// Salvar os dados
        TerrainDataHandler.saveData();

// ✅ Finaliza o comando corretamente
        return 1;
    }

    private int executeAliado(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(addPrefix(Component.literal("Este comando só pode ser executado por jogadores.")));
            return 0;
        }

        String allyName = StringArgumentType.getString(context, "nome");
        ServerPlayer ally = player.getServer().getPlayerList().getPlayerByName(allyName);
        UUID allyUUID;
        String resolvedAllyName;

        // Tentar obter o UUID e nome do jogador (online ou offline)
        if (ally != null) {
            allyUUID = ally.getUUID();
            resolvedAllyName = ally.getName().getString();
        } else {
            Optional<GameProfile> profile = player.getServer().getProfileCache().get(allyName);
            if (profile.isEmpty()) {
                source.sendFailure(addPrefix(Component.literal("Jogador '" + allyName + "' não encontrado.")));
                return 0;
            }
            allyUUID = profile.get().getId();
            resolvedAllyName = profile.get().getName();
        }

        // Impedir que o guildmaster se convide
        if (allyUUID.equals(player.getUUID())) {
            source.sendFailure(addPrefix(Component.literal("Você não pode se convidar para a própria guilda.")));
            return 0;
        }

        String guildName = GuildRegistry.getGuildNameByOwner(player.getUUID());
        if (guildName == null) {
            source.sendFailure(addPrefix(Component.literal("Você não é o chefe de nenhuma guilda.")));
            return 0;
        }

        // Verificar limite de aliados
        ProtectionInfo guildInfo = null;
        for (ProtectionInfo info : ClasseGeral.protectedChunks.values()) {
            if (info.getTerrainName().equals(guildName) && info.isGuildTerrain()) {
                guildInfo = info;
                break;
            }
        }
        if (guildInfo != null && guildInfo.getAllies().size() >= guildInfo.getAllyLimit()) {
            source.sendFailure(addPrefix(Component.literal("Limite de aliados atingido para esta guilda.")));
            return 0;
        }

        // Verificar se o jogador já é membro
        if (GuildRegistry.getGuildInfo(guildName).getMembers().contains(allyUUID)) {
            source.sendFailure(addPrefix(Component.literal("Este jogador já é membro da guilda.")));
            return 0;
        }

        // Verificar se já existe um convite pendente
        if (pendingGuildInvites.containsKey(allyUUID)) {
            source.sendFailure(addPrefix(Component.literal(resolvedAllyName + " já tem um convite pendente para outra guilda.")));
            return 0;
        }

        // Armazenar o convite pendente
        pendingGuildInvites.put(allyUUID, new GuildInviteData(guildName, player.getUUID()));

        // Enviar mensagem ao guildmaster
        final Component guildmasterMessage = addPrefix(Component.literal("Convite enviado para " + resolvedAllyName + " se juntar à guilda '" + guildName + "'.")
                .withStyle(ChatFormatting.YELLOW));
        source.sendSuccess(() -> guildmasterMessage, false);

        // Enviar mensagem ao aliado (se online)
        if (ally != null) {
            final Component allyMessage = addPrefix(Component.literal("Você recebeu um convite para se juntar à guilda '" + guildName + "' de " + player.getName().getString() + "!")
                    .append(Component.literal(" Use /guild accept para aceitar ou /guild decline para recusar.")
                            .withStyle(ChatFormatting.AQUA)));
            ally.sendSystemMessage(allyMessage);
        } else {
            source.sendSystemMessage(addPrefix(Component.literal(resolvedAllyName + " está offline, mas o convite foi enviado.")));
        }

        return 1;
    }

    private int executeRetirarAliado(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(addPrefix(Component.literal("Este comando só pode ser executado por jogadores.")));
            return 0;
        }

        String allyName = StringArgumentType.getString(context, "nome");
        ServerPlayer ally = player.getServer().getPlayerList().getPlayerByName(allyName);
        UUID allyUUID;
        String resolvedAllyName;

        // Tentar obter o UUID e nome do jogador (online ou offline)
        if (ally != null) {
            allyUUID = ally.getUUID();
            resolvedAllyName = ally.getName().getString();
        } else {
            Optional<GameProfile> profile = player.getServer().getProfileCache().get(allyName);
            if (profile.isEmpty()) {
                source.sendFailure(addPrefix(Component.literal("Jogador '" + allyName + "' não encontrado.")));
                return 0;
            }
            allyUUID = profile.get().getId();
            resolvedAllyName = profile.get().getName();
        }

        String guildName = GuildRegistry.getGuildNameByOwner(player.getUUID());
        if (guildName == null) {
            source.sendFailure(addPrefix(Component.literal("Você não é o chefe de nenhuma guilda.")));
            return 0;
        }

        if (!GuildRegistry.removeMember(guildName, allyUUID)) {
            source.sendFailure(addPrefix(Component.literal("Este jogador não é membro da sua guilda.")));
            return 0;
        }

        // Remover de ProtectionInfo.allies
        for (Map.Entry<DimensionalChunkPos, ProtectionInfo> entry : ClasseGeral.protectedChunks.entrySet()) {
            ProtectionInfo info = entry.getValue();
            if (info.getTerrainName().equals(guildName) && info.isGuildTerrain()) {
                info.getAllies().remove(allyUUID);
            }
        }

        // Executar comando /status remover como console
        boolean statusRemoved = false;
        try {
            String command = "status remover " + resolvedAllyName;
            source.sendSystemMessage(addPrefix(Component.literal("Removendo status Guildmember de " + resolvedAllyName + "...")));
            CommandSourceStack consoleSource = player.getServer().createCommandSourceStack()
                    .withPermission(4)
                    .withSuppressedOutput();
            player.getServer().getCommands().performPrefixedCommand(consoleSource, command);
            statusRemoved = true; // Assumir sucesso se não houver exceção
        } catch (Exception e) {
            source.sendSystemMessage(addPrefix(Component.literal("Erro ao remover status Guildmember: " + e.getMessage())
                    .withStyle(ChatFormatting.RED)));
        }

        // Preparar mensagem final para o guildmaster
        final Component guildmasterMessage = addPrefix(Component.literal("Jogador " + resolvedAllyName + " foi removido da sua guilda."
                        + (statusRemoved ? "" : " (Status Guildmember não removido)"))
                .withStyle(statusRemoved ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        source.sendSuccess(() -> guildmasterMessage, false);

        // Enviar mensagem ao aliado (se online)
        if (ally != null) {
            final Component allyMessage = addPrefix(Component.literal("Você foi removido da guilda '" + guildName + "' por " + player.getName().getString() + "!"
                            + (statusRemoved ? "" : " (Status Guildmember não removido)"))
                    .withStyle(statusRemoved ? ChatFormatting.RED : ChatFormatting.YELLOW));
            ally.sendSystemMessage(allyMessage);
        } else {
            source.sendSystemMessage(addPrefix(Component.literal(resolvedAllyName + " está offline, mas foi removido da guilda.")));
        }

        // Salvar dados
        GuildRegistry.saveGuilds();
        TerrainDataHandler.saveData();
        return 1;
    }

    // Comando /terreno admin limit add <player> <valor>
    private int executeAdminLimitAdd(CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        if (!source.hasPermission(2)) {
            source.sendFailure(addPrefix(Component.literal("Você não tem permissão para usar esse comando.")));
            return 0;
        }

        final String targetName = StringArgumentType.getString(context, "player");
        final int value = IntegerArgumentType.getInteger(context, "valor");

        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            source.sendFailure(addPrefix(Component.literal("Jogador não encontrado ou offline.")));
            return 0;
        }
        UUID targetUUID = target.getUUID();
        int currentLimit = playerTerrainLimits.getOrDefault(targetUUID, 1);
        int tempLimit = currentLimit + value;
        if (tempLimit < 1) {
            tempLimit = 1;
        }
        final int newLimit = tempLimit;
        playerTerrainLimits.put(targetUUID, newLimit);

        source.sendSuccess(() ->
                addPrefix(Component.literal("O limite de terrenos para " + targetName + " agora é " + newLimit + ".")), true);
        TerrainDataHandler.saveData();
        return 1;
    }


    // Comando /terreno admin limit remove <player> <valor>
    private int executeAdminLimitRemove(CommandContext<CommandSourceStack> context) {
        final CommandSourceStack source = context.getSource();
        if (!source.hasPermission(2)) {
            source.sendFailure(addPrefix(Component.literal("Você não tem permissão para usar esse comando.")));
            return 0;
        }

        final String targetName = StringArgumentType.getString(context, "player");
        final int value = IntegerArgumentType.getInteger(context, "valor");

        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            source.sendFailure(addPrefix(Component.literal("Jogador não encontrado ou offline.")));
            return 0;
        }
        UUID targetUUID = target.getUUID();
        int currentLimit = playerTerrainLimits.getOrDefault(targetUUID, 1);
        int tempLimit = currentLimit - value;
        if (tempLimit < 1) {
            tempLimit = 1;
        }
        final int newLimit = tempLimit;
        playerTerrainLimits.put(targetUUID, newLimit);

        source.sendSuccess(() ->
                addPrefix(Component.literal("O limite de terrenos para " + targetName + " agora é " + newLimit + ".")), true);
        TerrainDataHandler.saveData();
        return 1;
    }


    private int executeVender(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(addPrefix(Component.literal("Este comando só pode ser executado por jogadores.")));
            return 0;
        }

        BlockPos playerPos = player.blockPosition();
        ChunkPos cp = new ChunkPos(playerPos);
        String dimension = player.level().dimension().location().toString();
        DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, cp.x, cp.z);
        ProtectionInfo info = ClasseGeral.protectedChunks.get(dcp);
        if (info == null) {
            source.sendFailure(addPrefix(Component.literal("Este chunk não está protegido.")));
            return 0;
        }
        if (!player.getUUID().equals(info.getOwner())) {
            source.sendFailure(addPrefix(Component.literal("Você não é o dono deste chunk.")));
            return 0;
        }

        // Verifica se é um terreno de guilda
        if (info.isGuildTerrain()) {
            source.sendFailure(addPrefix(Component.literal("Você não pode vender um terreno de guilda com este comando. Use /guild remove.")));
            return 0;
        }

        // Remove a proteção de todos os chunks associados
        ClasseGeral.protectedChunks.remove(dcp);
        // Remove o terreno da lista de terrenos admin
        String terrainName = info.getTerrainName();
        if (terrainName != null && !terrainName.isEmpty()) {
            ClasseGeral.adminTerrains.remove(terrainName);
        }

        // Atualiza o limite de terrenos do jogador
        UUID playerUUID = player.getUUID();
        if (terrainName != null && terrainName.equals("LOJA")) {
            int currentStoreLimit = ClasseGeral.playerStoreTerrainLimits.getOrDefault(playerUUID, 0);
            ClasseGeral.playerStoreTerrainLimits.put(playerUUID, currentStoreLimit + 1);
        } else {
            // Usa o valor armazenado ou 0 se não existir, para evitar sobrescrever
            int currentLimit = ClasseGeral.playerTerrainLimits.getOrDefault(playerUUID, 0);
            ClasseGeral.playerTerrainLimits.put(playerUUID, currentLimit + 1);
        }

        // Efetua o reembolso com base no tipo de terreno
        ItemStack refundStack;
        String refundMessage;

        if (terrainName != null && terrainName.equals("LOJA")) {
            final Item imbuedCoin = BuiltInRegistries.ITEM.get(ResourceLocation.parse("arcanemoney:imbued_coin"));
            refundStack = new ItemStack(imbuedCoin, 8);
            refundMessage = "Você vendeu a loja, perdeu o controle deste chunk e recebeu 8 imbued coins.";
        } else {
            final Item arcaneCoin = BuiltInRegistries.ITEM.get(ResourceLocation.parse("arcanemoney:arcane_coin"));
            refundStack = new ItemStack(arcaneCoin, 500);
            refundMessage = "Você vendeu o terreno, perdeu o controle deste chunk e recebeu 500 arcane coins.";
        }

        boolean added = player.getInventory().add(refundStack);
        if (!added) {
            player.drop(refundStack, false);
        }

        source.sendSuccess(() -> addPrefix(Component.literal(refundMessage)), true);
        TerrainDataHandler.saveData();
        return 1;
    }

    private int executeListarTerrenos(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(addPrefix(Component.literal("Este comando só pode ser executado por jogadores.")));
            return 0;
        }

        UUID playerUUID = player.getUUID();
        int count = 0;
        for (Map.Entry<DimensionalChunkPos, ProtectionInfo> entry : ClasseGeral.protectedChunks.entrySet()) {
            if (entry.getValue().getOwner().equals(playerUUID)) {
                count++;
            }
        }

        final int totalCount = count;
        source.sendSuccess(() ->
                addPrefix(Component.literal("Você possui " + totalCount + " terrenos.")), true);
        return 1;
    }

    public class TeleportData {
        Vec3 initialPos;           // Posição inicial do jogador
        int ticksRemaining;        // Número de ticks restantes até o teletransporte
        CommandSourceStack source; // Origem do comando
        HomeCommandHandler.Home destination;          // Destino do teletransporte (Home)
        String destinationName;    // Nome da home para feedback de sucesso

        // Construtor da classe para armazenar as informações do teletransporte
        public TeleportData(Vec3 initialPos, int ticksRemaining, CommandSourceStack source, HomeCommandHandler.Home destination, String destinationName) {
            this.initialPos = initialPos;
            this.ticksRemaining = ticksRemaining;
            this.source = source;
            this.destination = destination;
            this.destinationName = destinationName;
        }
    }




    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Ao iniciar o servidor, carrega os dados persistidos
        TerrainDataHandler.loadData();
        HomeCommandHandler.loadHomes();
    }
    @SubscribeEvent
    public void onPlayerChat(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        // Não usamos PlayerLoggedInEvent, usamos AsyncChatEvent ou similar
        // Para NeoForge, precisamos usar ServerChatEvent
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // Ao parar o servidor, salva os dados persistidos
        TerrainDataHandler.saveData();
        HomeCommandHandler.saveHomes();
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();

        // Limpar convites de guilda expirados
        Iterator<Map.Entry<UUID, GuildInviteData>> inviteIterator = pendingGuildInvites.entrySet().iterator();
        long currentTime = System.currentTimeMillis();
        while (inviteIterator.hasNext()) {
            Map.Entry<UUID, GuildInviteData> entry = inviteIterator.next();
            GuildInviteData invite = entry.getValue();
            if (currentTime - invite.inviteTime > 5 * 60 * 1000) { // 5 minutos
                inviteIterator.remove();
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    final Component message = addPrefix(Component.literal("O convite para a guilda '" + invite.guildName + "' expirou.")
                            .withStyle(ChatFormatting.RED));
                    player.sendSystemMessage(message);
                }
            }
        }

        Iterator<Map.Entry<UUID, HomeCommandHandler.TeleportData>> iterator = HomeCommandHandler.getTeleportPending().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, HomeCommandHandler.TeleportData> entry = iterator.next();
            UUID playerUUID = entry.getKey();
            HomeCommandHandler.TeleportData data = entry.getValue();

            // Obtém o jogador a partir do UUID
            ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);

            // Verifica se o jogador desconectou ou morreu
            if (player == null || !player.isAlive()) {
                // Se o jogador morreu ou desconectou, cancela o teleporte e remove da lista
                data.source.sendFailure(Component.literal("Teleport cancelado: você morreu ou desconectou!"));
                iterator.remove(); // Remove o jogador da lista de teleportes pendentes
                continue;
            }

            // Verifica se o jogador se moveu durante a contagem
            Vec3 currentPos = player.position();
            if (data.initialPos.distanceTo(currentPos) > 0.1) { // Tolerância de 0.1 blocos
                data.source.sendFailure(Component.literal("Teleport cancelado: você se moveu!"));
                player.serverLevel().sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.3, 0.3, 0.3, 0.1);
                iterator.remove(); // Remove o jogador da lista de teleportes pendentes
                continue;
            }

            // Feedback visual: Partículas ao redor do jogador
            player.serverLevel().sendParticles(
                    ParticleTypes.ENCHANT,
                    player.getX(),
                    player.getY() + 1.0,
                    player.getZ(),
                    5, 0.3, 0.3, 0.3, 0.1
            );

            // Verifica o tempo restante e envia a mensagem de contagem
            int ticksPerSecond = 20; // A cada 20 ticks (1 segundo)
            if (data.ticksRemaining % ticksPerSecond == 0) {
                data.source.sendSuccess(() -> Component.literal(data.ticksRemaining / ticksPerSecond + " segundos restantes...").withStyle(ChatFormatting.YELLOW), true);
            }

            // Reduz o número de ticks restantes
            data.ticksRemaining--;
            if (data.ticksRemaining <= 0) {
                // Realiza o teletransporte
                String currentDimension = player.level().dimension().location().toString(); // Obter a dimensão atual do jogador
                if (!currentDimension.equals(data.destination.dimension)) { // Verificar se as dimensões são diferentes
                    ResourceKey<Level> targetDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(data.destination.dimension));
                    ServerLevel targetLevel = player.server.getLevel(targetDimension);
                    if (targetLevel != null) {
                        player.changeDimension(new DimensionTransition(
                                targetLevel,
                                new Vec3(data.destination.position.getX(), data.destination.position.getY(), data.destination.position.getZ()),
                                Vec3.ZERO, player.getYRot(), player.getXRot(), false, DimensionTransition.PLAY_PORTAL_SOUND
                        ));
                    } else {
                        data.source.sendFailure(Component.literal("A dimensão da casa '" + data.destinationName + "' não está acessível."));
                        iterator.remove();
                        continue;
                    }
                } else {
                    player.teleportTo(data.destination.position.getX(), data.destination.position.getY(), data.destination.position.getZ());
                }

                // Atualiza o cooldown
                HomeCommandHandler.getTeleportCooldowns().put(playerUUID, System.currentTimeMillis());

                // Feedback de sucesso após o teletransporte
                data.source.sendSuccess(() -> Component.literal("Seja bem-vindo à " + data.destinationName + "!").withStyle(style -> style.withBold(true).withColor(0x00FF00)), true);
                player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
                player.serverLevel().sendParticles(
                        ParticleTypes.PORTAL,
                        data.destination.position.getX(), data.destination.position.getY() + 1.0, data.destination.position.getZ(),
                        20, 0.5, 0.5, 0.5, 0.1
                );

                iterator.remove(); // Remove o teletransporte da lista após ser bem-sucedido
            }
        }
    }

    public static class ProtectionInfo {
        private final UUID owner;
        private final Set<UUID> allies = new HashSet<>();
        private final String terrainName;
        private boolean pvpAllowed = true;
        private boolean pveAllowed = true;
        private final int sizeX; // Largura em chunks (ex.: 1, 2, 4)
        private final int sizeZ; // Comprimento em chunks (ex.: 1, 2, 4)
        private final int allyLimit; // Limite de aliados

        // Construtor ajustado
        public ProtectionInfo(UUID owner, String terrainName, int sizeX, int sizeZ, int allyLimit) {
            this.owner = owner;
            this.terrainName = terrainName;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.allyLimit = allyLimit;
        }

        // Construtor compatível com terrenos existentes
        public ProtectionInfo(UUID owner, String terrainName) {
            this(owner, terrainName, 1, 1, 0); // Terrenos normais têm tamanho 1x1 e 0 aliados
        }

        public UUID getOwner() { return owner; }
        public Set<UUID> getAllies() { return allies; }
        public String getTerrainName() { return terrainName; }
        public boolean isPvpAllowed() { return pvpAllowed; }
        public void setPvpAllowed(boolean pvpAllowed) { this.pvpAllowed = pvpAllowed; }
        public boolean isPveAllowed() { return pveAllowed; }
        public void setPveAllowed(boolean pveAllowed) { this.pveAllowed = pveAllowed; }
        public int getSizeX() { return sizeX; }
        public int getSizeZ() { return sizeZ; }
        public int getAllyLimit() { return allyLimit; }
        public boolean isGuildTerrain() { return allyLimit > 0; } // Identifica terrenos de guilda
    }

    public static ProtectionInfo getProtectionInfo(BlockPos pos, Level level) {
        // Obtém a posição do chunk onde o jogador está
        ChunkPos chunkPos = new ChunkPos(pos);
        String dimension = level.dimension().location().toString();

        // Cria um identificador único para o chunk baseado na dimensão e nas coordenadas do chunk
        DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, chunkPos.x, chunkPos.z);

        // Retorna as informações de proteção do mapa
        return protectedChunks.get(dcp);
    }

    private Component addPrefix(Component message) {
        return Component.literal("§f§l[§6§lArcane §5§lMC§f§l] ").append(message);
    }

    public static class GuildCreationData {
        public final ServerLevel level;
        public final ServerPlayer player;
        public final ItemStack itemstack;
        public final int sizeX;
        public final int sizeZ;
        public final int allyLimit;
        public final int brickCount;
        public String guildName; // Mutável
        public final long creationTime; // Timestamp de quando o item foi usado

        public GuildCreationData(ServerLevel level, ServerPlayer player, ItemStack itemstack, int sizeX, int sizeZ, int allyLimit, int brickCount, String guildName) {
            this.level = level;
            this.player = player;
            this.itemstack = itemstack;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
            this.allyLimit = allyLimit;
            this.brickCount = brickCount;
            this.guildName = guildName;
            this.creationTime = System.currentTimeMillis(); // Registra o tempo atual
        }
    }

    private void finalizeGuildCreation(ServerLevel serverLevel, ServerPlayer serverPlayer, ItemStack itemstack, String guildName, CommandSourceStack source) {
        BlockPos playerPos = serverPlayer.blockPosition();
        ChunkPos centerChunk = new ChunkPos(playerPos);
        String dimension = serverLevel.dimension().location().toString();

        int sizeX = ((GuildContractItem) itemstack.getItem()).sizeX;
        int sizeZ = ((GuildContractItem) itemstack.getItem()).sizeZ;
        int allyLimit = ((GuildContractItem) itemstack.getItem()).allyLimit;
        int brickCount = ((GuildContractItem) itemstack.getItem()).brickCount;

        // Calcular os limites do terreno
        int minChunkX = centerChunk.x - (sizeX / 2);
        int maxChunkX = centerChunk.x + (sizeX / 2);
        int minChunkZ = centerChunk.z - (sizeZ / 2);
        int maxChunkZ = centerChunk.z + (sizeZ / 2);

        // Verificar conflitos com terrenos existentes
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, x, z);
                if (protectedChunks.containsKey(dcp)) {
                    source.sendFailure(Component.literal("Não é possível criar o terreno de guilda: a área já está protegida!")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                    GuildRegistry.removeGuild(guildName);
                    return;
                }
            }
        }

        // Substituir o chão com Polished Blackstone Bricks
        int minX = minChunkX << 4;
        int maxX = (maxChunkX << 4) + 15;
        int minZ = minChunkZ << 4;
        int maxZ = (maxChunkZ << 4) + 15;

        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;

        if (brickCount == -1) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int y = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    BlockPos pos = new BlockPos(x, y, z);
                    serverLevel.setBlockAndUpdate(pos, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                }
            }
        } else {
            int totalBlocks = 0;
            int radius = 0;
            while (totalBlocks < brickCount) {
                boolean addedBlocks = false;
                for (int x = centerX - radius; x <= centerX + radius; x++) {
                    for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                        if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
                        if (totalBlocks >= brickCount) break;

                        if (x == centerX - radius || x == centerX + radius || z == centerZ - radius || z == centerZ + radius) {
                            int y = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                            BlockPos pos = new BlockPos(x, y, z);
                            if (serverLevel.getBlockState(pos).getBlock() != Blocks.POLISHED_BLACKSTONE_BRICKS) {
                                serverLevel.setBlockAndUpdate(pos, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                                totalBlocks++;
                                addedBlocks = true;
                            }
                        }
                    }
                    if (totalBlocks >= brickCount) break;
                }
                radius++;
                if (!addedBlocks) break;
            }
        }

        // Adicionar cercas nos vértices
        BlockPos[] corners = {
                new BlockPos(minX, 0, minZ),
                new BlockPos(maxX, 0, minZ),
                new BlockPos(minX, 0, maxZ),
                new BlockPos(maxX, 0, maxZ)
        };

        for (BlockPos corner : corners) {
            int y = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, corner.getX(), corner.getZ());
            BlockPos fencePos = new BlockPos(corner.getX(), y, corner.getZ());
            serverLevel.setBlockAndUpdate(fencePos, Blocks.OAK_FENCE.defaultBlockState());
        }

        // Registrar o terreno com membros iniciais
        Set<UUID> initialAllies = GuildRegistry.getGuildInfo(guildName).getMembers();
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, x, z);
                ProtectionInfo info = new ProtectionInfo(serverPlayer.getUUID(), guildName, sizeX, sizeZ, allyLimit);
                info.getAllies().addAll(initialAllies);
                protectedChunks.put(dcp, info);
            }
        }

        addGuildMaterials(serverPlayer, itemstack.getItem(), source);

        // Efeitos
        serverLevel.playSound(null, serverPlayer.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.0F);
        serverLevel.sendParticles(ParticleTypes.PORTAL, serverPlayer.getX(), serverPlayer.getY() + 1.0, serverPlayer.getZ(), 50, 1.0, 1.0, 1.0, 0.1);

        // Mensagem de sucesso
        source.sendSuccess(() -> Component.literal("Guilda '" + guildName + "' criada com sucesso! Tamanho: " + sizeX + "x" + sizeZ + " chunks."), true);

        // Comandos úteis
        Component comandosGuilda = Component.literal("Comandos disponíveis para sua guilda:\n")
                .append(Component.literal("▶ /guild aliado adicionar <nome>").withStyle(style -> style.withColor(0x00FFFF).withBold(true)))
                .append(Component.literal(" - Adiciona um membro à sua guilda.\n"))
                .append(Component.literal("▶ /guild aliado retirar <nome>").withStyle(style -> style.withColor(0x00FFFF).withBold(true)))
                .append(Component.literal(" - Remove um membro da sua guilda.\n"))
                .append(Component.literal("▶ /guild list").withStyle(style -> style.withColor(0x00FFFF).withBold(true)))
                .append(Component.literal(" - Lista todas as guildas registradas.\n"))
                .append(Component.literal("▶ /guild remove").withStyle(style -> style.withColor(0xFF5555).withBold(true)))
                .append(Component.literal(" - Remove a sua guilda atual e devolve o contrato.\n"));

        source.sendSuccess(() -> comandosGuilda, true);

        // Broadcast
        serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal(serverPlayer.getName().getString() + " fundou a guilda '" + guildName + "'!")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), false);

        // Executar comando /status como console
        try {
            String playerName = serverPlayer.getName().getString();
            String command = "status guildmaster " + playerName;
            // Enviar feedback ao jogador
            source.sendSystemMessage(Component.literal("Aplicando status guildmaster para " + playerName + "..."));
            CommandSourceStack consoleSource = serverLevel.getServer().createCommandSourceStack()
                    .withPermission(4)
                    .withSuppressedOutput();
            serverLevel.getServer().getCommands().performPrefixedCommand(consoleSource, command);
        } catch (Exception e) {
            source.sendFailure(Component.literal("Erro ao aplicar status guildmaster: " + e.getMessage())
                    .withStyle(ChatFormatting.RED));
            return;
        }

        // Consumir o item
        itemstack.shrink(1);

        // Salvar dados
        TerrainDataHandler.saveData();
        GuildRegistry.saveGuilds();
    }

    private void addGuildMaterials(ServerPlayer player, Item item, CommandSourceStack source) {
        boolean addedAll = true;

        if (item == NovosItems.GUILD_CONTRACT_IRON.get()) {
            addedAll &= player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 64));
            addedAll &= player.getInventory().add(new ItemStack(Items.STONE, 32));
            addedAll &= player.getInventory().add(new ItemStack(Items.IRON_INGOT, 16));
        } else if (item == NovosItems.GUILD_CONTRACT_GOLD.get()) {
            addedAll &= player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 128));
            addedAll &= player.getInventory().add(new ItemStack(Items.STONE, 64));
            addedAll &= player.getInventory().add(new ItemStack(Items.IRON_INGOT, 32));
            addedAll &= player.getInventory().add(new ItemStack(Items.GOLD_INGOT, 16));
        } else if (item == NovosItems.GUILD_CONTRACT_DIAMOND.get()) {
            addedAll &= player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 256));
            addedAll &= player.getInventory().add(new ItemStack(Items.STONE, 128));
            addedAll &= player.getInventory().add(new ItemStack(Items.IRON_INGOT, 64));
            addedAll &= player.getInventory().add(new ItemStack(Items.GOLD_INGOT, 32));
            addedAll &= player.getInventory().add(new ItemStack(Items.DIAMOND, 16));
        }

        // Se algum item não foi adicionado ao inventário (ex.: inventário cheio), dropar no chão
        if (!addedAll) {
            source.sendFailure(addPrefix(Component.literal("§eAlguns materiais iniciais foram dropados no chão por falta de espaço no inventário!")));
            if (item == NovosItems.GUILD_CONTRACT_IRON.get()) {
                player.drop(new ItemStack(Items.OAK_PLANKS, 64), false);
                player.drop(new ItemStack(Items.STONE, 32), false);
                player.drop(new ItemStack(Items.IRON_INGOT, 16), false);
            } else if (item == NovosItems.GUILD_CONTRACT_GOLD.get()) {
                player.drop(new ItemStack(Items.OAK_PLANKS, 128), false);
                player.drop(new ItemStack(Items.STONE, 64), false);
                player.drop(new ItemStack(Items.IRON_INGOT, 32), false);
                player.drop(new ItemStack(Items.GOLD_INGOT, 16), false);
            } else if (item == NovosItems.GUILD_CONTRACT_DIAMOND.get()) {
                player.drop(new ItemStack(Items.OAK_PLANKS, 256), false);
                player.drop(new ItemStack(Items.STONE, 128), false);
                player.drop(new ItemStack(Items.IRON_INGOT, 64), false);
                player.drop(new ItemStack(Items.GOLD_INGOT, 32), false);
                player.drop(new ItemStack(Items.DIAMOND, 16), false);
            }
        }
    }

    public static Set<String> protectedDimensions = new HashSet<>();


}
