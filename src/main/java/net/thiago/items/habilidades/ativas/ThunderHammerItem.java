package net.thiago.items.habilidades.ativas;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.thiago.items.ClasseGeral;
import net.thiago.terrenomod.DimensionalChunkPos;
import io.redspace.ironsspellbooks.util.ParticleHelper;

import java.util.HashSet;
import java.util.Set;

public class ThunderHammerItem extends Item {

    public ThunderHammerItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide()) {
            // Verifica se o jogador está em um chunk protegido
            if (!ActiveSkillUtils.canUseActiveSkill(player)) {
                return InteractionResultHolder.fail(stack);
            }

            // Obtém a direção para onde o jogador está olhando
            Vec3 lookDirection = player.getLookAngle();

            // Posição do jogador (olhos)
            Vec3 playerPosition = player.getEyePosition(1.0F);

            // Calcula a posição do impacto do raio (5 blocos à frente)
            Vec3 impactPosition = playerPosition.add(lookDirection.scale(5));

            // Calcula a posição de verificação estendida (5 + 16 = 21 blocos à frente)
            Vec3 extendedCheckPosition = playerPosition.add(lookDirection.scale(21));

            // Determina a dimensão
            String dimension = level.dimension().location().toString();

            // Verifica os chunks ao longo do caminho até a distância estendida
            Set<DimensionalChunkPos> chunksInPath = getChunksInPath(playerPosition, extendedCheckPosition, dimension);
            for (DimensionalChunkPos dcp : chunksInPath) {
                ClasseGeral.ProtectionInfo info = ClasseGeral.protectedChunks.get(dcp);
                if (info != null && !info.isPvpAllowed()) {
                    player.sendSystemMessage(Component.literal("Você não pode lançar raios em direção a um terreno protegido com PvP desativado!")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                    return InteractionResultHolder.fail(stack);
                }
            }

            // Cria o raio e adiciona ao mundo na posição de impacto (5 blocos)
            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
            lightning.moveTo(impactPosition.x, impactPosition.y, impactPosition.z);
            level.addFreshEntity(lightning);

            // Adiciona partículas de eletricidade ao redor da posição de impacto
            if (level instanceof ServerLevel serverLevel) {
                for (int i = 0; i < 20; i++) {
                    double offsetX = (level.random.nextDouble() - 0.5) * 0.8; // Espalhamento de ±0.4 blocos
                    double offsetY = level.random.nextDouble() * 0.5; // Espalhamento vertical
                    double offsetZ = (level.random.nextDouble() - 0.5) * 0.8;
                    serverLevel.sendParticles(ParticleHelper.ELECTRICITY,
                            impactPosition.x + offsetX,
                            impactPosition.y + offsetY,
                            impactPosition.z + offsetZ,
                            1, // Número de partículas por chamada
                            0, 0, 0, // Velocidade
                            0); // Spread
                }
            }

            // Aplica o efeito de partículas ao jogador por 5 segundos (100 ticks)
            var effectHolder = BuiltInRegistries.MOB_EFFECT.getHolder(
                            ResourceLocation.fromNamespaceAndPath("arcaneitems", "thunder_hammer_particles"))
                    .orElseThrow(() -> new IllegalStateException("ThunderHammerParticleEffect not registered"));
            player.addEffect(new MobEffectInstance(
                    effectHolder,
                    60, // Duração: 5 segundos
                    0, // Amplificador
                    false, // Ambiente
                    false // Mostrar partículas do efeito (nenhuma, já que usamos partículas personalizadas)
            ));

            // Toca o som de trovão
            level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 1.0F);

            // Aplica cooldown de 3 minutos (1800 ticks)
            player.getCooldowns().addCooldown(this, 1800);

            // Mensagem de feedback
            player.sendSystemMessage(Component.literal("Raio lançado!").withStyle(ChatFormatting.AQUA));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private Set<DimensionalChunkPos> getChunksInPath(Vec3 start, Vec3 end, String dimension) {
        Set<DimensionalChunkPos> chunks = new HashSet<>();
        BlockPos startPos = new BlockPos((int) start.x, (int) start.y, (int) start.z);
        BlockPos endPos = new BlockPos((int) end.x, (int) end.y, (int) end.z);
        ChunkPos startChunk = new ChunkPos(startPos);
        ChunkPos endChunk = new ChunkPos(endPos);
        chunks.add(new DimensionalChunkPos(dimension, startChunk.x, startChunk.z));
        chunks.add(new DimensionalChunkPos(dimension, endChunk.x, endChunk.z));
        int steps = (int) start.distanceTo(end) + 1;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = start.x + (end.x - start.x) * t;
            double z = start.z + (end.z - start.z) * t;
            BlockPos intermediatePos = new BlockPos((int) x, (int) start.y, (int) z);
            ChunkPos intermediateChunk = new ChunkPos(intermediatePos);
            chunks.add(new DimensionalChunkPos(dimension, intermediateChunk.x, intermediateChunk.z));
        }
        return chunks;
    }
}