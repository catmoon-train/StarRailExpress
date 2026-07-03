package org.agmas.noellesroles.content.block_entity.scene;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.SceneRoleAccess;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 迷雾区域方块实体：允许职业进入则致盲（低可视度）；非允许职业被向外推出。
 * 迷雾内计时：15秒警告 → 25秒警告 → 32秒强制死亡（迷失自我）
 * 离开迷雾后 1分30秒 内无法再次进入
 */
public class FogZoneBlockEntity extends BlockEntity {

    // 玩家进入迷雾的游戏刻时间
    private static final Map<UUID, Long> playerInFogSince = new HashMap<>();
    // 玩家最后一次被检测到在迷雾中的游戏刻
    private static final Map<UUID, Long> playerInFogLastSeen = new HashMap<>();
    // 玩家迷雾冷却结束的游戏刻（离开迷雾后 1分30秒 内无法再次进入）
    private static final Map<UUID, Long> playerFogCooldownUntil = new HashMap<>();
    // 上一次清理已离开迷雾玩家的游戏刻
    private static long lastCleanupTick = -1;

    // 迷雾时间阈值（游戏刻）
    private static final long WARNING_15S_TICKS = 15 * 20;  // 15 秒
    private static final long WARNING_25S_TICKS = 25 * 20;  // 25 秒
    private static final long DEATH_32S_TICKS = 32 * 20;    // 32 秒
    // 离开迷雾后的冷却时间（1分30秒）
    private static final long FOG_COOLDOWN_TICKS = 90 * 20; // 90 秒 = 1分30秒

    public FogZoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.FOG_ZONE_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FogZoneBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long currentTick = serverLevel.getGameTime();

        // 每个 tick 只清理一次（由第一个执行到此的雾方块负责）
        if (lastCleanupTick != currentTick) {
            lastCleanupTick = currentTick;
            var iterator = playerInFogLastSeen.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                UUID uuid = entry.getKey();
                long lastSeen = entry.getValue();
                if (lastSeen < currentTick - 1) {
                    // 玩家已离开迷雾区域，重置计时并设置冷却
                    iterator.remove();
                    playerInFogSince.remove(uuid);
                    playerFogCooldownUntil.put(uuid, currentTick + FOG_COOLDOWN_TICKS);
                }
            }
        }

        AABB box = org.agmas.noellesroles.scene.SceneParticles.sceneRegion(pos);
        // 区域内迷雾粒子（覆盖 3×3×4 范围）
        if (currentTick % 2 == 0) {
            org.agmas.noellesroles.scene.SceneParticles.regionScatter(serverLevel, box,
                    net.minecraft.core.particles.ParticleTypes.CLOUD, 5);
        }
        List<Player> players = serverLevel.getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && !p.isSpectator());
        for (Player player : players) {
            if (player.isCreative()) {
                continue;
            }
            UUID uuid = player.getUUID();

            var role = SceneRoleAccess.roleOf(player);
            if (SceneRoleAccess.canEnterRestricted(player, null)
                    || (role != null && role.canAcrossFog())) {
                // 检查冷却时间
                Long cooldownUntil = playerFogCooldownUntil.get(uuid);
                if (cooldownUntil != null && currentTick < cooldownUntil) {
                    // 还在冷却期内，跳过迷雾效果
                    continue;
                }
                // 如果不在冷却内，清除冷却记录
                if (cooldownUntil != null && currentTick >= cooldownUntil) {
                    playerFogCooldownUntil.remove(uuid);
                }

                // 更新迷雾时间追踪
                playerInFogLastSeen.put(uuid, currentTick);
                Long entryTick = playerInFogSince.get(uuid);
                if (entryTick == null) {
                    entryTick = currentTick;
                    playerInFogSince.put(uuid, entryTick);
                }

                long elapsed = currentTick - entryTick;

                // 32 秒：强制死亡（迷失自我）
                if (elapsed >= DEATH_32S_TICKS) {
                    GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.SELF_LOST);
                    playerInFogSince.remove(uuid);
                    playerInFogLastSeen.remove(uuid);
                    playerFogCooldownUntil.remove(uuid);
                    continue;
                }

                // 25 秒：红色广播警告
                if (elapsed >= WARNING_25S_TICKS && player.tickCount % 20 == 0) {
                    Component msg = Component.translatable("message.noellesroles.fog.warning_25s")
                            .withStyle(ChatFormatting.RED);
                    serverLevel.getServer().getPlayerList().broadcastSystemMessage(msg, false);
                }
                // 15 秒：红色广播警告
                else if (elapsed >= WARNING_15S_TICKS && player.tickCount % 20 == 0) {
                    Component msg = Component.translatable("message.noellesroles.fog.warning_15s")
                            .withStyle(ChatFormatting.RED);
                    serverLevel.getServer().getPlayerList().broadcastSystemMessage(msg, false);
                }

                // 区域内低可视度（致盲），本能由客户端 SceneFogClient 关闭
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false, false));
            } else {
                // 非允许职业：被迷雾推出
                Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                Vec3 away = player.position().subtract(center);
                Vec3 flat = new Vec3(away.x, 0, away.z);
                if (flat.lengthSqr() < 1.0e-3) {
                    flat = new Vec3(player.getRandom().nextDouble() - 0.5, 0, player.getRandom().nextDouble() - 0.5);
                }
                flat = flat.normalize().scale(0.55);
                player.setDeltaMovement(flat.x, 0.18, flat.z);
                player.hurtMarked = true;
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false, false));
                if (player instanceof ServerPlayer sp && player.tickCount % 20 == 0) {
                    sp.displayClientMessage(Component.translatable("message.noellesroles.fog.denied"), true);
                }
            }
        }
    }
}
