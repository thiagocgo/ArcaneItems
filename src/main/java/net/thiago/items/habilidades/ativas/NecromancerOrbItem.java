package net.thiago.items.habilidades.ativas;

import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.Item;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class NecromancerOrbItem extends Item {

    private final List<Wolf> summonedWolves = new ArrayList<>();
    private long lastUsed = 0;
    private final long COOLDOWN_TIME = 240000; // 4 minutos em milissegundos
    private final Random random = new Random();

    public NecromancerOrbItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
            if (!ActiveSkillUtils.canUseActiveSkill(serverPlayer)) {
                return InteractionResult.FAIL;
            }

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUsed < COOLDOWN_TIME) {
                serverPlayer.sendSystemMessage(Component.literal("Este item está em cooldown.").withStyle(ChatFormatting.RED));
                return InteractionResult.PASS;
            }

            ServerLevel world = (ServerLevel) context.getLevel();

            // Remover lobos anteriores
            removeOldWolves();

            spawnWolfPack(serverPlayer, world);
            lastUsed = currentTime;

            serverPlayer.sendSystemMessage(Component.literal("A matilha sombria foi invocada!").withStyle(ChatFormatting.DARK_PURPLE));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private void spawnWolfPack(ServerPlayer player, ServerLevel world) {
        List<String> possibleNames = new ArrayList<>(List.of(
                "Guardião das Sombras",
                "Sentinela Sombria",
                "Luz do Vazio",
                "Guardião das Sombras",
                "Eco da Ruína",
                "Sombra Silente",
                "Caçador da Névoa",
                "Uivo Ancestral",
                "Guardião Esquecido",
                "Olhar Abissal",
                "Sentinela da Penumbra"
        ));
        Collections.shuffle(possibleNames); // Embaralha os nomes

        ChatFormatting[] colors = new ChatFormatting[] {
                ChatFormatting.DARK_PURPLE,
                ChatFormatting.RED,
                ChatFormatting.BLUE
        };

        int numberOfWolves = 3;
        for (int i = 0; i < numberOfWolves; i++) {
            Wolf wolf = EntityType.WOLF.create(world);
            if (wolf == null) continue;

            double offsetX = player.getX() + random.nextDouble() * 5 - 2.5;
            double offsetZ = player.getZ() + random.nextDouble() * 5 - 2.5;

            wolf.setPos(offsetX, player.getY(), offsetZ);
            wolf.setTame(true, false);
            wolf.setOwnerUUID(player.getUUID());

            // Nome único da lista embaralhada
            String name = possibleNames.get(i);
            ChatFormatting color = colors[i];

            wolf.setCustomName(Component.literal(name).withStyle(color, ChatFormatting.BOLD));
            wolf.setCustomNameVisible(true); // Nome visível sempre

            world.addFreshEntity(wolf);
            wolf.getNavigation().moveTo(player.getX(), player.getY(), player.getZ(), 1.0D);
            summonedWolves.add(wolf);

            // Partículas sombrias
            world.sendParticles(ParticleTypes.SOUL, offsetX, player.getY() + 1, offsetZ, 20, 0.3, 0.3, 0.3, 0.02);
        }
    }



    private void removeOldWolves() {
        for (Wolf wolf : summonedWolves) {
            if (wolf != null && !wolf.isRemoved()) {
                wolf.discard(); // remove da existência
            }
        }
        summonedWolves.clear();
    }

    private String randomWolfName() {
        String[] names = {
                "Guardião das Sombras",
                "Sentinela Sombria",
                "Luz do Vazio"
        };
        return names[random.nextInt(names.length)];
    }
}
