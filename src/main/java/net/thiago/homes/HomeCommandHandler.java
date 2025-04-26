package net.thiago.homes;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.thiago.items.ClasseGeral;
import net.thiago.terrenomod.DimensionalChunkPos;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import static net.thiago.items.ClasseGeral.playerTerrainLimits;

public class HomeCommandHandler {

    private static final Map<UUID, Map<String, Home>> playerHomes = new HashMap<>();
    private static final File homesDataFile = new File("config/homes.json");
    private static final Map<UUID, Entry<Long, Entry<String, BlockPos>>> teleportTimers = new HashMap<>();
    private static final int MAX_HOMES = 3;
    private static final long TELEPORT_DELAY = 3000;
    public static final Map<UUID, Long> teleportCooldowns = new HashMap<>(); // Mapa de cooldowns
    private static final long COOLDOWN_TIME = 10000; // Tempo de cooldown em milissegundos (10 segundos)
    private static final Map<UUID, TeleportData> teleportPending = new HashMap<>(); // Mapa de teletransporte pendente

    public static class Home {
        public BlockPos position;
        public String dimension;

        public Home(BlockPos position, String dimension) {
            this.position = position;
            this.dimension = dimension;
        }
    }

    // Getter para o mapa teleportCooldowns
    public static Map<UUID, Long> getTeleportCooldowns() {
        return teleportCooldowns;
    }

    // Getter para o mapa teleportPending
    public static Map<UUID, TeleportData> getTeleportPending() {
        return teleportPending;
    }


    // Classe interna marcada como static para uso em métodos estáticos
    public static class BlockPosAdapter implements JsonSerializer<BlockPos>, JsonDeserializer<BlockPos> {
        @Override
        public JsonElement serialize(BlockPos src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("x", src.getX());
            obj.addProperty("y", src.getY());
            obj.addProperty("z", src.getZ());
            return obj;
        }

        @Override
        public BlockPos deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            int x = obj.get("x").getAsInt();
            int y = obj.get("y").getAsInt();
            int z = obj.get("z").getAsInt();
            return new BlockPos(x, y, z);
        }
    }

    public static void loadHomes() {
        if (homesDataFile.exists()) {
            try {
                Gson gson = new GsonBuilder()
                        .registerTypeAdapter(BlockPos.class, new BlockPosAdapter())
                        .create();
                String json = new String(Files.readAllBytes(homesDataFile.toPath()));
                Map<UUID, Map<String, Home>> loadedHomes = gson.fromJson(json, new TypeToken<Map<UUID, Map<String, Home>>>(){}.getType());
                if (loadedHomes != null) {
                    playerHomes.putAll(loadedHomes);
                }
            } catch (IOException e) {
                System.err.println("Erro ao carregar casas: " + e.getMessage());
            }
        }
    }

    public static void saveHomes() {
        try {
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .registerTypeAdapter(BlockPos.class, new BlockPosAdapter())
                    .create();
            Files.createDirectories(homesDataFile.getParentFile().toPath());
            String json = gson.toJson(playerHomes);
            Files.write(homesDataFile.toPath(), json.getBytes());
        } catch (IOException e) {
            System.err.println("Erro ao salvar casas: " + e.getMessage());
        }
    }

    public static int executeSetHomeDefault(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Este comando só pode ser executado por jogadores."));
            return 0;
        }

        // Define a casa com o nome padrão "home"
        String homeName = "home";  // Nome padrão
        BlockPos playerPos = player.blockPosition();

        // Verifica se o jogador está no terreno que ele possui ou é aliado
        if (!isInOwnedOrAlliedTerritory(player)) {
            source.sendFailure(Component.literal("Você só pode setar homes no seu terreno ou no terreno de um aliado."));
            return 0;
        }

        // Verifica o número máximo de casas permitidas
        Map<String, Home> homes = playerHomes.getOrDefault(player.getUUID(), new HashMap<>());
        if (homes.size() >= MAX_HOMES && !homes.containsKey(homeName)) {
            source.sendFailure(Component.literal("Você não pode ter mais de " + MAX_HOMES + " casas."));
            return 0;
        }

        // Registra a casa no mapa de casas do jogador
        homes.put(homeName, new Home(playerPos, player.level().dimension().location().toString()));
        playerHomes.put(player.getUUID(), homes);
        saveHomes();

        // Cria uma variável final temporária para o nome da casa
        final String finalHomeName = homeName;

        // Adiciona feedback visual (partículas)
        player.serverLevel().sendParticles(
                ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1.0, player.getZ(),
                10, 0.3, 0.3, 0.3, 0.1
        );

        // Toca o som de portal ao definir a home
        player.level().playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.2F, 1.0F);

        // Envia uma mensagem de sucesso
        source.sendSuccess(() -> Component.literal("Home '" + finalHomeName + "' definida com sucesso!"), false);

        return 1;
    }

    public static int executeSetHomeWithName(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Este comando só pode ser executado por jogadores."));
            return 0;
        }

        // Obtém o nome da casa fornecido pelo jogador
        String homeName = context.getArgument("nome", String.class);
        if (homeName == null || homeName.isEmpty()) {
            homeName = "home";  // Usa "home" por padrão se o nome estiver vazio
        }

        if (!homeName.matches("[a-zA-Z0-9_]+")) {
            source.sendFailure(Component.literal("O nome da home só pode conter letras, números e sublinhados."));
            return 0;
        }

        BlockPos playerPos = player.blockPosition();

        // Verifica se o jogador está no terreno que ele possui ou é aliado
        if (!isInOwnedOrAlliedTerritory(player)) {
            source.sendFailure(Component.literal("Você só pode setar casas no seu terreno ou no terreno de um aliado."));
            return 0;
        }

        // Verifica o número máximo de casas permitidas
        Map<String, Home> homes = playerHomes.getOrDefault(player.getUUID(), new HashMap<>());
        if (homes.size() >= MAX_HOMES && !homes.containsKey(homeName)) {
            source.sendFailure(Component.literal("Você não pode ter mais de " + MAX_HOMES + " casas."));
            return 0;
        }

        // Registra a casa no mapa de casas do jogador
        homes.put(homeName, new Home(playerPos, player.level().dimension().location().toString()));
        playerHomes.put(player.getUUID(), homes);
        saveHomes();

        // Cria uma variável final temporária para o nome da casa
        final String finalHomeName = homeName;

        // Adiciona feedback visual (partículas)
        player.serverLevel().sendParticles(
                ParticleTypes.PORTAL,
                player.getX(), player.getY() + 1.0, player.getZ(),
                10, 0.3, 0.3, 0.3, 0.1
        );

        // Toca o som de portal ao definir a home
        player.level().playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.2F, 1.0F);

        // Envia uma mensagem de sucesso
        source.sendSuccess(() -> Component.literal("Home '" + finalHomeName + "' definida com sucesso!"), false);

        return 1;
    }



    public static int executeHomeDefault(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Este comando só pode ser executado por jogadores."));
            return 0;
        }

        String homeName = "home";  // Nome padrão

        return executeHomeWithNameInternal(player, homeName, source);
    }

    public static int executeHomeWithName(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Este comando só pode ser executado por jogadores."));
            return 0;
        }

        // Obtém o nome da casa fornecido pelo jogador
        String homeName = context.getArgument("nome", String.class);
        if (homeName == null || homeName.isEmpty()) {
            homeName = "home";  // Usa "home" por padrão se o nome estiver vazio
        }

        return executeHomeWithNameInternal(player, homeName, source);
    }

    private static int executeHomeWithNameInternal(ServerPlayer player, String homeName, CommandSourceStack source) {
        // Verifica cooldown
        long currentTime = System.currentTimeMillis();
        UUID playerUUID = player.getUUID();
        if (teleportCooldowns.containsKey(playerUUID)) {
            long lastUse = teleportCooldowns.get(playerUUID);
            if (currentTime - lastUse < COOLDOWN_TIME) {
                long timeLeft = (COOLDOWN_TIME - (currentTime - lastUse)) / 1000;
                source.sendFailure(Component.literal("Aguarde " + timeLeft + " segundos antes de usar o comando novamente!"));
                return 0;
            }
        }

        // Obtém as casas do jogador
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || homes.isEmpty()) {
            source.sendFailure(Component.literal("Você não tem nenhuma casa definida."));
            return 0;
        }

        // Verifica se a casa existe
        Home home = homes.get(homeName);
        if (home == null) {
            source.sendFailure(Component.literal("Home '" + homeName + "' não encontrada."));
            return 0;
        }

        // Mensagem inicial
        source.sendSuccess(() -> Component.literal("Fique parado por 3 segundos para se teleportar...").withStyle(ChatFormatting.YELLOW), false);

        // Adiciona o jogador ao mapa de teleporte pendente
        teleportPending.put(playerUUID, new TeleportData(player.position(), 60, source, home, homeName));

        // Registra o uso do comando para o cooldown
        teleportCooldowns.put(playerUUID, currentTime);

        return 1; // Comando aceito, teleporte será processado no tick
    }

    public static int executeDelHomeDefault(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Este comando só pode ser executado por jogadores."));
            return 0;
        }

        // Nome da casa padrão "home"
        String homeName = "home";
        return executeDelHomeWithNameInternal(player, homeName, source);
    }

    public static int executeDelHomeWithName(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Este comando só pode ser executado por jogadores."));
            return 0;
        }

        // Obtém o nome da casa fornecido pelo jogador
        String homeName = context.getArgument("nome", String.class);
        if (homeName == null || homeName.isEmpty()) {
            homeName = "home";  // Usa "home" por padrão se o nome estiver vazio
        }

        return executeDelHomeWithNameInternal(player, homeName, source);
    }

    private static int executeDelHomeWithNameInternal(ServerPlayer player, String homeName, CommandSourceStack source) {
        // Obtém as casas do jogador
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || homes.isEmpty()) {
            source.sendFailure(Component.literal("Você não tem nenhuma casa definida."));
            return 0;
        }

        // Verifica se a casa existe
        Home home = homes.get(homeName);
        if (home == null) {
            source.sendFailure(Component.literal("Home '" + homeName + "' não encontrada."));
            return 0;
        }

        // Remove a casa do mapa de casas do jogador
        homes.remove(homeName);
        playerHomes.put(player.getUUID(), homes);

        // Atualiza o limite de casas
        int currentLimit = playerTerrainLimits.getOrDefault(player.getUUID(), 2);
        playerTerrainLimits.put(player.getUUID(), currentLimit + 1);

        // Salva as casas no arquivo JSON
        saveHomes();

        // Envia uma mensagem de sucesso
        source.sendSuccess(() -> Component.literal("A casa '" + homeName + "' foi removida com sucesso!"), false);

        // Feedback visual: Partículas ao redor do jogador
        player.serverLevel().sendParticles(
                ParticleTypes.SMOKE,
                player.getX(), player.getY() + 1.0, player.getZ(),
                10, 0.3, 0.3, 0.3, 0.1
        );

        // Toca o som de cancelamento de portal
        player.level().playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.2F, 1.0F);

        return 1; // Comando aceito, a casa foi removida
    }


    public static int executeListHomes(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Este comando só pode ser executado por jogadores."));
            return 0;
        }

        // Obtém as casas do jogador
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes == null || homes.isEmpty()) {
            source.sendFailure(Component.literal("Você não tem nenhuma casa definida."));
            return 0;
        }

        // Cria um componente para listar todas as casas
        Component homesList = Component.literal("LISTA DE HOMES:\n")
                .withStyle(style -> style.withBold(true).withColor(0x00FFFF));  // Título em aqua

        // Itera sobre as casas e adiciona cada uma à lista
        for (Map.Entry<String, Home> entry : homes.entrySet()) {
            String homeName = entry.getKey();
            Home home = entry.getValue();
            String homeDimension = home.dimension;

            // Formatação para o nome da casa (dourado e negrito)
            Component homeNameComponent = Component.literal(homeName)
                    .withStyle(style -> style.withColor(0xFFD700).withBold(true));  // Nome em dourado e negrito

            // Formatação para a dimensão (vermelho e negrito)
            Component homeDimensionComponent = Component.literal(homeDimension)
                    .withStyle(style -> style.withColor(0xFF0000).withBold(true));  // Dimensão em vermelho e negrito

            // Adiciona o nome da casa e a dimensão à lista
            homesList = ((net.minecraft.network.chat.MutableComponent) homesList).append(Component.literal("\n").append(homeNameComponent).append(Component.literal("      ")).append(homeDimensionComponent));
        }

        // Envia a lista de casas para o jogador
        Component finalHomesList = homesList;
        source.sendSuccess(() -> finalHomesList, true);
        return 1;
    }

    public static void checkTeleport(MinecraftServer server) {
        long currentTime = System.currentTimeMillis();
        for (UUID playerUUID : new HashMap<>(teleportPending).keySet()) {
            TeleportData timer = teleportPending.get(playerUUID);  // Obtém o timer para o jogador
            if (timer == null) continue;  // Se o timer não estiver presente, pula para o próximo

            double timeElapsed = (currentTime - timer.initialPos.y());  // Calculando o tempo como double

            // Se o jogador se moveu, cancela o teletransporte
            ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
            if (player != null) {
                BlockPos originalPos = new BlockPos((int) timer.initialPos.x(), (int) timer.initialPos.y(), (int) timer.initialPos.z());
                if (!player.blockPosition().equals(originalPos)) {
                    // Usando timer.source.sendFailure() para enviar mensagens de erro ao jogador
                    timer.source.sendFailure(Component.literal("Teleporte cancelado: você se moveu!"));
                    player.serverLevel().sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.3, 0.3, 0.3, 0.1);
                    teleportPending.remove(playerUUID);
                    continue;
                }
            }

            // Se o tempo de espera foi atingido, realiza o teletransporte
            if (timeElapsed >= 3000) { // 3 segundos de atraso
                if (player != null) {
                    teleportToHome(player, timer.destinationName, timer.source);  // Passa source para o método teleportToHome
                    teleportPending.remove(playerUUID);  // Remover o teleporte pendente
                } else {
                    teleportPending.remove(playerUUID);  // Remover se o jogador não for encontrado
                }
            } else {
                long timeLeft = (3000 - (long) timeElapsed) / 1000; // Exibe o tempo restante, convertendo para long
                if (player != null) {
                    // Usando timer.source.sendSuccess() para enviar feedback de tempo restante
                    timer.source.sendSuccess(() -> Component.literal("Teleporte em " + timeLeft + " segundos..."), false);
                }
            }
        }
    }

    private static void teleportToHome(ServerPlayer player, String homeName, CommandSourceStack source) {
        Map<String, Home> homes = playerHomes.get(player.getUUID());
        if (homes != null && homes.containsKey(homeName)) {
            Home home = homes.get(homeName);
            String currentDimension = player.level().dimension().location().toString();
            if (!currentDimension.equals(home.dimension)) {
                ResourceKey<Level> targetDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(home.dimension));
                ServerLevel targetLevel = player.server.getLevel(targetDimension);
                if (targetLevel != null) {
                    player.changeDimension(new DimensionTransition(
                            targetLevel,
                            new Vec3(home.position.getX(), home.position.getY(), home.position.getZ()),
                            Vec3.ZERO,
                            player.getYRot(),
                            player.getXRot(),
                            DimensionTransition.DO_NOTHING
                    ));
                } else {
                    // Usando source.sendFailure() para enviar mensagens de erro sobre a dimensão não acessível
                    source.sendFailure(Component.literal("A dimensão da casa '" + homeName + "' não está acessível."));
                    return;
                }
            } else {
                player.teleportTo(home.position.getX(), home.position.getY(), home.position.getZ());
            }
            // Usando source.sendSuccess() para enviar mensagem de sucesso ao jogador
            source.sendSuccess(() -> Component.literal("Você foi teleportado para '" + homeName + "'!"), false);
        }
    }


    private static boolean isInOwnedOrAlliedTerritory(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        ChunkPos chunkPos = new ChunkPos(pos);
        String dimension = player.level().dimension().location().toString();
        DimensionalChunkPos dcp = new DimensionalChunkPos(dimension, chunkPos.x, chunkPos.z);

        ClasseGeral.ProtectionInfo info = ClasseGeral.protectedChunks.get(dcp);
        if (info != null) {
            UUID playerUUID = player.getUUID();
            return info.getOwner().equals(playerUUID) || info.getAllies().contains(playerUUID);
        }
        return false;
    }

    public static class TeleportData {
        public Vec3 initialPos;
        public int ticksRemaining;
        public CommandSourceStack source;
        public Home destination; // Destino do teletransporte (home personalizada)
        public String destinationName; // Nome da home

        TeleportData(Vec3 initialPos, int ticksRemaining, CommandSourceStack source, Home destination, String destinationName) {
            this.initialPos = initialPos;
            this.ticksRemaining = ticksRemaining;
            this.source = source;
            this.destination = destination;
            this.destinationName = destinationName;
        }
    }
}